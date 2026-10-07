/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.utils.{CommonOptions, pc}
import org.scalatest.TestData

/** A bare `empty` takes the type of its POSITION, and every position that supplies one checks it
  * (BACKLOG [1.26], Reid 2026-10-04..06).
  *
  * Every value in RIDDL is typed. `empty` carries no type in its own spelling, so where the position
  * supplies one -- the field it is assigned to or supplies, a function's `returns`, an output's
  * type, an invariant's `requires`, a stored row, the operand opposite it in a comparison -- the
  * value is legal exactly where that type's minimum cardinality is zero. Until 2.4.0 only `let`,
  * `set` on an alias-typed field, `append`/`remove` and constructor arguments checked it; every
  * other position accepted `empty` against a type that cannot be empty. One positive and one
  * negative case per position, so a position that stopped checking reddens.
  */
class EmptyValuePositionsTest extends AbstractValidatingTest {

  // Most cases assert an ABSENCE, which a fixture that failed to parse would satisfy for free.
  private def diagnostics(src: String, origin: String): Messages =
    var captured: Messages = Messages.empty
    pc.withOptions(CommonOptions(showWarnings = true)) { _ =>
      parseAndValidate(src, origin, shouldFailOnErrors = false) { (_, _, messages) =>
        captured = messages
        succeed
      }
    }
    captured.find(_.message.contains("Expected one of")) match
      case Some(m) => fail(s"fixture did not parse, so any absence proves nothing:\n${m.format}")
      case None    => captured
  end diagnostics

  private def emptyErrors(src: String, origin: String): Messages =
    diagnostics(src, origin).justErrors.filter(_.message.contains("is not a value of"))

  private def mustAccept(src: String, origin: String): Unit =
    val found = emptyErrors(src, origin)
    withClue(found.map(_.format).mkString("\n")) { found mustBe empty }

  private def mustRefuse(src: String, origin: String, mentioning: String): Unit =
    val found = emptyErrors(src, origin)
    withClue(diagnostics(src, origin).map(_.format).mkString("\n")) {
      found must not be empty
      found.head.message must include(mentioning)
    }

  private def entityModel(stmt: String): String =
    s"""domain D is {
       |  context C is {
       |    type MaybeNote is String?
       |    record In is { opt: String?, req: String } with { briefly "in" }
       |    record Out is { o: String } with { briefly "out" }
       |    record Data is { opt: String?, req: String, tags: String* } with { briefly "data" }
       |    command Go is { why: String } with { briefly "cmd" }
       |    function F is {
       |      requires record In
       |      returns record Out
       |      return record Out(o = "x")
       |    } with { briefly "fn" }
       |    invariant Positive requires record In is "it holds" with { briefly "inv" }
       |    entity Target is {
       |      handler TH is {
       |        on init(seed: String?, must: String) { do "start" }
       |      } with { briefly "th" }
       |    } with { briefly "t" }
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

  "a `set` to a field typed INLINE" should {
    "accept `empty` for an optional" in { (td: TestData) =>
      mustAccept(entityModel("set field Data.opt to empty"), td.name)
    }
    "refuse `empty` for a required field" in { (td: TestData) =>
      mustRefuse(entityModel("set field Data.req to empty"), td.name, "req")
    }
  }

  "an `append` to a collection whose element is typed inline" should {
    "refuse `empty` as an element that must be present" in { (td: TestData) =>
      mustRefuse(entityModel("append empty to field Data.tags"), td.name, "append")
    }
  }

  "a `let` with a PREDEFINED declared type" should {
    "refuse `empty`, which a bare predefined type has no room for" in { (td: TestData) =>
      mustRefuse(entityModel("let x: String = empty\n          do \"used\""), td.name, "'let x'")
    }
    "accept `empty` when the declared type is a name for an optional" in { (td: TestData) =>
      mustAccept(entityModel("let x: MaybeNote = empty\n          do \"used\""), td.name)
    }
  }

  "a `call` argument" should {
    "accept `empty` for an optional input" in { (td: TestData) =>
      mustAccept(
        entityModel("let r = call function F(opt = empty, req = \"x\")\n          do \"used\""),
        td.name
      )
    }
    "refuse `empty` for a required input" in { (td: TestData) =>
      mustRefuse(
        entityModel("let r = call function F(opt = empty, req = empty)\n          do \"used\""),
        td.name,
        "input 'req'"
      )
    }
  }

  "an `initiate` argument" should {
    "refuse `empty` for a required `on init` parameter, and accept it for an optional one" in {
      (td: TestData) =>
        val found = emptyErrors(
          entityModel(
            "let t = initiate entity Target(seed = empty, must = empty)\n          do \"used\""
          ),
          td.name
        )
        withClue(found.map(_.format).mkString("\n")) {
          found.size mustBe 1
          found.head.message must include("parameter 'must'")
        }
    }
  }

  "a `require … with`" should {
    "refuse `empty` for a `requires` record" in { (td: TestData) =>
      mustRefuse(entityModel("require invariant Positive with empty"), td.name, "requires")
    }
  }

  private def functionModel(returns: String, stmt: String): String =
    s"""domain D is {
       |  context C is {
       |    type MaybeNote is String?
       |    record Out is { o: String } with { briefly "out" }
       |    function F is {
       |      returns $returns
       |      $stmt
       |    } with { briefly "fn" }
       |  } with { briefly "c" }
       |} with { briefly "d" }
       |""".stripMargin

  "a `return`" should {
    "refuse `empty` for a record result" in { (td: TestData) =>
      mustRefuse(functionModel("record Out", "return empty"), td.name, "returns")
    }
  }

  private def appModel(presents: String): String =
    s"""domain D is {
       |  application context App is {
       |    type Greeting is String
       |    type MaybeGreeting is String?
       |    command Refresh is { why: String } with { briefly "cmd" }
       |    group Main is {
       |      output Panel presents type $presents
       |    } with { briefly "g" }
       |    handler Screen is {
       |      on command Refresh { put empty to output Panel }
       |    } with { briefly "h" }
       |  } with { briefly "c" }
       |} with { briefly "d" }
       |""".stripMargin

  "a `put`" should {
    "accept `empty` for an output presenting an optional" in { (td: TestData) =>
      mustAccept(appModel("MaybeGreeting"), td.name)
    }
    "refuse `empty` for an output presenting a required value" in { (td: TestData) =>
      mustRefuse(appModel("Greeting"), td.name, "Panel")
    }
  }

  private def repoModel(stmt: String): String =
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
       |          $stmt
       |        }
       |      } with { briefly "h" }
       |    } with { briefly "r" }
       |  } with { briefly "c" }
       |} with { briefly "d" }
       |""".stripMargin

  "an `update … set`" should {
    "accept `empty` for an optional row field" in { (td: TestData) =>
      mustAccept(repoModel("update S.rows set note = empty, id = e.id where id == e.id"), td.name)
    }
    "refuse `empty` for a required row field" in { (td: TestData) =>
      mustRefuse(
        repoModel("update S.rows set id = empty, note = e.id where id == e.id"),
        td.name,
        "field 'id'"
      )
    }
  }

  "a `store`" should {
    "refuse `empty` as a row, which is one record" in { (td: TestData) =>
      mustRefuse(repoModel("store empty in S.rows"), td.name, "row")
    }
  }

  "a comparison" should {
    "accept `empty` opposite an optional" in { (td: TestData) =>
      mustAccept(repoModel("delete from rows where note == empty"), td.name)
    }
    "refuse `empty` opposite a required value" in { (td: TestData) =>
      mustRefuse(repoModel("delete from rows where id == empty"), td.name, "'id'")
    }
  }
}
