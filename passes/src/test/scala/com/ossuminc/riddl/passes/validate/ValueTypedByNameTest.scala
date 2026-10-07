/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.{Messages, RuleId}
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.utils.{CommonOptions, pc}
import org.scalatest.TestData

/** Every value is typed, by a type NAME (BACKLOG [1.26], Reid 2026-10-04..06): the three new
  * Errors.
  *
  *   - `value-ascription-not-a-name`: `empty String?` and `prompt(…) as Recipe*` ascribe a type
  *     EXPRESSION. Admitted by mistake in 2.0; an Error, not a deprecation, because it contradicted
  *     a rule the language always had. A bare predefined type (`Real`) IS a name.
  *   - `value-empty-untyped`: a bare `empty` where nothing supplies a type.
  *   - `value-empty-ascription-contradicts`: an ascription that is not, syntactically, the
  *     position's declared type -- so an inline-typed field takes only a bare `empty`.
  *
  * Plus the acceptance case riddl-generator filed: `update T set f = empty where k == v` parses
  * and validates.
  */
class ValueTypedByNameTest extends AbstractValidatingTest {

  private def errors(src: String, origin: String): Messages =
    var captured: Messages = Messages.empty
    pc.withOptions(CommonOptions(showWarnings = true, provideTips = true)) { _ =>
      parseAndValidate(src, origin, shouldFailOnErrors = false) { (_, _, messages) =>
        captured = messages
        succeed
      }
    }
    captured.find(_.message.contains("Expected one of")) match
      case Some(m) => fail(s"fixture did not parse:\n${m.format}")
      case None    => captured.justErrors

  private def ruled(src: String, origin: String, rule: RuleId): Messages =
    errors(src, origin).filter(_.ruleId.contains(rule))

  private def mustBeClean(src: String, origin: String): Unit =
    val errs = errors(src, origin)
    withClue(errs.map(_.format).mkString("\n")) { errs mustBe empty }

  private def model(stmt: String): String =
    s"""domain D is {
       |  context C is {
       |    type MaybeNote is String?
       |    type OtherNote is String?
       |    type Params is String
       |    record Data is { opt: String?, named: MaybeNote } with { briefly "d" }
       |    command Go is { why: String } with { briefly "cmd" }
       |    invariant Holds requires type Params is "it holds" with { briefly "inv" }
       |    entity E is {
       |      state S of record Data is {
       |        handler H is { on command Go is {
       |          $stmt
       |        } } with { briefly "h" }
       |      } with { briefly "s" }
       |    } with { briefly "e" }
       |  } with { briefly "c" }
       |} with { briefly "d" }
       |""".stripMargin

  "an ascription that is a type EXPRESSION" should {
    "be an Error on `empty`" in { (td: TestData) =>
      ruled(model("let e = empty String?\n          do \"x\""), td.name,
        RuleId.AscriptionNotAName) must have size 1
    }
    "be an Error on `prompt(…) as`" in { (td: TestData) =>
      ruled(model("let p = prompt(\"p\") as String*\n          do \"x\""), td.name,
        RuleId.AscriptionNotAName) must have size 1
    }
    "be an Error in a `when` condition, which never reaches the generic value walk" in {
      (td: TestData) =>
        ruled(model("when prompt(\"p\") as Boolean? then do \"x\" end"), td.name,
          RuleId.AscriptionNotAName) must have size 1
    }
    "be an Error in a `require … with`, which never reaches it either" in { (td: TestData) =>
      ruled(model("require invariant Holds with prompt(\"p\") as String(1,9)"), td.name,
        RuleId.AscriptionNotAName) must have size 1
    }
    "NOT be an Error for a bare predefined type, which is a name" in { (td: TestData) =>
      mustBeClean(model("let p = prompt(\"p\") as Real\n          do \"x\""), td.name)
    }
  }

  "a bare `empty` where nothing supplies a type" should {
    "be an Error in an untyped `let`" in { (td: TestData) =>
      ruled(model("let e = empty\n          do \"x\""), td.name, RuleId.EmptyUntyped) must
        have size 1
    }
    "be an Error in a `log`" in { (td: TestData) =>
      ruled(model("log empty"), td.name, RuleId.EmptyUntyped) must have size 1
    }
    "be fine once it names its type" in { (td: TestData) =>
      mustBeClean(model("let e = empty MaybeNote\n          do \"x\""), td.name)
    }
  }

  "an ascription on `empty` at a typed position" should {
    "be fine when it restates the declared name" in { (td: TestData) =>
      mustBeClean(model("set field Data.named to empty MaybeNote"), td.name)
    }
    "contradict a field typed INLINE, which takes only a bare `empty`" in { (td: TestData) =>
      val found = ruled(model("set field Data.opt to empty MaybeNote"), td.name,
        RuleId.EmptyAscriptionContradicts)
      withClue(found.map(_.format).mkString("\n")) {
        found must have size 1
        found.head.suggestion must include("bare 'empty'")
      }
    }
    "contradict a differently-named type, even one that resolves alike" in { (td: TestData) =>
      ruled(model("set field Data.named to empty OtherNote"), td.name,
        RuleId.EmptyAscriptionContradicts) must have size 1
    }
    "contradict a `let`'s declared type" in { (td: TestData) =>
      ruled(model("let e: MaybeNote = empty OtherNote\n          do \"x\""), td.name,
        RuleId.EmptyAscriptionContradicts) must have size 1
    }
    "contradict a constructor field" in { (td: TestData) =>
      ruled(model("set state S to record C.Data(opt = empty MaybeNote, named = empty)"),
        td.name, RuleId.EmptyAscriptionContradicts) must have size 1
    }
  }

  "riddl-generator's acceptance case" should {
    "parse and validate: `update T set f = empty where k == v`" in { (td: TestData) =>
      mustBeClean(
        s"""domain D is {
           |  context C is {
           |    record Row is { id: String, note: String? } with { briefly "r" }
           |    event E is { id: String } with { briefly "e" }
           |    repository R is {
           |      schema S is relational
           |        of rows as record C.Row
           |        key on field C.Row.id
           |      with { briefly "s" }
           |      handler H is {
           |        on e: event C.E is {
           |          update S.rows set note = empty where id == e.id
           |        }
           |      } with { briefly "h" }
           |    } with { briefly "r" }
           |  } with { briefly "c" }
           |} with { briefly "d" }
           |""".stripMargin,
        td.name
      )
    }
  }
}
