/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.prettify

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Finder
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.passes.validate.AbstractValidatingTest
import com.ossuminc.riddl.passes.{Pass, PassInput, PassesOutput}
import com.ossuminc.riddl.utils.pc

import org.scalatest.*

/** RIDDL is reflective: the 2.0 handler-kind clauses (`on event`, `on activate`, `on passivate`)
  * must not only parse but also emit (prettify) and re-parse back to the SAME nodes at the SAME
  * place. This guards the prettify half of the reflection loop for the handler-kinds feature.
  */
class HandlerKindsRoundTripTest extends AbstractValidatingTest {

  private def parse(src: String, origin: String): Root =
    TopLevelParser.parseInput(RiddlParserInput(src, origin)) match
      case Right(root) => root
      case Left(msgs)  => fail(s"parse of $origin failed:\n${msgs.format}")

  private def prettify(root: Root): String =
    val creators = Pass.standardPasses :+ { (in: PassInput, out: PassesOutput) =>
      PrettifyPass(in, out, PrettifyPass.Options(flatten = true, inputDir = ""))
    }
    val result = Pass.runThesePasses(PassInput(root), creators)
    result.outputs
      .outputOf[PrettifyOutput](PrettifyPass.name)
      .getOrElse(fail("PrettifyPass produced no output"))
      .state
      .filesAsString

  private val src =
    """domain d is {
      |  context c is {
      |    constant Grace: Duration = "30 minutes"
      |    entity e is {
      |      command Cmd is { g: Integer }
      |      event Evt is { h: Integer }
      |      handler hh is {
      |        on command Cmd { do "handle" }
      |        on event Evt { do "note" }
      |        on activate { do "rehydrate" }
      |        on passivate { do "evict" }
      |        on quiescence "30 minutes" { do "nobody touched it" }
      |      }
      |    }
      |    repository r is {
      |      handler rh is {
      |        on quiescence Grace { do "compact" }
      |      }
      |    }
      |  }
      |}
      |""".stripMargin

  "Handler kinds" should {

    "round-trip on event / on activate / on passivate through prettify" in { (td: TestData) =>
      val root1 = parse(src, "src")
      val f1 = Finder(root1)
      f1.recursiveFindByType[OnEventClause].size mustBe 1
      f1.recursiveFindByType[OnActivationClause].size mustBe 1
      f1.recursiveFindByType[OnPassivationClause].size mustBe 1
      f1.recursiveFindByType[OnMessageClause].size mustBe 1 // the `on command Cmd`
      f1.recursiveFindByType[OnQuiescenceClause].size mustBe 2

      // Emitted: each clause keyword must appear, in its round-trippable source form. The
      // quiescence WINDOW rides `Declaration.ascription`, so a `format`-only implementation would
      // emit `on quiescence is` and drop it -- the `on other as x` regression, pinned here.
      val pretty = prettify(root1)
      pretty must include("on command Cmd is")
      pretty must include("on event Evt is")
      pretty must include("on activate is")
      pretty must include("on passivate is")
      pretty must include("on quiescence \"30 minutes\" is")
      pretty must include("on quiescence Grace is")

      // Re-parsed: every clause survives as the SAME node kind (not dropped, not
      // collapsed into a plain OnMessageClause / OnOtherClause).
      val root2 = parse(pretty, "regen")
      val f2 = Finder(root2)
      f2.recursiveFindByType[OnEventClause].size mustBe 1
      f2.recursiveFindByType[OnActivationClause].size mustBe 1
      f2.recursiveFindByType[OnPassivationClause].size mustBe 1
      f2.recursiveFindByType[OnMessageClause].size mustBe 1
      val quiet = f2.recursiveFindByType[OnQuiescenceClause]
      quiet.size mustBe 2
      quiet.map(_.window.format).sorted mustBe Seq("\"30 minutes\"", "Grace")
    }
  }
}
