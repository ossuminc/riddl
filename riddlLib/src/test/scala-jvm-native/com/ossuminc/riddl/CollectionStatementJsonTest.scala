/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Finder
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.utils.pc
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** `append` / `remove` (2026-09-14) on the JSON surface: `$kind: "append"` / `"remove"`, the keyed
  * form's `key` present and the by-value form's absent, and the round trip a fixed point.
  */
class CollectionStatementJsonTest extends AnyWordSpec with Matchers {

  private val src =
    """domain Carts is {
      |  context Shopping is {
      |    record Item is { itemId: String, qty: Integer } with { briefly "a line" }
      |    event-sourced entity Cart is {
      |      record CartData is { cartId: String, items: Shopping.Item*, tags: String* } with { briefly "s" }
      |      event ItemAdded is { item: Shopping.Item } with { briefly "e" }
      |      event ItemRemoved is { itemId: String } with { briefly "e" }
      |      event TagDropped is { tag: String } with { briefly "e" }
      |      state Live of record Cart.CartData
      |      handler H is {
      |        on added: event Cart.ItemAdded is { append added.item to field Cart.CartData.items }
      |        on removed: event Cart.ItemRemoved is {
      |          remove from field Cart.CartData.items where itemId == removed.itemId
      |        }
      |        on dropped: event Cart.TagDropped is { remove dropped.tag from field Cart.CartData.tags }
      |        on other is { error "unexpected" }
      |      } with { briefly "h" }
      |    } with { briefly "c" }
      |  } with { briefly "s" }
      |} with { briefly "d" }
      |""".stripMargin

  private def parse(text: String): Root =
    TopLevelParser.parseInput(RiddlParserInput(text, "src")) match
      case Right(root) => root
      case Left(msgs)  => fail(s"parse failed:\n${msgs.format}")

  private def formats(root: Root): Seq[String] =
    Finder(root).recursiveFindByType[CollectionStatement].map(_.format)

  "append and remove in JSON" should {

    "serialize with their kinds and the key only where written" in {
      // Whitespace-insensitive: the writer pretty-prints.
      val json = RiddlLib.root2Json(parse(src)).replaceAll("\\s+", "")
      json must include("\"kind\":\"append\"")
      json must include("\"kind\":\"remove\"")
      json must include("\"key\":\"itemId\"")
      // exactly ONE key: the by-value remove must not grow one
      json.split("\"key\":").length - 1 mustBe 1
    }

    "round-trip to the same statements and be a JSON fixed point" in {
      val root = parse(src)
      val json1 = RiddlLib.root2Json(root)
      RiddlLib.parseJson(json1, "json") match
        case RiddlResult.Success(back) =>
          formats(back) mustBe formats(root)
          RiddlLib.root2Json(back) mustBe json1
        case RiddlResult.Failure(msgs) => fail(s"JSON reparse failed:\n${msgs.format}")
    }
  }
}
