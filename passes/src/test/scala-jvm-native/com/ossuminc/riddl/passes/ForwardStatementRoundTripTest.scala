/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Finder
import com.ossuminc.riddl.language.bast.{BASTReader, FORMAT_REVISION}
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.passes.prettify.{PrettifyOutput, PrettifyPass}
import com.ossuminc.riddl.passes.validate.AbstractValidatingTest
import com.ossuminc.riddl.utils.pc

import org.scalatest.TestData

/** `forward` must survive every reflection surface, in BOTH transmission shapes.
  *
  * RIDDL is fully reflective: anything that parses must also be emitted, and must come back
  * unchanged through prettify and BAST. A new statement is only half done when it parses.
  *
  * The two shapes are exercised separately on purpose. They share a sub-kind (21) and are told
  * apart by ONE discriminator byte, so a writer/reader disagreement about that byte would decode
  * the wrong reference kind and misalign everything after it -- and a test covering only the
  * portlet shape would never notice.
  */
class ForwardStatementRoundTripTest extends AbstractValidatingTest {

  private val src =
    """domain Delegation is {
      |  context Boundary is {
      |    event Happened is { note: String }
      |    result Answer is { note: String }
      |    command DoIt yields event Boundary.Happened is { note: String }
      |    query AskIt replies result Boundary.Answer is { note: String }
      |    entity Worker is {
      |      inlet Work is type Boundary.DoIt
      |      handler w is {
      |        on command Boundary.DoIt is { yield event Boundary.Happened }
      |        on query Boundary.AskIt is { reply result Boundary.Answer }
      |      }
      |    }
      |    entity Front is {
      |      outlet Onward is type Boundary.DoIt
      |      handler h is {
      |        on doIt: command Boundary.DoIt is {
      |          forward doIt to outlet Boundary.Front.Onward
      |        }
      |        on askIt: query Boundary.AskIt is {
      |          forward askIt to entity Boundary.Worker
      |        }
      |      }
      |    }
      |  }
      |}
      |""".stripMargin

  private def parse(text: String, origin: String): Root =
    TopLevelParser.parseInput(RiddlParserInput(text, origin), true) match
      case Right(r)   => r
      case Left(msgs) => fail(s"parse failed:\n${msgs.format}")

  private def prettify(root: Root): String =
    Pass
      .runThesePasses(
        PassInput(root),
        Pass.standardPasses :+ { (in: PassInput, out: PassesOutput) =>
          PrettifyPass(in, out, PrettifyPass.Options(flatten = true, inputDir = ""))
        }
      )
      .outputs
      .outputOf[PrettifyOutput](PrettifyPass.name)
      .getOrElse(fail("PrettifyPass produced no output"))
      .state
      .filesAsString

  private def forwards(container: Container[?]): Seq[ForwardStatement] =
    Finder(container.contents).recursiveFindByType[ForwardStatement]

  "forward" should {

    "survive a prettify round trip in both shapes" in { (td: TestData) =>
      val emitted = prettify(parse(src, td.name))
      withClue(s"emitted:\n$emitted") {
        // Emitted as `forward`, not degraded to send/tell -- the whole point is that the statement
        // says something send/tell cannot.
        emitted must include("forward")
        emitted must include("to outlet")
        emitted must include("to entity")
      }
      val reparsed = parse(emitted, td.name + "-reparsed")
      val again = forwards(reparsed)
      withClue(s"re-parsed had ${again.size} forward statements:\n$emitted") {
        again.size mustBe 2
        // One of each shape, still distinguishable after the trip.
        again.count(_.target.isInstanceOf[PortletRef[?]]) mustBe 1
        again.count(_.target.isInstanceOf[ProcessorRef[?]]) mustBe 1
      }
    }

    "survive a BAST round trip in both shapes" in { (td: TestData) =>
      val root = parse(src, td.name)
      val bytes = Pass
        .runThesePasses(PassInput(root), Seq(BASTWriterPass.creator()))
        .outputOf[BASTOutput](BASTWriterPass.name)
        .getOrElse(fail("BASTWriterPass produced no output"))
        .bytes
      val decoded = BASTReader(bytes).read() match
        case Right(d)   => d
        case Left(msgs) => fail(s"BAST round trip failed:\n${msgs.format}")
      val decodedForwards = forwards(decoded)
      withClue(s"decoded ${decodedForwards.size} forward statements") {
        decodedForwards.size mustBe 2
        decodedForwards.count(_.target.isInstanceOf[PortletRef[?]]) mustBe 1
        decodedForwards.count(_.target.isInstanceOf[ProcessorRef[?]]) mustBe 1
      }
      // The definition AFTER the forwards must decode intact. A BAST error names where the reader
      // DERAILED, never what derailed it, so a discriminator-byte mismatch would surface here --
      // on the sibling that follows -- rather than on the forward itself.
      Finder(decoded.contents).recursiveFindByType[Entity].map(_.id.value) must contain("Worker")
    }

    "be 23 -- multi-line `do`/`prompt` bumped it; 22 has shipped" in { (td: TestData) =>
      FORMAT_REVISION mustBe 23.toShort
    }
  }
}
