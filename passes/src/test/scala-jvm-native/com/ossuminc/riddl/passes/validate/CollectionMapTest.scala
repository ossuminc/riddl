/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.{Messages, RuleId}
import com.ossuminc.riddl.language.Messages.Message
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.passes.Riddl
import com.ossuminc.riddl.utils.{CommonOptions, pc}

import org.scalatest.TestData

/** The collection map, `each of <coll> as <e> [where <p>] yielding <value>` (Reid, 2026-11-21,
  * reversing B5's "no `map`" ruling of 2026-09-22 in favour of the GENERAL map).
  *
  * Each case yields `Done(f = <arg>)` with `f` declared `<fieldType>`, so the map's TYPE is tested
  * by what it may fill, through the existing assignability check and the optional-for-required
  * Advisory. Every silent case asserts the whole model is error-free, so a fixture that failed to
  * parse could not pass vacuously.
  */
class CollectionMapTest extends AbstractValidatingTest {

  private def messages(src: String, td: TestData): Seq[Message] =
    var out: Seq[Message] = Seq.empty
    pc.withOptions(CommonOptions.default) { _ =>
      out = Riddl.parseAndValidate(RiddlParserInput(src, td), shouldFailOnError = false) match
        case Right(result) => result.messages
        case Left(msgs)    => msgs
    }
    out

  private def of(msgs: Seq[Message], rule: RuleId): Seq[Message] = msgs.filter(_.ruleId.contains(rule))
  private def errors(msgs: Seq[Message]): Seq[Message] = msgs.filter(_.kind == Messages.Error)
  private def clue(msgs: Seq[Message]): String = msgs.map(_.format).mkString("\n")

  /** In scope: `g` (the `Go` command) and the state `E.S`, described in the record `E.F`. */
  private def model(fieldType: String, arg: String, letLine: String = ""): String =
    s"""domain D is { context C is {
       |  record Line is { sku: String, qty: Integer, drink: Boolean, note: String? } with { briefly "l" }
       |  record Ticket is { code: String, n: Integer } with { briefly "t" }
       |  command Go yields event Done is { sku: String } with { briefly "c" }
       |  event Done is { f: $fieldType } with { briefly "e" }
       |  entity E is {
       |    record F is { lines: C.Line*, plus: C.Line+, n: Integer }
       |      with { briefly "f" }
       |    state S of record E.F
       |    inlet I is command Go
       |    outlet O is event Done
       |    handler H is {
       |      on g: command Go is {
       |        $letLine
       |        yield event Done(f = $arg)
       |      }
       |      on other is { error "x" }
       |    }
       |  }
       |} }
       |""".stripMargin

  private def mustBeClean(fieldType: String, arg: String, td: TestData, letLine: String = ""): Unit =
    val msgs = messages(model(fieldType, arg, letLine), td)
    withClue(clue(msgs)) {
      errors(msgs) mustBe empty
      of(msgs, RuleId.OptionalForRequired) mustBe empty
    }

  private def mustAdvise(fieldType: String, arg: String, td: TestData): Unit =
    val msgs = messages(model(fieldType, arg), td)
    withClue(clue(msgs)) {
      errors(msgs) mustBe empty
      of(msgs, RuleId.OptionalForRequired).size mustBe 1
    }

  private def mustError(fieldType: String, arg: String, td: TestData, letLine: String = ""): Seq[Message] =
    val msgs = messages(model(fieldType, arg, letLine), td)
    withClue(clue(msgs)) { errors(msgs) must not be empty }
    errors(msgs)

  private val ticket = "record C.Ticket(code = l.sku, n = l.qty)"

  "`each of … yielding`" should {

    "be a collection of the projection's type" in { (td: TestData) =>
      mustBeClean("String*", "each of E.S.lines as l yielding l.sku", td)
      mustBeClean("C.Ticket*", s"each of E.S.lines as l yielding $ticket", td)
      mustBeClean("Integer*", "each of E.S.lines as l yielding l.qty * 2", td)
    }

    "refuse a projection of the wrong type" in { (td: TestData) =>
      val found = mustError("Integer*", "each of E.S.lines as l yielding l.sku", td)
      found.flatMap(_.ruleId) must contain(RuleId.ValueTypeMismatch)
      mustError("C.Line*", s"each of E.S.lines as l yielding $ticket", td)
    }

    "keep the source's cardinality when unfiltered" in { (td: TestData) =>
      mustBeClean("C.Ticket+", s"each of E.S.plus as l yielding $ticket", td)
      // `T*` may be empty, so it reaches a `T+` only with the usual Advisory.
      mustAdvise("C.Ticket+", s"each of E.S.lines as l yielding $ticket", td)
    }

    "drop the minimum to zero when filtered, which a `T+` hears as the Advisory" in {
      (td: TestData) =>
        val filtered = s"each of E.S.plus as l where not l.drink yielding $ticket"
        mustBeClean("C.Ticket*", filtered, td)
        mustAdvise("C.Ticket+", filtered, td)
        // The `T{n,m}` arm of the typing is exercised in RangeCardinalityTest, now that the range
        // has a source spelling (2026-11-23).
    }

    "parse `where x == empty yielding`, not read `yielding` as a type" in { (td: TestData) =>
      mustBeClean("String*", "each of E.S.lines as l where l.note != empty yielding l.sku", td)
    }

    "be a collection operand of `count of`" in { (td: TestData) =>
      mustBeClean("Integer", "count of (each of E.S.lines as l where l.drink yielding l.sku)", td)
    }

    "bind the element in the filter and the projection, and nowhere else" in { (td: TestData) =>
      mustError("String*", "each of E.S.lines as l yielding l.nosuch", td)
      mustError("String*", "each of E.S.lines as l where l.nosuch yielding l.sku", td)
      mustError(
        "String",
        "l.sku",
        td,
        "let s = count of (each of E.S.lines as l yielding l.sku)"
      )
    }

    "refuse a source that is not a collection" in { (td: TestData) =>
      val msgs = messages(model("Integer*", "each of E.S.n as l yielding l"), td)
      withClue(clue(msgs)) { of(msgs, RuleId.NotACollection) must not be empty }
    }

    "refuse a filter that is not boolean" in { (td: TestData) =>
      mustError("String*", "each of E.S.lines as l where l.sku yielding l.sku", td)
    }
  }

  "the reactive-bbq shape" should {
    "validate: a filtered reshape into a record constructor, inside a `create`" in {
      (td: TestData) =>
        val src =
          """domain D is { context C is {
            |  record Line is { sku: String, qty: Integer, drink: Boolean } with { briefly "l" }
            |  record Ticket is { code: String, n: Integer } with { briefly "t" }
            |  command Submit is { lines: C.Line* } with { briefly "s" }
            |  entity Kitchen is {
            |    record KF is { items: C.Ticket* } with { briefly "kf" }
            |    state KS of record Kitchen.KF
            |    handler KH is {
            |      on init(items: C.Ticket*) is { morph entity C.Kitchen to state C.Kitchen.KS
            |        with record Kitchen.KF(items = items) }
            |    } with { briefly "kh" }
            |  } with { briefly "k" }
            |  entity Front is {
            |    record FF is { n: Integer } with { briefly "ff" }
            |    state FS of record Front.FF
            |    inlet I is command Submit
            |    handler FH is {
            |      on s: command Submit is {
            |        let k = create entity C.Kitchen(
            |          each of s.lines as line where not line.drink
            |            yielding record C.Ticket(code = line.sku, n = line.qty)
            |        )
            |        log k
            |      }
            |      on other is { error "x" }
            |    } with { briefly "fh" }
            |  } with { briefly "f" }
            |} with { briefly "c" } } with { briefly "d" }
            |""".stripMargin
        val msgs = messages(src, td)
        withClue(clue(msgs)) { errors(msgs) mustBe empty }
    }
  }
}
