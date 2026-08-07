/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Finder
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.utils.pc

import org.scalatest.TestData

/** A24: `call function F(args)` value expression. "Functions only" is a target restriction (the
  * callee is a `FunctionRef`); a call is effect-free (functions are pure, A26) so it composes
  * anywhere a value is valid — function bodies and handlers alike. Its type is the called
  * function's `output`; arguments bind to the fields of the function's `input` aggregate.
  */
class CallValidationTest extends AbstractValidatingTest {

  // Two sibling functions in a context: `Add` (the callee, with input/output), `Now` (no input),
  // `Sink` (no output), and a `Caller` whose body exercises the call. `Amount` is an aliased field
  // type so per-argument type-compatibility checks can fire (primitive Integer fields are skipped).
  private def calc(body: String): String =
    s"""domain d is {
       |  context Calc is {
       |    type Amount is Integer
       |    record Args is { a: Amount, b: Amount }
       |    record Sum is { total: Integer }
       |    record Diff is { d: Integer }
       |    function Add is {
       |      requires record Args
       |      returns record Sum
       |      return record Sum(total = "t")
       |    }
       |    function Now is {
       |      returns record Sum
       |      return record Sum(total = "0")
       |    }
       |    function Sink is {
       |      requires record Args
       |      ???
       |    }
       |    function Caller is {
       |      requires record Args
       |      returns record Sum
       |      $body
       |    }
       |  }
       |}
       |""".stripMargin

  // An entity handler that sets a Sum-typed state field — used to exercise a call in `set` position
  // (and to prove a call is valid in a handler, not only in a function body).
  private def ent(body: String): String =
    s"""domain d is {
       |  context Calc is {
       |    record Args is { a: Integer, b: Integer }
       |    record Sum is { total: Integer }
       |    command Go is { ??? }
       |    function Add is {
       |      requires record Args
       |      returns record Sum
       |      return record Sum(total = "t")
       |    }
       |    entity E is {
       |      record Data is { s: Sum }
       |      state St of record Data
       |      handler H is {
       |        on command Go {
       |          $body
       |        }
       |      }
       |    }
       |  }
       |}
       |""".stripMargin

  private def parse(src: String, origin: String): Root =
    TopLevelParser.parseInput(RiddlParserInput(src, origin)) match
      case Right(root) => root
      case Left(msgs)  => fail(s"parse of $origin failed:\n${msgs.format}")

  "Call value (A24)" should {

    "parse `let r = call function Add(a, b)` to a Call value" in { (td: TestData) =>
      val root = parse(
        calc("""let r = call function Add(a = "1", b = "2")
                              |      return r""".stripMargin),
        td.name
      )
      val lets = Finder(root).recursiveFindByType[LetStatement]
      val callLet = lets
        .find(_.expression.isInstanceOf[Call])
        .getOrElse(fail(s"no let bound to a Call found; lets: ${lets.map(_.expression)}"))
      callLet.expression match
        case c: Call =>
          c.function.pathId.value mustBe Seq("Add")
          c.args.size mustBe 2
          c.args.map(_.name.map(_.value)) mustBe Seq(Some("a"), Some("b"))
        case other => fail(s"expected a Call, got $other")
    }

    "accept a well-formed named-argument call resolved and type-checked" in { (td: TestData) =>
      parseAndValidate(
        calc("""return call function Add(a = "1", b = "2")"""),
        td.name,
        shouldFailOnErrors = false
      ) { case (_, _, msgs: Messages) =>
        msgs.filter(m =>
          m.kind == Error && (m.message.contains("Call of") ||
            m.message.contains("input field of") ||
            m.message.contains("was not found") ||
            m.message.contains("was expected") ||
            m.message.contains("'return' value has type"))
        ) mustBe empty
      }
    }

    "accept a well-formed positional-argument call" in { (td: TestData) =>
      parseAndValidate(
        calc("""return call function Add("1", "2")"""),
        td.name,
        shouldFailOnErrors = false
      ) { case (_, _, msgs: Messages) =>
        msgs.filter(m => m.kind == Error && m.message.contains("Call of")) mustBe empty
      }
    }

    "accept an empty-argument call to a no-input function" in { (td: TestData) =>
      parseAndValidate(
        calc("""return call function Now()"""),
        td.name,
        shouldFailOnErrors = false
      ) { case (_, _, msgs: Messages) =>
        msgs.filter(m =>
          m.kind == Error && (m.message.contains("Call of") || m.message.contains("input field"))
        ) mustBe empty
      }
    }

    "reject a call with too many positional arguments" in { (td: TestData) =>
      parseAndValidate(
        calc("""return call function Add("1", "2", "3")"""),
        td.name,
        shouldFailOnErrors = false
      ) { case (_, _, msgs: Messages) =>
        assertValidationMessage(msgs, Error, "takes 2 input fields")
      }
    }

    "reject a call with an unknown named argument" in { (td: TestData) =>
      parseAndValidate(
        calc("""return call function Add(bogus = "x")"""),
        td.name,
        shouldFailOnErrors = false
      ) { case (_, _, msgs: Messages) =>
        assertValidationMessage(msgs, Error, "is not an input field of")
      }
    }

    "reject positional arguments following named arguments" in { (td: TestData) =>
      parseAndValidate(
        calc("""return call function Add(a = "1", "2")"""),
        td.name,
        shouldFailOnErrors = false
      ) { case (_, _, msgs: Messages) =>
        assertValidationMessage(msgs, Error, "positional arguments must precede named arguments")
      }
    }

    "reject an argument whose type does not match the input field" in { (td: TestData) =>
      parseAndValidate(
        calc("""let x: Diff = "1"
               |      return call function Add(a = x, b = x)""".stripMargin),
        td.name,
        shouldFailOnErrors = false
      ) { case (_, _, msgs: Messages) =>
        assertValidationMessage(msgs, Error, "expects")
      }
    }

    "reject a call to a function with no output" in { (td: TestData) =>
      parseAndValidate(
        calc("""return call function Sink(a = "1", b = "2")"""),
        td.name,
        shouldFailOnErrors = false
      ) { case (_, _, msgs: Messages) =>
        assertValidationMessage(msgs, Error, "has no 'returns' output")
      }
    }

    "reject a call whose target is not a Function (wrong kind)" in { (td: TestData) =>
      parseAndValidate(
        calc("""return call function Sum(total = "1")"""),
        td.name,
        shouldFailOnErrors = false
      ) { case (_, _, msgs: Messages) =>
        assertValidationMessage(msgs, Error, "Function")
      }
    }

    "reject a call to an undefined function" in { (td: TestData) =>
      parseAndValidate(
        calc("""return call function Nope(a = "1", b = "2")"""),
        td.name,
        shouldFailOnErrors = false
      ) { case (_, _, msgs: Messages) =>
        assertValidationMessage(msgs, Error, "was not found in the symbol table")
      }
    }

    "accept a call in `set` position inside a handler (call valid outside functions)" in {
      (td: TestData) =>
        parseAndValidate(
          ent("""set field E.St.s to call function Add(a = "1", b = "2")"""),
          td.name,
          shouldFailOnErrors = false
        ) { case (root, _, msgs: Messages) =>
          // A Call is a Value (not a Contents node): reach it via the containing SetStatement.
          val calls =
            Finder(root).recursiveFindByType[SetStatement].map(_.value).collect { case c: Call =>
              c
            }
          calls.size mustBe 1
          calls.head.function.pathId.value mustBe Seq("Add")
          msgs.filter(m =>
            m.kind == Error && (m.message.contains("Call of") ||
              m.message.contains("input field of") ||
              m.message.contains("was not found"))
          ) mustBe empty
        }
    }

    "accept and resolve a call nested as a constructor argument" in { (td: TestData) =>
      parseAndValidate(
        calc("""return record Sum(total = call function Add(a = "1", b = "2"))"""),
        td.name,
        shouldFailOnErrors = false
      ) { case (root, _, msgs: Messages) =>
        // The call is nested as a constructor argument inside the ReturnStatement's value.
        val calls = Finder(root)
          .recursiveFindByType[ReturnStatement]
          .map(_.value)
          .collect { case c: Constructor => c }
          .flatMap(_.args)
          .map(_.value)
          .collect { case c: Call => c }
        calls.size mustBe 1
        msgs.filter(m =>
          m.kind == Error && (m.message.contains("Call of") || m.message.contains("was not found"))
        ) mustBe empty
      }
    }
  }
}
