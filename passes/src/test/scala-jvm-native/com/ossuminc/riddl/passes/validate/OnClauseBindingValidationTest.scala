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

/** A55: the optional local name bound to an on-clause's message, and the consequence of routing
  * every [[com.ossuminc.riddl.language.AST.ValueRef]] through `ResolutionPass` instead of matching
  * its LAST component by hand in validation.
  */
class OnClauseBindingValidationTest extends AbstractValidatingTest {

  /** An entity whose command carries `a`/`b`/`conditionRed` and whose state carries `count`. */
  private def model(clause: String): String =
    s"""domain d is {
       |  context c is {
       |    command Foo is { a: Integer, b: Integer, conditionRed: Boolean }
       |    event Bar is { a: Integer, b: Integer, red: Boolean }
       |    outlet emitted is event Bar
       |    entity e is {
       |      record CartData is { count: Integer }
       |      state Open of record CartData
       |      handler Ops is {
       |        $clause
       |      }
       |    }
       |  }
       |}
       |""".stripMargin

  private def errorsOf(msgs: Messages): Seq[String] =
    msgs.filter(_.kind == Error).map(_.message)

  "A55 on-clause binding" should {

    "resolve the worked example end to end" in { (td: TestData) =>
      val src = model(
        """on foo: command Foo {
          |          let bar = foo
          |          when foo.conditionRed then
          |            error "red"
          |          end
          |          send event Bar(bar.a, bar.b, bar.conditionRed) to outlet c.emitted
          |        }""".stripMargin
      )
      parseAndValidate(src, td.name, shouldFailOnErrors = false) { case (_, _, msgs: Messages) =>
        errorsOf(msgs) mustBe empty
      }
    }

    "leave a clause without a binding behaving exactly as before" in { (td: TestData) =>
      val src = model(
        """on command Foo {
          |          when conditionRed then
          |            error "red"
          |          end
          |          send event Bar(a, b, conditionRed) to outlet c.emitted
          |        }""".stripMargin
      )
      parseAndValidate(src, td.name, shouldFailOnErrors = false) { case (_, _, msgs: Messages) =>
        errorsOf(msgs) mustBe empty
      }
    }

    "reach a field that collides with the binding name via `foo.foo`, and warn about the overload" in {
      (td: TestData) =>
        val src =
          """domain d is {
            |  context c is {
            |    command Foo is { foo: Integer }
            |    event Bar is { n: Integer }
            |    outlet emitted is event Bar
            |    entity e is {
            |      record CartData is { count: Integer }
            |      state Open of record CartData
            |      handler Ops is {
            |        on foo: command Foo {
            |          send event Bar(foo.foo) to outlet c.emitted
            |        }
            |      }
            |    }
            |  }
            |}
            |""".stripMargin
        // Pin the options: the overload nudge is a Warning, and ambient options vary in a full run.
        pc.withOptions(CommonOptions.default) { _ =>
          parseAndValidate(src, td.name, shouldFailOnErrors = false) {
            case (_, _, msgs: Messages) =>
              errorsOf(msgs) mustBe empty
              assertValidationMessage(msgs, Warning, "has the same name as a field")
          }
        }
    }

    // Proves the binding actually RESOLVES rather than merely being tolerated: the `when` type
    // check can only fire once `foo.a` has been walked to the Integer field it names.
    "type-check a bound-message field, proving the path is genuinely resolved" in {
      (td: TestData) =>
        val src = model(
          """on foo: command Foo {
          |          when foo.a then
          |            error "not boolean"
          |          end
          |          send event Bar(a, b, conditionRed) to outlet c.emitted
          |        }""".stripMargin
        )
        parseAndValidate(src, td.name, shouldFailOnErrors = false) { case (_, _, msgs: Messages) =>
          assertValidationMessage(msgs, Error, "must be a Boolean value")
        }
    }

    "reject a path whose head resolves to nothing" in { (td: TestData) =>
      val src = model(
        """on foo: command Foo {
          |          send event Bar(nosuchthing.a, b, conditionRed) to outlet c.emitted
          |        }""".stripMargin
      )
      parseAndValidate(src, td.name, shouldFailOnErrors = false) { case (_, _, msgs: Messages) =>
        assertValidationMessage(msgs, Error, "is not a 'let'-local")
      }
    }

    // Before A55 only `path.value.last` was matched, so a garbage prefix in front of a real field
    // name validated cleanly. The resolver walks EVERY component now.
    "reject a multi-component path that used to pass on last-component luck" in { (td: TestData) =>
      val src = model(
        """on command Foo {
          |          send event Bar(garbage.nonsense.conditionRed, b, conditionRed) to outlet c.emitted
          |        }""".stripMargin
      )
      parseAndValidate(src, td.name, shouldFailOnErrors = false) { case (_, _, msgs: Messages) =>
        assertValidationMessage(msgs, Error, "is not a 'let'-local")
      }
    }

    "reject a field that does not exist on the bound message" in { (td: TestData) =>
      val src = model(
        """on foo: command Foo {
          |          send event Bar(foo.nosuchfield, b, conditionRed) to outlet c.emitted
          |        }""".stripMargin
      )
      parseAndValidate(src, td.name, shouldFailOnErrors = false) { case (_, _, msgs: Messages) =>
        assertValidationMessage(msgs, Error, "is not a 'let'-local")
      }
    }

    "style-warn about a local name that does not begin lowercase" in { (td: TestData) =>
      val src = model(
        """on Foo2: command Foo {
          |          let Bar2 = Foo2
          |          send event Bar(a, b, conditionRed) to outlet c.emitted
          |        }""".stripMargin
      )
      // Pin the options: these are StyleWarnings, and ambient options vary across a full run.
      pc.withOptions(CommonOptions.default) { _ =>
        parseAndValidate(src, td.name, shouldFailOnErrors = false) { case (_, _, msgs: Messages) =>
          val styles = msgs.filter(_.kind == StyleWarning).map(_.message)
          styles.exists(
            _.contains("on-clause binding 'Foo2' should begin with a lowercase")
          ) mustBe true
          styles.exists(
            _.contains("'let' local 'Bar2' should begin with a lowercase")
          ) mustBe true
        }
      }
    }

    "warn when a local shadows a definition of the same name" in { (td: TestData) =>
      val src = model(
        """on command Foo {
          |          let Bar = a
          |          send event Bar(a, b, conditionRed) to outlet c.emitted
          |        }""".stripMargin
      )
      pc.withOptions(CommonOptions.default) { _ =>
        parseAndValidate(src, td.name, shouldFailOnErrors = false) { case (_, _, msgs: Messages) =>
          assertValidationMessage(msgs, Warning, "shadows a definition of the same name")
        }
      }
    }
  }
}
