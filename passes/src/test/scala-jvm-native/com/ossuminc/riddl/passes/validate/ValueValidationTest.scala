/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.utils.pc

import org.scalatest.TestData

/** A54/A45/A45b/A57: value-expression validation — constructor arity/names/types, `put` value vs
  * output type, `return` value vs function output, get-from-input/state resolution, and four-source
  * value references.
  */
class ValueValidationTest extends AbstractValidatingTest {

  private def app(body: String, extra: String = ""): String =
    s"""domain d is {
       |  application context UI is {
       |    type Greeting is record { text: String }
       |    type Other is record { n: Integer }
       |    command Refresh is { ??? }
       |    $extra
       |    group Main is {
       |      form Entry acquires type Greeting
       |      output Panel presents type Greeting
       |    }
       |    handler Screen is {
       |      on command Refresh {
       |        $body
       |      }
       |    }
       |  }
       |}
       |""".stripMargin

  private def func(body: String, returns: String = "record Sum"): String =
    s"""domain d is {
       |  context Calc is {
       |    type Sum is record { total: Integer }
       |    type Diff is record { d: Integer }
       |    function Add is {
       |      returns $returns
       |      $body
       |    }
       |  }
       |}
       |""".stripMargin

  "Value validation (A54/A45/A45b/A57)" should {

    "accept a put whose constructor value matches the output type" in { (td: TestData) =>
      parseAndValidate(
        app("""put record Greeting(text = "hi") to output Panel"""),
        td.name,
        shouldFailOnErrors = false
      ) { case (_, _, msgs: Messages) =>
        msgs.filter(m => m.kind == Error && m.message.contains("'put' value has type")) mustBe empty
      }
    }

    "reject a put whose constructor value type does not match the output" in { (td: TestData) =>
      parseAndValidate(
        app("""put record Other(n = "3") to output Panel"""),
        td.name,
        shouldFailOnErrors = false
      ) { case (_, _, msgs: Messages) =>
        assertValidationMessage(msgs, Error, "'put' value has type")
      }
    }

    "accept a put reading a value from a UI input (A45b)" in { (td: TestData) =>
      parseAndValidate(
        app("""put get from input Entry to output Panel"""),
        td.name,
        shouldFailOnErrors = false
      ) { case (_, _, msgs: Messages) =>
        msgs.filter(m =>
          m.kind == Error && (m.message.contains("'put' value") || m.message.contains("input"))
        ) mustBe empty
      }
    }

    "reject a constructor with too many positional arguments" in { (td: TestData) =>
      parseAndValidate(
        app("""put record Greeting("a", "b") to output Panel"""),
        td.name,
        shouldFailOnErrors = false
      ) { case (_, _, msgs: Messages) =>
        assertValidationMessage(msgs, Error, "arguments but the type")
      }
    }

    "reject a constructor with an unknown named argument" in { (td: TestData) =>
      parseAndValidate(
        app("""put record Greeting(bogus = "x") to output Panel"""),
        td.name,
        shouldFailOnErrors = false
      ) { case (_, _, msgs: Messages) =>
        assertValidationMessage(msgs, Error, "is not a field of")
      }
    }

    "reject positional arguments following named arguments" in { (td: TestData) =>
      parseAndValidate(
        app("""put record Greeting(text = "x", "y") to output Panel"""),
        td.name,
        shouldFailOnErrors = false
      ) { case (_, _, msgs: Messages) =>
        assertValidationMessage(msgs, Error, "positional arguments must precede named arguments")
      }
    }

    "reject a value reference that is not in scope" in { (td: TestData) =>
      parseAndValidate(
        app("""put nonexistent to output Panel"""),
        td.name,
        shouldFailOnErrors = false
      ) { case (_, _, msgs: Messages) =>
        assertValidationMessage(msgs, Error, "is not a 'let'-local")
      }
    }

    "accept a value reference bound by a let in scope" in { (td: TestData) =>
      parseAndValidate(
        app("""let greet: Greeting = "hello"
              |        put greet to output Panel""".stripMargin),
        td.name,
        shouldFailOnErrors = false
      ) { case (_, _, msgs: Messages) =>
        msgs.filter(m => m.kind == Error && m.message.contains("is not a 'let'-local")) mustBe empty
      }
    }

    "accept a return whose value matches the function output" in { (td: TestData) =>
      parseAndValidate(
        func("""return record Sum(total = "t")"""),
        td.name,
        shouldFailOnErrors = false
      ) { case (_, _, msgs: Messages) =>
        msgs.filter(m =>
          m.kind == Error && m.message.contains("'return' value has type")
        ) mustBe empty
      }
    }

    "reject a return whose value type does not match the function output" in { (td: TestData) =>
      parseAndValidate(
        func("""return record Diff(d = "x")"""),
        td.name,
        shouldFailOnErrors = false
      ) { case (_, _, msgs: Messages) =>
        assertValidationMessage(msgs, Error, "'return' value has type")
      }
    }

    // I1: a handler whose only action is `put` is executable, not Empty — no spurious
    // "has no executable statements" completeness warning.
    "not flag a put-only handler as having no executable statements" in { (td: TestData) =>
      parseAndValidate(
        app("""put record Greeting(text = "hi") to output Panel"""),
        td.name,
        shouldFailOnErrors = false
      ) { case (_, _, msgs: Messages) =>
        msgs.filter(m => m.message.contains("has no executable statements")) mustBe empty
      }
    }

    // M1: value validation must reach put/return nested under when/match/foreach — proven by the
    // fact that a nested out-of-scope value-ref / type mismatch is still reported.
    "reach a put nested inside a when clause (value-ref scope checked)" in { (td: TestData) =>
      parseAndValidate(
        app("""when "c" then
              |          put nonexistent to output Panel
              |        end""".stripMargin),
        td.name,
        shouldFailOnErrors = false
      ) { case (_, _, msgs: Messages) =>
        assertValidationMessage(msgs, Error, "is not a 'let'-local")
      }
    }

    "reach a return nested inside a when clause (type mismatch checked)" in { (td: TestData) =>
      parseAndValidate(
        func("""when "c" then
              |        return record Diff(d = "x")
              |      end""".stripMargin),
        td.name,
        shouldFailOnErrors = false
      ) { case (_, _, msgs: Messages) =>
        assertValidationMessage(msgs, Error, "'return' value has type")
      }
    }
  }
}
