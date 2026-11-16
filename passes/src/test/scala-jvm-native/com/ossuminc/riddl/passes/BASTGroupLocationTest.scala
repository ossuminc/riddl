/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes

import com.ossuminc.riddl.language.AST.{ContainedGroup, Container, Definition, Root, RiddlValue}
import com.ossuminc.riddl.language.Finder
import com.ossuminc.riddl.language.bast.BASTReader
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.utils.{pc, ec}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Reported by synapify (2026-11-14). `BASTReader.readGroupOrContainedGroupNode` tells a `Group`
  * from a `ContainedGroup` by PEEKING: it reads ahead through an identifier, then seeks back. The
  * identifier read calls `readLocation`, which advances the delta-decoding base `lastLocation`, and
  * `seek` restores only the byte cursor. The identifier is then read again against a base already
  * past it, so every later location decodes shifted -- by `group `'s width per group read.
  *
  * Offsets are what is compared: line and column derive from them, so offset equality is the whole
  * claim.
  */
class BASTGroupLocationTest extends AnyWordSpec with Matchers {

  // Two groups and a contained group, with definitions after each, so a shift introduced by any
  // one of them shows up on something read later. Metadata on the groups mirrors synapify's
  // reproduction, where the shift spread past the group's identifier into its contents.
  private val source: String =
    """domain Shop is {
      |  application context Storefront is {
      |    record Order is { id: String }
      |    group Summary is {
      |      output Note shows "your order" with { briefly "A note" }
      |      contains Inner as group Detail with { briefly "Inner" }
      |      output After shows "after the contained group"
      |    } with { briefly "The summary" }
      |    group Detail is {
      |      output Line shows "a line"
      |    }
      |    page Checkout is {
      |      input Pay acquires Order
      |    } with { briefly "Checkout" }
      |    type Size is any of { Small, Large }
      |  }
      |}
      |""".stripMargin

  private def input: RiddlParserInput = RiddlParserInput(source, "bast-group-loc")

  private def parsed: Root =
    TopLevelParser.parseInput(input, true) match
      case Right(root: Root) => root
      case Left(msgs)        => fail(msgs.format)

  private def bytesOf(root: Root): Array[Byte] =
    val writerResult = Pass.runThesePasses(PassInput(root), Seq(BASTWriterPass.creator()))
    writerResult.outputOf[BASTOutput](BASTWriterPass.name).get.bytes

  /** Both the definition's own location and its IDENTIFIER's: the peek defect shifted the
    * identifier first, and in a model without metadata the definitions' own locations happened to
    * come back right -- so comparing those alone passed with the defect in place (canary-checked).
    */
  private def defsOf(container: Container[? <: RiddlValue]): Seq[(String, String, Int, Int)] =
    Finder(container).recursiveFindByType[Definition].map { d =>
      (d.kind, d.id.value, d.loc.offset, d.id.loc.offset)
    }

  "BAST location round trip through UI groups" should {
    "give every definition the offset it had when parsed" in {
      val root = parsed
      val expected = defsOf(root)
      expected.map(_._2) must contain allOf ("Summary", "Inner", "After", "Detail", "Checkout",
        "Size")
      BASTReader.read(bytesOf(root), Map(input.origin -> input)) match
        case Right(m) =>
          val actual = defsOf(m)
          val mismatches = expected.zip(actual).filter { case (e, a) => e != a }
          withClue(mismatches.map { case (e, a) => s"parsed $e, read back $a" }.mkString("\n")) {
            actual.size mustBe expected.size
            mismatches mustBe empty
          }
        case Left(msgs) => fail(msgs.format)
    }

    "read a contained group back as a ContainedGroup with its group reference" in {
      // Until revision 31 this could not be read at all: ContainedGroup shared Group's tag and the
      // reader's disambiguating peek always chose Group.
      val root = parsed
      val original = Finder(root).recursiveFindByType[ContainedGroup].head
      BASTReader.read(bytesOf(root), Map(input.origin -> input)) match
        case Right(m) =>
          Finder(m).recursiveFindByType[ContainedGroup] match
            case Seq(back) =>
              back.id.value mustBe "Inner"
              back.group.keyword mustBe original.group.keyword
              back.group.pathId.value mustBe original.group.pathId.value
              back.group.loc.offset mustBe original.group.loc.offset
            case other => fail(s"expected one ContainedGroup, got $other")
        case Left(msgs) => fail(msgs.format)
    }

    "recover the parsed line and column for the second group" in {
      val root = parsed
      val original = Finder(root).recursiveFindByType[Definition].find(_.id.value == "Detail").get
      BASTReader.read(bytesOf(root), Map(input.origin -> input)) match
        case Right(m) =>
          val back = Finder(m).recursiveFindByType[Definition].find(_.id.value == "Detail").get
          (back.loc.line, back.loc.col) mustBe (original.loc.line, original.loc.col)
          (back.id.loc.line, back.id.loc.col) mustBe (original.id.loc.line, original.id.loc.col)
        case Left(msgs) => fail(msgs.format)
    }
  }
}
