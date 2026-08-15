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
       |    record Greeting is { text: String }
       |    record Other is { n: Integer }
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
       |    record Sum is { total: Integer }
       |    record Diff is { d: Integer }
       |    function Add is {
       |      returns $returns
       |      $body
       |    }
       |  }
       |}
       |""".stripMargin

  // A17: an entity whose state carries boolean fields `flag`/`isPaid` and a numeric field `count` —
  // all in scope of the `on command Do` clause as state fields (used to exercise bare boolean value
  // references as `when` conditions).
  private def whenEnt(body: String): String =
    s"""domain d is {
       |  context c is {
       |    command Do is { ??? }
       |    entity E is {
       |      record Data is { flag: Boolean, isPaid: Boolean, count: Integer }
       |      state S of record Data
       |      handler h is {
       |        on command Do {
       |          $body
       |        }
       |      }
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

    // A54: operand widening — a Constructor in a send/morph is validated through checkStatementScopes.
    "reject a send whose event constructor has the wrong arity" in { (td: TestData) =>
      val model =
        """domain d is {
          |  context c is {
          |    event Added is { sku: String }
          |    command Add is { sku: String }
          |    outlet outp is event Added
          |    entity E is {
          |      record Data is { n: Integer }
          |      state S of record Data
          |      handler H is {
          |        on command Add {
          |          send event Added(sku = "x", extra = "y") to outlet c.outp
          |        }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin
      parseAndValidate(model, td.name, shouldFailOnErrors = false) { case (_, _, msgs: Messages) =>
        assertValidationMessage(msgs, Error, "is not a field of")
      }
    }

    "accept a well-formed morph record constructor" in { (td: TestData) =>
      val model =
        """domain d is {
          |  context c is {
          |    command Add is { sku: String }
          |    entity E is {
          |      record Data is { n: Integer }
          |      state S of record Data
          |      handler H is {
          |        on command Add {
          |          morph entity E to state E.S with record Data(n = "1")
          |        }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin
      parseAndValidate(model, td.name, shouldFailOnErrors = false) { case (_, _, msgs: Messages) =>
        msgs.filter(m => m.kind == Error && m.message.contains("Constructor of")) mustBe empty
      }
    }

    "reject a comparison of a numeric to a string operand (A28)" in { (td: TestData) =>
      val model =
        """domain d is {
          |  context c is {
          |    type Num is Integer
          |    type Str is String
          |    handler h is {
          |      on init {
          |        let a: Num = "1"
          |        let b: Str = "x"
          |        let bad = a == b
          |      }
          |    }
          |  }
          |}
          |""".stripMargin
      parseAndValidate(model, td.name, shouldFailOnErrors = false) { case (_, _, msgs: Messages) =>
        assertValidationMessage(msgs, Error, "Cannot compare")
      }
    }

    "reject a non-boolean operand of a logical `and` (A28)" in { (td: TestData) =>
      val model =
        """domain d is {
          |  context c is {
          |    type Num is Integer
          |    handler h is {
          |      on init {
          |        let a: Num = "1"
          |        let b: Num = "2"
          |        let bad = a and b
          |      }
          |    }
          |  }
          |}
          |""".stripMargin
      parseAndValidate(model, td.name, shouldFailOnErrors = false) { case (_, _, msgs: Messages) =>
        assertValidationMessage(msgs, Error, "must be a boolean")
      }
    }

    "accept a well-formed boolean expression (A28)" in { (td: TestData) =>
      val model =
        """domain d is {
          |  context c is {
          |    type Num is Integer
          |    type Flag is Boolean
          |    handler h is {
          |      on init {
          |        let n: Num = "1"
          |        let f: Flag = "true"
          |        let ok = n > n and f
          |        let lit = true
          |      }
          |    }
          |  }
          |}
          |""".stripMargin
      parseAndValidate(model, td.name, shouldFailOnErrors = false) { case (_, _, msgs: Messages) =>
        msgs.filter(m =>
          m.kind == Error && (m.message.contains("Cannot compare") || m.message.contains(
            "must be a boolean"
          ))
        ) mustBe empty
      }
    }

    // ---- A28 slice 3: type-safe comparison operands (refs, or a bare NumericLiteral — A28's
    // ref-only rule was reversed 2026-08-14) ----

    "accept `count > MaxCount` comparing a numeric local to a numeric constant (A28 s3)" in {
      (td: TestData) =>
        val model =
          """domain d is {
            |  context c is {
            |    type Num is Integer
            |    constant MaxCount is Integer = "5"
            |    handler h is {
            |      on init {
            |        let count: Num = "1"
            |        let ok = count > MaxCount
            |        let ok2 = count > constant MaxCount
            |      }
            |    }
            |  }
            |}
            |""".stripMargin
        parseAndValidate(model, td.name, shouldFailOnErrors = false) {
          case (_, _, msgs: Messages) =>
            msgs.filter(m =>
              m.kind == Error && (m.message.contains("Cannot compare") ||
                m.message.contains("requires a numeric operand") ||
                m.message.contains("is not a 'let'-local"))
            ) mustBe empty
        }
    }

    "reject an ordering comparison against a string constant (A28 s3)" in { (td: TestData) =>
      val model =
        """domain d is {
          |  context c is {
          |    type Num is Integer
          |    constant Label is String = "hi"
          |    handler h is {
          |      on init {
          |        let count: Num = "1"
          |        let bad = count > Label
          |      }
          |    }
          |  }
          |}
          |""".stripMargin
      parseAndValidate(model, td.name, shouldFailOnErrors = false) { case (_, _, msgs: Messages) =>
        assertValidationMessage(msgs, Error, "requires a numeric operand")
      }
    }

    "reject an ordering comparison of two booleans (A28 s3)" in { (td: TestData) =>
      val model =
        """domain d is {
          |  context c is {
          |    type Flag is Boolean
          |    handler h is {
          |      on init {
          |        let f: Flag = "true"
          |        let g: Flag = "false"
          |        let bad = f < g
          |      }
          |    }
          |  }
          |}
          |""".stripMargin
      parseAndValidate(model, td.name, shouldFailOnErrors = false) { case (_, _, msgs: Messages) =>
        assertValidationMessage(msgs, Error, "requires a numeric operand")
      }
    }

    "accept `==` identity on same-typed refs (A28 s3)" in { (td: TestData) =>
      val model =
        """domain d is {
          |  context c is {
          |    type Num is Integer
          |    handler h is {
          |      on init {
          |        let a: Num = "1"
          |        let b: Num = "2"
          |        let ok = a == b
          |      }
          |    }
          |  }
          |}
          |""".stripMargin
      parseAndValidate(model, td.name, shouldFailOnErrors = false) { case (_, _, msgs: Messages) =>
        msgs.filter(m =>
          m.kind == Error && (m.message.contains("Cannot compare") ||
            m.message.contains("requires a numeric operand"))
        ) mustBe empty
      }
    }

    // ---- A28 slice 2: boolean-expression conditions in when/require/invariant ----

    "reject a non-boolean `when` condition (A28 s2)" in { (td: TestData) =>
      val model =
        """domain d is {
          |  context c is {
          |    type Num is Integer
          |    type Str is String
          |    handler h is {
          |      on init {
          |        let a: Num = "1"
          |        let b: Str = "x"
          |        when a == b then error "unreachable" end
          |      }
          |    }
          |  }
          |}
          |""".stripMargin
      parseAndValidate(model, td.name, shouldFailOnErrors = false) { case (_, _, msgs: Messages) =>
        assertValidationMessage(msgs, Error, "Cannot compare")
      }
    }

    "accept a well-formed boolean `when` condition (A28 s2)" in { (td: TestData) =>
      val model =
        """domain d is {
          |  context c is {
          |    type Flag is Boolean
          |    handler h is {
          |      on init {
          |        let f: Flag = "true"
          |        when f and f then error "unreachable" end
          |      }
          |    }
          |  }
          |}
          |""".stripMargin
      parseAndValidate(model, td.name, shouldFailOnErrors = false) { case (_, _, msgs: Messages) =>
        msgs.filter(m =>
          m.kind == Error && (m.message.contains("Cannot compare") || m.message.contains(
            "must be a boolean"
          ))
        ) mustBe empty
      }
    }

    "reject a non-boolean `require` condition (A28 s2)" in { (td: TestData) =>
      val model =
        """domain d is {
          |  context c is {
          |    type Num is Integer
          |    type Str is String
          |    handler h is {
          |      on init {
          |        let a: Num = "1"
          |        let b: Str = "x"
          |        require a == b
          |      }
          |    }
          |  }
          |}
          |""".stripMargin
      parseAndValidate(model, td.name, shouldFailOnErrors = false) { case (_, _, msgs: Messages) =>
        assertValidationMessage(msgs, Error, "Cannot compare")
      }
    }

    "reject a non-boolean logical operand in a `require` condition (A28 s2)" in { (td: TestData) =>
      val model =
        """domain d is {
          |  context c is {
          |    type Num is Integer
          |    handler h is {
          |      on init {
          |        let a: Num = "1"
          |        let b: Num = "2"
          |        require a and b
          |      }
          |    }
          |  }
          |}
          |""".stripMargin
      parseAndValidate(model, td.name, shouldFailOnErrors = false) { case (_, _, msgs: Messages) =>
        assertValidationMessage(msgs, Error, "must be a boolean")
      }
    }

    "accept a structured boolean invariant condition (A28 s2)" in { (td: TestData) =>
      val model =
        """domain d is {
          |  context c is {
          |    entity e is {
          |      invariant ok is a > b and true
          |    }
          |  }
          |}
          |""".stripMargin
      parseAndValidate(model, td.name, shouldFailOnErrors = false) { case (_, _, msgs: Messages) =>
        msgs.filter(m =>
          m.kind == Error && (m.message.contains("Cannot compare") || m.message.contains(
            "must be a boolean"
          ))
        ) mustBe empty
      }
    }

    // ---- A17: a bare boolean value reference is a first-class, type-checked `when` condition ----

    "accept a bare Boolean field ref as a `when` condition — single name (A17)" in {
      (td: TestData) =>
        parseAndValidate(
          whenEnt("""when flag then error "x" end"""),
          td.name,
          shouldFailOnErrors = false
        ) { case (_, _, msgs: Messages) =>
          msgs.filter(m =>
            m.kind == Error && m.message.contains("must be a Boolean value")
          ) mustBe empty
        }
    }

    "accept a bare Boolean field ref as a `when` condition — dotted path (A17)" in {
      (td: TestData) =>
        parseAndValidate(
          whenEnt("""when order.isPaid then error "x" end"""),
          td.name,
          shouldFailOnErrors = false
        ) { case (_, _, msgs: Messages) =>
          msgs.filter(m =>
            m.kind == Error && m.message.contains("must be a Boolean value")
          ) mustBe empty
        }
    }

    "reject a non-Boolean field ref as a `when` condition (A17)" in { (td: TestData) =>
      parseAndValidate(
        whenEnt("""when count then error "x" end"""),
        td.name,
        shouldFailOnErrors = false
      ) { case (_, _, msgs: Messages) =>
        assertValidationMessage(msgs, Error, "must be a Boolean value")
      }
    }
  }
}
