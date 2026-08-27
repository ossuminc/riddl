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

/** Message-value-source design, Task 1: `checkMessageOperandSource` widens A56's `tell`/`send`
  * operand check from "names an on-clause binding" to "resolves to a value whose type is, or
  * aliases to, a command/event/query/result" — the same A55/lifecycle-parameter resolution walk
  * ([[ValidationPass.valueRefTypeExpr]]) every other bare `ValueRef` uses.
  *
  * One case per legal source (state-record field, on-clause binding, `let`-local, function result,
  * `ask` result), plus the two rejections: `self` (a synthesized record, not a message, gets its
  * OWN message) and a genuinely unresolvable name (still an Error — the canary that proves the
  * check still runs).
  *
  * Every positive case here was an ERROR before this widening (`checkBoundMessageOperand` probed
  * only `refMap.definitionOf[Type](vr.path)`, the on-clause-binding key). Reverting the widening —
  * restoring `checkBoundMessageOperand`'s narrow probe — must turn every case below RED except
  * "on-clause binding" and the two rejections, which is the load-bearing proof this suite exists to
  * provide (see task-1-report.md for the actual revert-and-rerun).
  */
class MessageOperandSourceValidationTest extends AbstractValidatingTest {

  private def errorsOf(msgs: Messages): Seq[String] = msgs.filter(_.kind == Error).map(_.message)

  // `d.c.Foo` is always the message being forwarded; `target` always the destination entity, whose
  // lone handler exists only so the model as a whole validates clean apart from the operand under
  // test.
  private val preamble: String =
    """command Foo is { a: Integer }
      |entity target is {
      |  handler In is {
      |    on command d.c.Foo is { do "handle" }
      |  }
      |}
      |""".stripMargin

  private def model(
    extraContext: String,
    srcBody: String,
    srcExtra: String = ""
  ): String =
    s"""domain d is {
       |  context c is {
       |    $preamble
       |    $extraContext
       |    entity src is {
       |      $srcExtra
       |      handler Ops is {
       |        on command d.c.Foo is {
       |          $srcBody
       |        }
       |      }
       |    }
       |  }
       |}
       |""".stripMargin

  "checkMessageOperandSource" should {

    "accept a state-record field as the operand" in { (td: TestData) =>
      val src = model(
        extraContext = "record Data is { msg: d.c.Foo }",
        srcBody = "tell msg to entity d.c.target",
        srcExtra = "state S of record d.c.Data"
      )
      parseAndValidate(src, td.name, shouldFailOnErrors = false) { case (_, _, msgs: Messages) =>
        errorsOf(msgs) mustBe empty
      }
    }

    "accept an on-clause binding as the operand (A56, must keep working)" in { (td: TestData) =>
      val src =
        s"""domain d is {
           |  context c is {
           |    $preamble
           |    entity src is {
           |      handler Ops is {
           |        on p: command d.c.Foo is { tell p to entity d.c.target }
           |      }
           |    }
           |  }
           |}
           |""".stripMargin
      parseAndValidate(src, td.name, shouldFailOnErrors = false) { case (_, _, msgs: Messages) =>
        errorsOf(msgs) mustBe empty
      }
    }

    "accept a `let`-local bound to a constructed message as the operand" in { (td: TestData) =>
      val src = model(
        extraContext = "",
        srcBody = """let m = command d.c.Foo(a = 1)
            |tell m to entity d.c.target""".stripMargin
      )
      parseAndValidate(src, td.name, shouldFailOnErrors = false) { case (_, _, msgs: Messages) =>
        errorsOf(msgs) mustBe empty
      }
    }

    "accept a function result as the operand" in { (td: TestData) =>
      val src = model(
        extraContext = """function MakeFoo is {
            |  returns command d.c.Foo
            |  return command d.c.Foo(a = 1)
            |}""".stripMargin,
        srcBody = """let m = call function d.c.MakeFoo()
            |tell m to entity d.c.target""".stripMargin
      )
      parseAndValidate(src, td.name, shouldFailOnErrors = false) { case (_, _, msgs: Messages) =>
        errorsOf(msgs) mustBe empty
      }
    }

    "accept an `ask` result as the operand" in { (td: TestData) =>
      val src = model(
        extraContext = """result Answer is { v: Integer }
            |query Ask replies result d.c.Answer is { q: Integer }
            |entity Ledger is {
            |  handler H is {
            |    on query d.c.Ask is { reply result d.c.Answer(v = 1) }
            |  }
            |}""".stripMargin,
        srcBody = """let m = ask query d.c.Ask of entity d.c.Ledger
            |tell m to entity d.c.target""".stripMargin
      )
      parseAndValidate(src, td.name, shouldFailOnErrors = false) { case (_, _, msgs: Messages) =>
        errorsOf(msgs) mustBe empty
      }
    }

    "accept a widened `send` operand too, not only `tell`" in { (td: TestData) =>
      val src = model(
        extraContext = "",
        srcBody = """let m = command d.c.Foo(a = 1)
            |send m to outlet d.c.src.emitted""".stripMargin,
        srcExtra = "outlet emitted is command d.c.Foo"
      )
      parseAndValidate(src, td.name, shouldFailOnErrors = false) { case (_, _, msgs: Messages) =>
        errorsOf(msgs) mustBe empty
      }
    }

    "reject `self` with its own message, not the generic one" in { (td: TestData) =>
      val src = model(extraContext = "", srcBody = "tell self to entity d.c.target")
      parseAndValidate(src, td.name, shouldFailOnErrors = false) { case (_, _, msgs: Messages) =>
        val errs = errorsOf(msgs)
        errs.size mustBe 1
        errs.head must include("'self'")
        errs.head must include("not a message")
        errs.head mustNot include("does not name a message value")
      }
    }

    "reject a value whose type is not a message (wrong kind)" in { (td: TestData) =>
      val src = model(
        extraContext = "record Data is { count: Integer }",
        srcBody = "tell count to entity d.c.target",
        srcExtra = "state S of record d.c.Data"
      )
      parseAndValidate(src, td.name, shouldFailOnErrors = false) { case (_, _, msgs: Messages) =>
        val errs = errorsOf(msgs)
        errs.exists(_.contains("not a command, event, query or result")) mustBe true
      }
    }

    // The canary. If this ever goes green, the check above has stopped running.
    "still reject a genuinely unresolvable name" in { (td: TestData) =>
      val src = model(extraContext = "", srcBody = "tell nosuchname to entity d.c.target")
      parseAndValidate(src, td.name, shouldFailOnErrors = false) { case (_, _, msgs: Messages) =>
        val errs = errorsOf(msgs)
        errs.exists(_.contains("does not name a message value")) mustBe true
      }
    }
  }
}
