/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Finder
import com.ossuminc.riddl.language.bast.{BASTReader, FORMAT_REVISION}
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.passes.validate.AbstractValidatingTest
import com.ossuminc.riddl.passes.{BASTOutput, BASTWriterPass, Pass, PassInput}
import com.ossuminc.riddl.utils.pc

import org.scalatest.TestData

/** A20 typed holes, Task 4: `writeValue`'s `PromptValue` arm (tag 4) now APPENDS an optional `as
  * <type>` ascription (`writeOption`/`writeTypeExpression`) after the prompt literal, so an
  * untyped prompt's bytes carry one extra "none" flag byte and a typed one carries the flag plus
  * the encoded type. This rides the unreleased revision 18 (shared with numeric literals and the
  * URL fix) -- see [[com.ossuminc.riddl.language.bast.FORMAT_REVISION]].
  *
  * Every case below is exercised BOTH unascribed (`typeEx` must decode back to `None`) and
  * ascribed (`typeEx` must decode back to the SAME `TypeExpression`, not merely "present"), since a
  * codec that always writes a type would break every existing model, and one that writes the wrong
  * bytes for the type would still look "present" without asserting identity.
  *
  * A definition AFTER the prompt-bearing constructs is asserted to decode intact: a BAST error
  * names where the reader DERAILED, never what derailed it, so a writer/reader field-count mismatch
  * here would surface as garbage on the sibling context that follows, not on the PromptValue
  * itself. That is the case that actually proves the codec, per `URLBASTRoundTripTest`'s pattern.
  */
class TypedHoleBASTRoundTripTest extends AbstractValidatingTest {

  /** parse -> BAST -> decode. Returns the decoded tree, which is a Module (the nebula the writer
    * wraps a Root in), not a Root.
    */
  private def roundTrip(src: String, origin: String): Module =
    val root = TopLevelParser.parseInput(RiddlParserInput(src, origin), true) match
      case Right(r)   => r
      case Left(msgs) => fail(s"parse failed:\n${msgs.format}")
    val bytes = Pass
      .runThesePasses(PassInput(root), Seq(BASTWriterPass.creator()))
      .outputOf[BASTOutput](BASTWriterPass.name)
      .getOrElse(fail("BASTWriterPass produced no output"))
      .bytes
    BASTReader(bytes).read() match
      case Right(decoded) => decoded
      case Left(msgs)     => fail(s"BAST round trip failed:\n${msgs.format}")

  // `let plain`/`let typed` exercise the value in a `let`; the `when` condition exercises it in a
  // second position with a PREDEFINED type (Boolean) rather than an alias, since the two ascription
  // shapes take different TypeExpression codec paths (TYPE_UNIQUE_ID-adjacent alias resolution vs
  // a predefined-type tag). Context `After` follows C, so a misaligned PromptValue payload derails
  // onto something this test can name.
  private val src =
    """domain D is {
      |  context C is {
      |    type OrderId is String
      |    command Add is { sku: String }
      |    entity E is {
      |      record Data is { note: String }
      |      state S of record Data
      |      handler H is {
      |        on command Add is {
      |          let plain = prompt("x")
      |          let typed = prompt("x") as OrderId
      |          when prompt("x") as Boolean then
      |            do "something"
      |          end
      |        }
      |      }
      |    }
      |  }
      |  context After is {
      |    type Marker is Integer
      |  }
      |}
      |""".stripMargin

  private def letExpression(root: Module, name: String): Value =
    Finder(root)
      .recursiveFindByType[LetStatement]
      .find(_.identifier.value == name)
      .getOrElse(fail(s"no let statement named '$name' found"))
      .expression

  private def whenConditionPromptValue(root: Module): PromptValue =
    Finder(root)
      .recursiveFindByType[WhenStatement]
      .map(_.condition)
      .collectFirst { case pv: PromptValue => pv }
      .getOrElse(fail("no PromptValue when condition found"))

  "an unascribed prompt(...)" should {
    "decode with typeEx = None through a BAST round trip" in { (td: TestData) =>
      val root = roundTrip(src, "unascribed-let")
      letExpression(root, "plain") match
        case pv: PromptValue => pv.typeEx mustBe None
        case other           => fail(s"expected a PromptValue, got $other")
    }
  }

  "an aliased ascription (prompt(...) as OrderId)" should {
    "decode as the SAME AliasedTypeExpression through a BAST round trip" in { (td: TestData) =>
      val root = roundTrip(src, "aliased-let")
      letExpression(root, "typed") match
        case pv: PromptValue =>
          pv.typeEx.getOrElse(fail("typeEx was None")) match
            case ate: AliasedTypeExpression => ate.pathId.value.last mustBe "OrderId"
            case other => fail(s"expected an AliasedTypeExpression, got $other")
        case other => fail(s"expected a PromptValue, got $other")
    }
  }

  "a predefined ascription (prompt(...) as Boolean) in a `when` condition" should {
    "decode as the SAME predefined type through a BAST round trip" in { (td: TestData) =>
      val root = roundTrip(src, "predefined-when")
      whenConditionPromptValue(root).typeEx.getOrElse(fail("typeEx was None")) mustBe a[Bool]
    }
  }

  "a definition AFTER the prompt-bearing constructs" should {
    "still decode intact" in { (td: TestData) =>
      val root = roundTrip(src, "after-prompt-definitions")
      val after = Finder(root).recursiveFindByType[Context].find(_.id.value == "After")
        .getOrElse(fail("the 'After' context did not survive"))
      val marker = Finder(after).recursiveFindByType[Type].find(_.id.value == "Marker")
      marker must not be empty
    }
  }

  "the format revision" should {
    "stay at 18 -- this rides the unreleased shared bump, it does not add one" in {
      (td: TestData) =>
        FORMAT_REVISION mustBe 18.toShort
    }
  }
}
