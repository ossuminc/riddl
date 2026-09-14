/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{Finder, toSeq}
import com.ossuminc.riddl.language.bast.BASTReader
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.passes.prettify.{PrettifyOutput, PrettifyPass}
import com.ossuminc.riddl.utils.pc
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** `append` / `remove` (2026-09-14) on the parse, prettify and BAST surfaces: every form that
  * parses must be emitted and must survive a round trip at the same place. The keyed `remove`
  * carries its key inline in BAST (sub-kind 23, has-key byte) and the by-value one does not; the
  * three forms are asserted separately so a dropped key cannot hide behind a surviving statement.
  */
class CollectionStatementRoundTripTest extends AnyWordSpec with Matchers {

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

  private def parse(text: String, origin: String): Root =
    TopLevelParser.parseInput(RiddlParserInput(text, origin)) match
      case Right(root) => root
      case Left(msgs)  => fail(s"parse of $origin failed:\n${msgs.format}")

  private def prettify(root: Root): String =
    Pass
      .runThesePasses(
        PassInput(root),
        Pass.standardPasses :+ { (in: PassInput, out: PassesOutput) =>
          PrettifyPass(in, out, PrettifyPass.Options(flatten = true, inputDir = ""))
        }
      )
      .outputs
      .outputOf[PrettifyOutput](PrettifyPass.name)
      .getOrElse(fail("no prettify output"))
      .state
      .filesAsString

  /** The three statements, in source order, as (format, class name). `BASTReader.read` yields a
    * Module rather than a Root, so this takes any container.
    */
  private def collectionStatements(root: Container[?]): Seq[(String, String)] =
    Finder(root.contents).recursiveFindByType[CollectionStatement].map(cs => (cs.format, cs.getClass.getSimpleName))

  private val expected = Seq(
    ("append added.item to field Cart.CartData.items", "AppendStatement"),
    ("remove from field Cart.CartData.items where itemId == removed.itemId", "RemoveStatement"),
    ("remove dropped.tag from field Cart.CartData.tags", "RemoveStatement")
  )

  "append and remove" should {

    "parse to the three nodes, the keyed remove carrying its key" in {
      val root = parse(src, "src")
      collectionStatements(root) mustBe expected
      val keyed = Finder(root.contents).recursiveFindByType[RemoveStatement].map(_.key.map(_.value))
      keyed mustBe Seq(Some("itemId"), None)
    }

    "PRETTIFY to the same spelling and re-parse to the same nodes" in {
      val root = parse(src, "src")
      val pretty = prettify(root)
      expected.foreach { case (text, _) =>
        withClue(s"prettify lost '$text':\n$pretty") { pretty must include(text) }
      }
      collectionStatements(parse(pretty, "regen")) mustBe expected
    }

    "survive BAST, key included" in {
      val root = parse(src, "src")
      val written = Pass
        .runThesePasses(PassInput(root), Seq(BASTWriterPass.creator()))
        .outputOf[BASTOutput](BASTWriterPass.name)
        .getOrElse(fail("no BAST output"))
      BASTReader.read(written.bytes) match
        case Right(back) =>
          collectionStatements(back) mustBe expected
          Finder(back.contents).recursiveFindByType[RemoveStatement].map(_.key.map(_.value)) mustBe
            Seq(Some("itemId"), None)
        case Left(errors) => fail(s"BAST read failed: ${errors.format}")
      end match
    }
  }
}
