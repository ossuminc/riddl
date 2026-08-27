/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.prettify

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{filter, Finder}
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.passes.validate.AbstractValidatingTest
import com.ossuminc.riddl.passes.{Pass, PassInput, PassesOutput}
import com.ossuminc.riddl.utils.pc

import org.scalatest.*

/** A `state` with no body keeps its metadata through prettify.
  *
  * `closeState` called `closeDef` only when the state had contents, and `closeDef` emits BOTH the
  * closing brace and the metadata. A body-less state has no brace to close — and was losing its
  * `briefly` and `described as` with it. The bodied form was unaffected, which is why this went
  * unnoticed: the two spellings took different paths and only one was complete.
  *
  * 41 states across 41 files of riddl-models were affected, producing 82 missing warnings and
  * blocking canonicalisation of the corpus.
  */
class BodylessStateRoundTripTest extends AbstractValidatingTest {

  private def parse(src: String, origin: String): Root =
    TopLevelParser.parseInput(RiddlParserInput(src, origin)) match
      case Right(root) => root
      case Left(msgs)  => fail(s"parse of $origin failed:\n${msgs.format}")

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

  /** Both spellings, so a fix cannot trade one for the other. */
  private val src =
    """domain D is {
      |  context C is {
      |    type SData is { id: String }
      |    entity E is {
      |      state Bodyless of record D.C.SData with {
      |        briefly "a body-less state"
      |        described as "it carries its metadata inline"
      |      }
      |      state Bodied of record D.C.SData is {
      |        handler SH is { ??? }
      |      } with {
      |        briefly "a bodied state"
      |      }
      |      handler EH is { ??? }
      |    }
      |  }
      |}
      |""".stripMargin

  private def stateNamed(root: Root, name: String): State =
    Finder(root)
      .recursiveFindByType[State]
      .find(_.id.value == name)
      .getOrElse(fail(s"no state named $name"))

  "a body-less state's metadata" should {

    "be emitted by prettify" in { (_: TestData) =>
      val pretty = prettify(parse(src, "bodyless"))
      withClue(s"prettified output was:\n$pretty") {
        pretty must include("a body-less state")
        pretty must include("it carries its metadata inline")
      }
    }

    "survive a prettify round trip" in { (_: TestData) =>
      val pretty = prettify(parse(src, "bodyless"))
      val again = stateNamed(parse(pretty, "regen"), "Bodyless")
      withClue(s"prettified output was:\n$pretty") {
        again.metadata.filter[BriefDescription] must not be empty
        again.metadata.filter[BlockDescription] must not be empty
      }
    }

    "leave the bodied form intact" in { (_: TestData) =>
      val pretty = prettify(parse(src, "bodyless"))
      val again = parse(pretty, "regen")
      withClue(s"prettified output was:\n$pretty") {
        stateNamed(again, "Bodied").metadata.filter[BriefDescription] must not be empty
        stateNamed(again, "Bodied").contents.filter[Handler] must not be empty
      }
    }
  }
}
