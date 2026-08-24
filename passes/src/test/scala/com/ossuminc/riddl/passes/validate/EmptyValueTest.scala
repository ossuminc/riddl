/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.utils.{CommonOptions, pc}
import org.scalatest.TestData

/** `empty` / `none` — the minimum-cardinality inhabitant of a type (Reid, 2026-08-23).
  *
  * Reported by riddl-models: RIDDL could DECLARE `T?` and `T*` but could not WRITE their empty
  * inhabitants, so a model could express acquiring a value and never releasing one — releasing a
  * hold, un-assigning a driver, emptying a cart. A type system that can declare a type but not
  * name one of its inhabitants is incomplete at that type.
  *
  * **One literal, and `none` is a SYNONYM** — both spellings build the identical node with no flag
  * recording which was written, the same choice `not`/`!` made, because a spelling flag lets two
  * ASTs meaning the same thing compare unequal. Prettify converges `none` to `empty`.
  *
  * **The rule is minimum cardinality zero**, which is what lets one literal serve both the absent
  * optional and the empty collection: they are the same inhabitant under different upper bounds.
  */
class EmptyValueTest extends AbstractValidatingTest {

  private def messagesFor(src: String, td: TestData): Messages =
    var captured: Messages = List.empty
    pc.withOptions(
      CommonOptions(showStyleWarnings = true, showWarnings = true, provideTips = true)
    ) { _ =>
      parseAndValidateDomain(RiddlParserInput(src, td), shouldFailOnErrors = false) {
        case (_, _, msgs) =>
          captured = msgs
          succeed
      }
    }
    captured
  end messagesFor

  private def errs(msgs: Messages): Messages = msgs.filter(_.isError)

  private def model(stmt: String): String =
    s"""domain Dom is {
       |  context Ctx is {
       |    event Cleared is { why: String(1,20) }
       |    type Notes is String(1,20)?
       |    type Items is String(1,20)*
       |    type Tags is String(1,20)+
       |    record Data is { note: Notes  items: Items  tags: Tags }
       |    entity Ent is {
       |      state S of record Ctx.Data is {
       |        handler H is {
       |          on event Ctx.Cleared is {
       |$stmt
       |          }
       |        }
       |      }
       |    }
       |  }
       |}
       |""".stripMargin

  "a bare `empty`" should {
    "set an optional field to absent" in { (td: TestData) =>
      val msgs = messagesFor(model("""            set field Data.note to empty"""), td)
      withClue(msgs.map(_.message).mkString("\n")) { errs(msgs) mustBe empty }
    }

    "set a collection field to empty" in { (td: TestData) =>
      val msgs = messagesFor(model("""            set field Data.items to empty"""), td)
      withClue(msgs.map(_.message).mkString("\n")) { errs(msgs) mustBe empty }
    }

    "be an Error against a type requiring at least one value" in { (td: TestData) =>
      val msgs = messagesFor(model("""            set field Data.tags to empty"""), td)
      val found = errs(msgs).filter(_.message.contains("requires at least one value"))
      withClue(msgs.map(_.message).mkString("\n")) { found must not be empty }
    }
  }

  "`none`" should {
    "be accepted as a synonym, with no separate behaviour" in { (td: TestData) =>
      val withNone = messagesFor(model("""            set field Data.note to none"""), td)
      val withEmpty = messagesFor(model("""            set field Data.note to empty"""), td)
      withClue(withNone.map(_.message).mkString("\n")) {
        errs(withNone) mustBe empty
        // Same defect surface either way -- there is no node that remembers the spelling.
        errs(withNone).size mustBe errs(withEmpty).size
      }
    }
  }

  "the ascribed form" should {
    "carry its own type, so it needs no expected type from the position" in { (td: TestData) =>
      val msgs = messagesFor(model("""            let e = empty String(1,20)*
                                     |            do "used"""".stripMargin), td)
      withClue(msgs.map(_.message).mkString("\n")) { errs(msgs) mustBe empty }
    }

    "be an Error when the ascribed type requires at least one value" in { (td: TestData) =>
      val msgs = messagesFor(model("""            set field Data.note to empty String(1,20)+"""), td)
      val found = errs(msgs).filter(_.message.contains("minimum cardinality is zero"))
      withClue(msgs.map(_.message).mkString("\n")) { found must not be empty }
    }

    "be an Error on a bare type, which always has exactly one value" in { (td: TestData) =>
      val msgs = messagesFor(model("""            set field Data.note to empty String(1,20)"""), td)
      val found = errs(msgs).filter(_.message.contains("minimum cardinality is zero"))
      withClue(msgs.map(_.message).mkString("\n")) { found must not be empty }
    }

    "resolve its type reference, so a nonexistent one is reported" in { (td: TestData) =>
      val msgs = messagesFor(model("""            set field Data.note to empty Nonexistent*"""), td)
      withClue(msgs.map(_.message).mkString("\n")) {
        errs(msgs).filter(_.message.contains("Nonexistent")) must not be empty
      }
    }
  }

  "an unascribed `empty` followed by another statement" should {
    "NOT swallow it as an ascription" in { (td: TestData) =>
      // Statements are whitespace-separated with no terminator and an aliased type is a bare path,
      // so without the statement-keyword guard the second `set` parsed as the first's ascription.
      val msgs = messagesFor(
        model("""            set field Data.note to empty
                |            set field Data.items to empty""".stripMargin),
        td
      )
      withClue(msgs.map(_.message).mkString("\n")) { errs(msgs) mustBe empty }
    }
  }

  /** A bare `empty` as a CONSTRUCTOR ARGUMENT, which rc.23 shipped unchecked (riddl-models,
    * 2026-08-24). This is the position models actually write it in, and it was the one place the
    * check could not see: `checkValueType` takes an expected *named* Type and a field typed
    * `TimeStamp` names none. The field itself is available in `validateConstructor`, so an earlier
    * claim that constructor arguments carry no expected type was too pessimistic — what they lack
    * is a named Type, not the type.
    */
  private def ctorModel(args: String): String =
    s"""domain Dom is {
       |  context Ctx is {
       |    event Cleared is { why: String(1,20) }
       |    record R is {
       |      opt: String(1,20)?
       |      req: TimeStamp
       |      lots: String(1,20)+
       |    }
       |    entity Ent is {
       |      state S of record Ctx.R is {
       |        handler H is {
       |          on event Ctx.Cleared is { set state S to record Ctx.R($args) }
       |        }
       |      }
       |    }
       |  }
       |}
       |""".stripMargin

  "a bare `empty` constructor argument" should {
    "be accepted for an optional field" in { (td: TestData) =>
      val msgs = messagesFor(ctorModel("""opt = empty"""), td)
      withClue(msgs.map(_.message).mkString("\n")) {
        errs(msgs).filter(_.message.contains("is not a value of field")) mustBe empty
      }
    }

    "be an Error for a required field, naming the field and its type" in { (td: TestData) =>
      val msgs = messagesFor(ctorModel("""req = empty"""), td)
      val found = errs(msgs).filter(_.message.contains("is not a value of field"))
      withClue(msgs.map(_.message).mkString("\n")) {
        found must not be empty
        found.head.message must include("'req'")
        found.head.message must include("TimeStamp")
      }
    }

    "be an Error for a one-or-more collection" in { (td: TestData) =>
      val msgs = messagesFor(ctorModel("""lots = empty"""), td)
      val found = errs(msgs).filter(_.message.contains("is not a value of field"))
      withClue(msgs.map(_.message).mkString("\n")) {
        found must not be empty
        found.head.message must include("'lots'")
      }
    }

    "report each offending argument, not just the first" in { (td: TestData) =>
      val msgs = messagesFor(ctorModel("""opt = empty, req = empty, lots = empty"""), td)
      val found = errs(msgs).filter(_.message.contains("is not a value of field"))
      withClue(msgs.map(_.message).mkString("\n")) { found.size mustBe 2 }
    }

    "be checked for POSITIONAL arguments too" in { (td: TestData) =>
      // Positional args pair to fields by index; arity is reported separately.
      val msgs = messagesFor(ctorModel("""empty, empty, empty"""), td)
      val found = errs(msgs).filter(_.message.contains("is not a value of field"))
      withClue(msgs.map(_.message).mkString("\n")) { found.size mustBe 2 }
    }
  }
}
