/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.language.RuleId
import com.ossuminc.riddl.utils.{CommonOptions, pc}

import org.scalatest.TestData

/** B2 (2026-09-22): the storage statements are typed against the schema's stored record, and a
  * bare name in a `where`/`set` is a ROW field — the `foreach` element mechanism, so it SHADOWS
  * a same-named message field.
  */
class RepositoryStatementsValidationTest extends AbstractValidatingTest {

  private def diagnostics(source: String, origin: String): Messages =
    var captured: Messages = Messages.empty
    pc.withOptions(CommonOptions.default) { _ =>
      parseAndValidate(source, origin, shouldFailOnErrors = false) { (_, _, messages) =>
        captured = messages
        succeed
      }
    }
    captured

  private def of(msgs: Messages, rule: RuleId): Seq[Message] = msgs.filter(_.ruleId.contains(rule))

  /** `body` is the `on command` clause body; `schemaLines` the schema's clauses after the entry;
    * `extra` adds a second schema or another context definition.
    */
  private def model(
    body: String,
    schemaLines: String = "        key on field C.Row.id",
    extra: String = ""
  ): String =
    s"""domain D is {
       |  context C is {
       |    record Row is { id: String, name: String, size: Integer } with { briefly "r" }
       |    record Other is { other: String } with { briefly "o" }
       |    command E is { id: String, name: String } with { briefly "cmd" }
       |    query Find replies result Found is { id: String } with { briefly "q" }
       |    result Found is { name: String } with { briefly "f" }
       |    repository R is {
       |      schema S is relational
       |        of rows as record C.Row
       |$schemaLines
       |      with { briefly "s" }
       |$extra
       |      inlet In is command C.E with { briefly "i" }
       |      inlet Asks is query C.Find with { briefly "i" }
       |      outlet Out is result C.Found with { briefly "o" }
       |      handler H is {
       |        on e: command C.E is {
       |          $body
       |        }
       |        on q: query C.Find is {
       |          let found: record C.Row = query one S.rows where id == q.id
       |          reply result C.Found(name = found.name)
       |        }
       |        on other is { error "unexpected" }
       |      } with { briefly "h" }
       |    } with { briefly "r" }
       |  } with { briefly "c" }
       |} with { briefly "d" }
       |""".stripMargin

  private val store = """store record C.Row(id = e.id, name = e.name, size = 1) in S.rows"""

  "the storage statements" should {

    "validate a model using all four plus a query, qualified and bare" in { (td: TestData) =>
      val msgs = diagnostics(
        model(
          s"""$store
             |          upsert record C.Row(id = e.id, name = e.name, size = 2) in rows
             |          update S.rows set name = e.name, size = 3 where id == e.id
             |          delete from rows where name == e.name""".stripMargin
        ),
        td.name
      )
      withClue(msgs.justErrors.format) { msgs.justErrors mustBe empty }
    }

    "refuse a value that is not the table's record" in { (td: TestData) =>
      val msgs = diagnostics(model("""store record C.Other(other = e.name) in S.rows"""), td.name)
      val found = of(msgs, RuleId.StoreValueNotTableRecord)
      found.size mustBe 1
      found.head.message must include("'Row'")
      found.head.message must include("'Other'")
    }

    "refuse an upsert when the schema declares no key for that record" in { (td: TestData) =>
      val msgs = diagnostics(
        model("""upsert record C.Row(id = e.id, name = e.name, size = 1) in rows""",
          schemaLines = "        index on field C.Row.name"),
        td.name
      )
      of(msgs, RuleId.UpsertNeedsKey).size mustBe 1
      // and with a key it is clean
      of(diagnostics(model("""upsert record C.Row(id = e.id, name = e.name, size = 1) in rows"""), td.name),
        RuleId.UpsertNeedsKey) mustBe empty
    }

    "refuse an assignment to a field the row does not have" in { (td: TestData) =>
      val msgs = diagnostics(model("""update S.rows set nosuch = e.name where id == e.id"""), td.name)
      val found = of(msgs, RuleId.UpdateFieldNotInRow)
      found.size mustBe 1
      found.head.message must include("'nosuch'")
    }

    "type an assignment against the row field" in { (td: TestData) =>
      of(diagnostics(model("""update S.rows set name = e.id where id == e.id"""), td.name),
        RuleId.ValueTypeMismatch) mustBe empty
      of(diagnostics(model("""update S.rows set name = 5 where id == e.id"""), td.name),
        RuleId.ValueTypeMismatch).size mustBe 1
    }

    "refuse a table that is not in the schema, and a bare table with two schemas" in {
      (td: TestData) =>
        of(diagnostics(model("""delete from S.nosuch where id == e.id"""), td.name),
          RuleId.TableNotInSchema).size mustBe 1
        val two = diagnostics(
          model("""delete from rows where id == e.id""",
            extra = """      schema S2 is relational
                      |        of others as record C.Other
                      |      with { briefly "s2" }""".stripMargin),
          td.name
        )
        of(two, RuleId.TableNotInSchema).size mustBe 1
    }

    "refuse `query` outside a repository" in { (td: TestData) =>
      val src =
        """domain D is {
          |  context C is {
          |    record Row is { id: String } with { briefly "r" }
          |    handler H is { on init is { let x = query S.rows } } with { briefly "h" }
          |  } with { briefly "c" }
          |} with { briefly "d" }
          |""".stripMargin
      of(diagnostics(src, td.name), RuleId.QueryOutsideRepository).size mustBe 1
    }
  }

  "the row scope" should {

    "resolve a bare name to the row's field, shadowing a same-named message field" in {
      (td: TestData) =>
        // `name` is BOTH a row field (String) and a message field (String); as a row field it
        // must type, and assigning an Integer to it must fail on the ROW's type.
        val ok = diagnostics(model("""delete from rows where name == e.name"""), td.name)
        withClue(ok.justErrors.format) { ok.justErrors mustBe empty }
        of(diagnostics(model("""update rows set size = e.name where id == e.id"""), td.name),
          RuleId.ValueTypeMismatch).size mustBe 1
    }

    "report a `where` naming a field the row does not have" in { (td: TestData) =>
      // A comparison operand takes the comparand route, so this is `value-comparand-unresolved`.
      of(diagnostics(model("""delete from rows where nosuch == e.id"""), td.name),
        RuleId.ComparandUnresolved).size mustBe 1
    }

    "require the `where` to be boolean" in { (td: TestData) =>
      diagnostics(model("""delete from rows where id"""), td.name).justErrors must not be empty
    }
  }

  "A23 and discharge" should {

    "treat a store as an effect, so a refusal after it is reported" in { (td: TestData) =>
      val msgs = diagnostics(model(s"""$store
                                      |          require invariant Nope""".stripMargin), td.name)
      // the invariant does not exist, so at minimum the refusals-first rule must have seen the
      // store as an effect; assert on the A23 message text rather than a specific rule id
      msgs.justErrors.map(_.message).mkString("\n") must include("refus")
    }
  }
}
