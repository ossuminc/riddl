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

/** `append` / `remove` (Reid, 2026-09-14) obey every rule `set` obeys -- they are the same kind of
  * thing, a LOCAL state transformation -- plus two of their own: the target must be a COLLECTION
  * field, and a keyed `remove` must name a field of the element record. The value is typed against
  * the element type (or the key field's type) through the same check `set` uses, so a mismatch
  * draws `set`'s rule. And a fold that appends or removes is DERIVED for rule 3: it says what it
  * changes, which is the whole point of the statements.
  */
class CollectionStatementValidationTest extends AbstractValidatingTest {

  private def diagnostics(source: String, origin: String): Messages =
    var captured: Messages = Messages.empty
    pc.withOptions(CommonOptions.default) { _ =>
      parseAndValidate(source, origin, shouldFailOnErrors = false) { (_, _, messages) =>
        captured = messages
        succeed
      }
    }
    captured

  private def errorsOf(msgs: Messages, rule: RuleId): Seq[Message] =
    msgs.filter(m => m.kind == Messages.Error && m.ruleId.contains(rule))

  /** An event-sourced cart; `fold` is the body of the `on event ItemAdded` clause. */
  private def cart(fold: String, extra: String = ""): String =
    s"""domain D is {
       |  context Shopping is {
       |    record Item is { itemId: String, qty: Integer } with { briefly "a line" }
       |    command AddItem yields event Shopping.Cart.ItemAdded is { item: Shopping.Item } with { briefly "c" }
       |    event-sourced entity Cart is {
       |      record CartData is { cartId: String, items: Shopping.Item*, tags: String*, note: String, one: String? } with { briefly "s" }
       |      event ItemAdded is { item: Shopping.Item, tag: String } with { briefly "e" }
       |      state Live of record Cart.CartData
       |      inlet In is command Shopping.AddItem with { briefly "i" }
       |      outlet Out is event Cart.ItemAdded with { briefly "o" }
       |      handler H is {
       |        on init is { yield event Cart.ItemAdded(item = record Shopping.Item(itemId = "y", qty = 1), tag = "t") }
       |        on a: command Shopping.AddItem is { yield event Cart.ItemAdded(item = a.item, tag = "t") }
       |        on added: event Cart.ItemAdded is { $fold }
       |        on other is { error "unexpected" }
       |      } with { briefly "h" }
       |    } with { briefly "c" }
       |$extra
       |  } with { briefly "s" }
       |} with { briefly "d" }
       |""".stripMargin

  "a collection statement in an event-sourced fold" should {

    "validate with zero errors in all three forms" in { (td: TestData) =>
      val msgs = diagnostics(
        cart(
          """append added.item to field Cart.CartData.items
            |          remove from field Cart.CartData.items where itemId == added.tag
            |          remove added.tag from field Cart.CartData.tags""".stripMargin
        ),
        td.name
      )
      withClue(msgs.justErrors.format) { msgs.justErrors mustBe empty }
    }

    "make the fold DERIVED, so rule 3 does not fire" in { (td: TestData) =>
      val msgs = diagnostics(cart("append added.item to field Cart.CartData.items"), td.name)
      msgs.filter(_.ruleId.contains(RuleId.EntityEventSourcedProseFolds)) mustBe empty
    }
  }

  "the target" should {

    "be an Error when it is not a collection field (a plain String)" in { (td: TestData) =>
      val found = errorsOf(
        diagnostics(cart("append added.tag to field Cart.CartData.note"), td.name),
        RuleId.CollectionFieldNotCollection
      )
      found.size mustBe 1
      found.head.message must include("not a collection")
    }

    "be an Error when it is merely OPTIONAL (`T?` holds at most one value)" in { (td: TestData) =>
      errorsOf(
        diagnostics(cart("append added.tag to field Cart.CartData.one"), td.name),
        RuleId.CollectionFieldNotCollection
      ).size mustBe 1
    }
  }

  "a keyed remove" should {

    "be an Error when the key is not a field of the element record" in { (td: TestData) =>
      val found = errorsOf(
        diagnostics(cart("remove from field Cart.CartData.items where sku == added.tag"), td.name),
        RuleId.CollectionKeyNotAField
      )
      found.size mustBe 1
      found.head.message must include("sku")
    }

    "be an Error when the element type is not a record at all" in { (td: TestData) =>
      val found = errorsOf(
        diagnostics(cart("remove from field Cart.CartData.tags where itemId == added.tag"), td.name),
        RuleId.CollectionKeyNotAField
      )
      found.size mustBe 1
      found.head.message must include("not a record")
    }
  }

  "the same rules as set" should {

    "reject it in a Context handler, which owns no state" in { (td: TestData) =>
      val msgs = diagnostics(
        cart(
          "append added.item to field Cart.CartData.items",
          """    handler CH is {
            |      on command Shopping.AddItem is { append "x" to field Cart.CartData.tags }
            |    } with { briefly "ch" }""".stripMargin
        ),
        td.name
      )
      errorsOf(msgs, RuleId.SetNotAllowed).exists(_.message.contains("'append'")) mustBe true
    }

    "reject it outside an `on event` clause of an event-sourced entity (R3)" in { (td: TestData) =>
      val src = cart("append added.item to field Cart.CartData.items")
        .replace(
          "on a: command Shopping.AddItem is { yield event Cart.ItemAdded(item = a.item, tag = \"t\") }",
          "on a: command Shopping.AddItem is { append a.item to field Cart.CartData.items yield event Cart.ItemAdded(item = a.item, tag = \"t\") }"
        )
      errorsOf(diagnostics(src, td.name), RuleId.EventSourcedMutationScope)
        .exists(_.message.contains("'append'")) mustBe true
    }

    "count as an EFFECT for refusals-first (A23): a `require` after it is an Error" in {
      (td: TestData) =>
        // `require` is banned in an `on event` clause, so put the ordering test in a non-
        // event-sourced entity's command clause, where both statements are legal.
        val src =
          """domain D is {
            |  context C is {
            |    command Tag is { tag: String } with { briefly "c" }
            |    entity Bag is {
            |      record Data is { tags: String* } with { briefly "d" }
            |      state Live of record Bag.Data
            |      inlet In is command Tag with { briefly "i" }
            |      handler H is {
            |        on t: command Tag is {
            |          append t.tag to field Bag.Data.tags
            |          require "the tag is allowed"
            |        }
            |        on other is { error "unexpected" }
            |      } with { briefly "h" }
            |    } with { briefly "b" }
            |  } with { briefly "c" }
            |} with { briefly "d" }
            |""".stripMargin
        errorsOf(diagnostics(src, td.name), RuleId.RefusalAfterEffect) must not be empty
    }
  }
}
