/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.{Messages, RuleId}
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.passes.Riddl
import com.ossuminc.riddl.utils.pc

import org.scalatest.TestData

/** A processor reference's keyword must name the kind it resolves to (ossum.tech, 2026-11-17).
  *
  * `tell command Cm(x = 1) to repository F`, with `F` a streamlet, validated clean: callers resolve
  * a `processor_ref` as a generic Processor, so the keyword a reader trusts was compared with
  * nothing. `ref-wrong-keyword` already held type references to the same rule.
  */
class ProcessorRefKeywordTest extends AbstractValidatingTest {

  private def model(target: String): String =
    s"""domain D is { context C is {
       | command Cm is { x: Integer }
       | command Go is { x: Integer }
       | streamlet F as flow is { inlet I is command Cm outlet O is command Cm
       |   handler H is { on c: command Cm { send c to outlet O } } }
       | entity E is { inlet EI is command Go outlet EO is command Cm
       |   handler EH is { on command Go { tell command Cm(x = 1) to $target } } }
       | connector K is { from outlet E.EO to inlet F.I }
       |} }
       |""".stripMargin

  private def wrongKeyword(src: String, td: TestData): Seq[Messages.Message] =
    val msgs = Riddl.parseAndValidate(RiddlParserInput(src, td), shouldFailOnError = false) match
      case Right(result) => result.messages
      case Left(msgs)    => msgs
    msgs.filter(_.ruleId.contains(RuleId.WrongKeyword))

  "A processor reference" should {
    "be an Error when its keyword names a different kind (the ossum.tech repro)" in {
      (td: TestData) =>
        val errs = wrongKeyword(model("repository F"), td)
        errs.size mustBe 1
        errs.head.kind mustBe Messages.Error
        errs.head.message must include("'F' is declared a streamlet")
        errs.head.message must include("a repository")
        wrongKeyword(model("entity F"), td).size mustBe 1
    }

    "accept `streamlet`, `processor` and every shape keyword for any streamlet" in {
      (td: TestData) =>
        for kw <- Seq("streamlet", "processor", "flow", "source", "sink", "merge", "router") do
          withClue(kw) { wrongKeyword(model(s"$kw F"), td) mustBe empty }
    }

    "be an Error when a streamlet keyword names an entity" in { (td: TestData) =>
      val errs = wrongKeyword(model("streamlet E"), td)
      errs.size mustBe 1
      errs.head.message must include("'E' is declared an entity")
    }

    "check an epic step's processor reference too" in { (td: TestData) =>
      val src =
        """domain D is {
          | user U is "someone"
          | context C is {
          |  command Cm is { x: Integer }
          |  entity E is { inlet EI is command Cm handler EH is { on command Cm { ??? } } }
          | }
          | epic Ep is {
          |  user D.U wants to "x" so that "y"
          |  case UC is {
          |   user D.U wants to "x" so that "y"
          |   step send command C.Cm from user D.U to repository C.E
          |  }
          | }
          |}
          |""".stripMargin
      val errs = wrongKeyword(src, td)
      errs.size mustBe 1
      errs.head.message must include("is declared an entity")
      wrongKeyword(src.replace("to repository C.E", "to entity C.E"), td) mustBe empty
    }

    "accept an epic step's `flow X` naming a group or a streamlet, and nothing else" in {
      (td: TestData) =>
        // In an interaction endpoint `flow X` parses as a GROUP reference first, so a flow
        // streamlet arrives as a GroupRef. Both readings are legitimate; an entity is not.
        def step(from: String): String =
          s"""domain D is {
             | user U is "someone"
             | context C is {
             |  command Cm is { x: Integer }
             |  streamlet F as flow is { inlet I is command Cm outlet O is command Cm
             |    handler H is { on c: command Cm { send c to outlet O } } }
             |  entity E is { inlet EI is command Cm handler EH is { on command Cm { ??? } } }
             | }
             | application context App is { group G is { ??? } }
             | epic Ep is {
             |  user D.U wants to "x" so that "y"
             |  case UC is {
             |   user D.U wants to "x" so that "y"
             |   step send command C.Cm from $from to entity C.E
             |  }
             | }
             |}
             |""".stripMargin
        wrongKeyword(step("flow C.F"), td) mustBe empty
        wrongKeyword(step("streamlet C.F"), td) mustBe empty
        wrongKeyword(step("flow App.G"), td) mustBe empty
        val errs = wrongKeyword(step("flow C.E"), td)
        errs.size mustBe 1
        errs.head.message must include("'C.E' is declared an entity")
    }
  }
}
