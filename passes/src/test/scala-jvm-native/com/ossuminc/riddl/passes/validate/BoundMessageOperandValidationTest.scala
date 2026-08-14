/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.utils.{CommonOptions, pc}

import org.scalatest.TestData

/** A56: `tell`/`send` accept a name bound by the enclosing on-clause.
  *
  * The point of this suite is the NEGATIVE case. A bound operand that resolves produces no
  * messages, and a check that never runs produces no messages either, so "it validates clean" is
  * not evidence on its own — the unbound case must actually redden.
  */
class BoundMessageOperandValidationTest extends AbstractValidatingTest {

  private def model(clause: String): String =
    s"""domain d is {
       |  context c is {
       |    command Foo is { a: Integer }
       |    entity target is {
       |      handler In is {
       |        on command d.c.Foo is { do "handle" }
       |      }
       |    }
       |    entity e is {
       |      outlet emitted is command d.c.Foo
       |      handler Ops is {
       |        $clause
       |      }
       |    }
       |  }
       |}
       |""".stripMargin

  private def errorsOf(msgs: Messages): Seq[String] =
    msgs.filter(_.kind == Error).map(_.message)

  "A56 bound message operand" should {

    "forward a bound message with `tell`" in { (td: TestData) =>
      val src = model("""on p: command d.c.Foo is { tell p to entity d.c.target }""")
      parseAndValidate(src, td.name, shouldFailOnErrors = false) { case (_, _, msgs: Messages) =>
        errorsOf(msgs) mustBe empty
      }
    }

    "forward a bound message with `send`" in { (td: TestData) =>
      val src = model("""on p: command d.c.Foo is { send p to outlet d.c.e.emitted }""")
      parseAndValidate(src, td.name, shouldFailOnErrors = false) { case (_, _, msgs: Messages) =>
        errorsOf(msgs) mustBe empty
      }
    }

    // The canary. If this ever goes green, the check above has stopped running.
    "reject a name nothing in scope binds" in { (td: TestData) =>
      val src = model("""on p: command d.c.Foo is { tell nosuchname to entity d.c.target }""")
      parseAndValidate(src, td.name, shouldFailOnErrors = false) { case (_, _, msgs: Messages) =>
        val errs = errorsOf(msgs)
        errs.size mustBe 1
        errs.head must include("'nosuchname'")
        errs.head must include("does not name a message value")
      }
    }

    "reject an unbound name in a clause that binds nothing at all" in { (td: TestData) =>
      val src = model("""on command d.c.Foo is { tell p to entity d.c.target }""")
      parseAndValidate(src, td.name, shouldFailOnErrors = false) { case (_, _, msgs: Messages) =>
        errorsOf(msgs).exists(_.contains("does not name a message value")) mustBe true
      }
    }

    "leave a keyword-led operand unaffected" in { (td: TestData) =>
      val src = model("""on command d.c.Foo is { tell command d.c.Foo(a = "the a") to entity d.c.target }""")
      parseAndValidate(src, td.name, shouldFailOnErrors = false) { case (_, _, msgs: Messages) =>
        errorsOf(msgs) mustBe empty
      }
    }
  }
}
