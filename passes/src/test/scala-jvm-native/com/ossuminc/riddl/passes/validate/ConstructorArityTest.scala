/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.utils.pc
import org.scalatest.TestData

/** Constructing an aggregate checks its arguments.
  *
  * `Checkout()` is legal SYNTAX — a constructor of a message with no fields — so an empty argument
  * list is a parser question no longer and a validation question instead. Against a type that HAS
  * fields it is a mistake, and it used to pass silently: the arity branch was guarded on
  * `args.nonEmpty`, so zero arguments were never compared with the field count at all.
  */
class ConstructorArityTest extends AbstractValidatingTest {

  private def model(fields: String, args: String): String =
    s"""domain Dom is {
       |  context Ctx is {
       |    type Name is String
       |    command Checkout is { $fields }
       |    entity E is {
       |      handler H is {
       |        on other is { tell command Dom.Ctx.Checkout($args) to entity Dom.Ctx.E }
       |      }
       |    }
       |  }
       |}
       |""".stripMargin

  private def errorsFor(fields: String, args: String, td: TestData): Seq[String] =
    var found = Seq.empty[String]
    parseAndValidateDomain(RiddlParserInput(model(fields, args), td), shouldFailOnErrors = false) {
      case (_, _, messages) =>
        found = messages.justErrors.map(_.message).filter(_.contains("Constructor of"))
        succeed
    }
    found

  "constructing an aggregate" should {

    "accept `Checkout()` when the message has NO fields" in { (td: TestData) =>
      val errs = errorsFor("???", "", td)
      withClue(s"an empty constructor of a field-less message is legal: $errs") {
        errs mustBe empty
      }
    }

    "REJECT `Checkout()` when the message HAS fields" in { (td: TestData) =>
      val errs = errorsFor("who: Dom.Ctx.Name", "", td)
      withClue("zero arguments against a type with fields is an arity error") {
        errs mustNot be(empty)
      }
    }

    "accept the right number of positional arguments" in { (td: TestData) =>
      errorsFor("who: Dom.Ctx.Name", """"someone"""", td) mustBe empty
    }

    "REJECT too many arguments" in { (td: TestData) =>
      errorsFor("who: Dom.Ctx.Name", """"a", "b"""", td) mustNot be(empty)
    }

    /** A prompt is AI-computed and untyped, so it is compatible with a field of ANY type — it must
      * still satisfy arity, though.
      */
    "accept a prompt as an argument for any field" in { (td: TestData) =>
      errorsFor("who: Dom.Ctx.Name", """prompt("work out who")""", td) mustBe empty
    }
  }
}
