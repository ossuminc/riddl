/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.utils.pc
import org.scalatest.TestData

/** [1.13]: `put`, `return` and `require … with` are type-checked.
  *
  * Constructor arguments gained this on 2026-08-25; these three did not, and the gap was the same
  * one in each: **it was never a missing policy.** `isAssignmentCompatible` already answers whether
  * an `Id(E)` may fill a `UUID`; nothing at these positions asked it.
  *
  * `put` and `return` DID compare types — but only when both sides resolved to a NAMED type, via
  * `valueType`. A predefined type yields `None` there, so the check silently skipped exactly the
  * case a generator trips over. The named comparison is KEPT, because it is the stricter rule and
  * the right one when it applies: RIDDL treats a declared alias as a distinct name, not a
  * transparent synonym. The TypeExpression check runs only where the named one could not.
  */
class PutReturnRequireTypesTest extends AbstractValidatingTest {

  "require … with" should {

    "reject a value whose type is not what the invariant requires" in { (td: TestData) =>
      val rpi = RiddlParserInput(
        """domain D is {
          |  context C is {
          |    entity E is {
          |      type Wanted is Integer
          |      invariant Inv requires type C.E.Wanted is "something must hold"
          |      record Fields is { count: Integer }
          |      initial state S of record C.E.Fields is {
          |        handler H is {
          |          on init {
          |            require invariant C.E.Inv with "a string, not an Integer"
          |          }
          |        }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin,
        "require-with"
      )
      parseAndValidateAggregate(rpi) { result =>
        val errs = result.messages.justErrors.map(_.message)
        withClue(s"errors were:\n  ${errs.mkString("\n  ")}\n") {
          errs.exists(m => m.contains("requires") && m.contains("String")) mustBe true
        }
      }
    }

    "stay silent when the value matches" in { (td: TestData) =>
      val rpi = RiddlParserInput(
        """domain D is {
          |  context C is {
          |    entity E is {
          |      type Wanted is Integer
          |      invariant Inv requires type C.E.Wanted is "something must hold"
          |      record Fields is { count: Integer }
          |      initial state S of record C.E.Fields is {
          |        handler H is {
          |          on init {
          |            require invariant C.E.Inv with 42
          |          }
          |        }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin,
        "require-with-ok"
      )
      parseAndValidateAggregate(rpi) { result =>
        val bad = result.messages.justErrors.map(_.message).filter(_.contains("requires"))
        withClue(s"unexpected errors:\n  ${bad.mkString("\n  ")}\n") { bad mustBe empty }
      }
    }
  }

  "return" should {

    "reject a value whose type the function does not return" in { (td: TestData) =>
      val rpi = RiddlParserInput(
        """domain D is {
          |  context C is {
          |    type Score is Integer
          |    function F is {
          |      requires { amount: Integer }
          |      returns type C.Score
          |      return "not an Integer"
          |    }
          |  }
          |}
          |""".stripMargin,
        "return-type"
      )
      parseAndValidateAggregate(rpi) { result =>
        val errs = result.messages.justErrors.map(_.message)
        withClue(s"errors were:\n  ${errs.mkString("\n  ")}\n") {
          // The AGGREGATION form of `returns` is deliberately not covered: `validateReturn`
          // handles only `returns <TypeRef>`, and what `return X` means against an aggregate is a
          // separate question this item did not settle.
          errs.exists(m => m.contains("'return' value") && m.contains("String")) mustBe true
        }
      }
    }
  }
}
