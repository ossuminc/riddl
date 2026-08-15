/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{At, Finder}
import com.ossuminc.riddl.language.bast.BASTReader
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.utils.pc
import com.ossuminc.riddl.passes.validate.AbstractValidatingTest

import org.scalatest.TestData

/** Not/! synonymy task 4 (2026-08-15): `WhenStatement` no longer carries a `negated` field, so
  * task 2's placeholder byte (always written/read as a hardcoded `0`) is now GONE from the wire,
  * not merely zeroed. Negation is fully carried by the `NotExpression` inside `condition` (BAST
  * value tag 3, already round-tripping since before this task).
  *
  * Modeled on `ConstantAndMethodBASTRoundTripTest` -- same lesson: a BAST deserialization error
  * names where the reader DERAILED, never what derailed it, so the case that actually proves the
  * writer/reader stayed in lockstep is the one with a definition AFTER the affected node.
  */
class BangNotBASTRoundTripTest extends AbstractValidatingTest {

  private def parse(src: String, origin: String): Root =
    TopLevelParser.parseInput(RiddlParserInput(src, origin)) match
      case Right(root) => root
      case Left(msgs)  => fail(s"parse of $origin failed:\n${msgs.format}")

  /** parse -> BAST -> decode. Returns the decoded tree, which is a Module (the nebula the writer
    * wraps a Root in), not a Root.
    */
  private def roundTrip(src: String, origin: String): Module =
    val root = parse(src, origin)
    val bytes = Pass
      .runThesePasses(PassInput(root), Seq(BASTWriterPass.creator()))
      .outputOf[BASTOutput](BASTWriterPass.name)
      .getOrElse(fail("BASTWriterPass produced no output"))
      .bytes
    BASTReader(bytes).read() match
      case Right(decoded) => decoded
      case Left(msgs)     => fail(s"BAST round trip failed:\n${msgs.format}")

  /** Strip `At` locations so a condition from a freshly-decoded tree compares on SHAPE, not on
    * byte offsets -- mirrors `BangNotSynonymyTest`'s `blank` helper (parser-level sibling of this
    * suite).
    */
  private def blank(v: RiddlValue): RiddlValue = v match
    case NotExpression(_, expr) => NotExpression(At.empty, blank(expr).asInstanceOf[Value])
    case ValueRef(_, path)      => ValueRef(At.empty, blank(path).asInstanceOf[PathIdentifier])
    case PathIdentifier(_, value) => PathIdentifier(At.empty, value)
    case other                    => other

  private def model(condSpelling: String): String =
    s"""domain D is {
       |  context C is {
       |    entity Order is {
       |      handler H is {
       |        on init {
       |          when $condSpelling flag then
       |            do "boom"
       |          end
       |        }
       |      } with { briefly "h" }
       |    } with { briefly "e" }
       |  } with { briefly "c" }
       |} with { briefly "d" }
       |""".stripMargin

  private def whenConditionIn[CV <: RiddlValue](root: Container[CV]): Value =
    Finder(root)
      .recursiveFindByType[WhenStatement]
      .headOption
      .getOrElse(fail("no WhenStatement found"))
      .condition match
      case v: Value => v
      case other    => fail(s"expected the condition to be a Value, got $other")

  "a `not`/`!` condition" should {

    "decode as an identical NotExpression from BOTH spellings, through BAST" in { (td: TestData) =>
      val notDecoded = roundTrip(model("not"), s"not-${td.name}")
      val bangDecoded = roundTrip(model("!"), s"bang-${td.name}")

      val notCond = whenConditionIn(notDecoded)
      val bangCond = whenConditionIn(bangDecoded)

      notCond mustBe a[NotExpression]
      bangCond mustBe a[NotExpression]
      blank(notCond) must be(blank(bangCond))
    }

    "not corrupt the nodes that FOLLOW it -- a statement after the `when`, and a definition " +
      "after the entity that holds it" in { (td: TestData) =>
        // The shape that actually exercises the byte-removal: if the writer stopped emitting the
        // legacy flag byte but the reader still consumed one (or vice versa), everything after the
        // FIRST WhenStatement misreads -- the sibling `let`, the rest of the clause, and every
        // definition after the entity. `AfterType` is the innocent bystander this proves survives.
        val src =
          """domain D is {
            |  context C is {
            |    entity Order is {
            |      handler H is {
            |        on init {
            |          when not flag then
            |            do "boom"
            |          end
            |          let after = "still-here"
            |        }
            |      } with { briefly "h" }
            |    } with { briefly "e" }
            |    type AfterType is String with { briefly "after" }
            |  } with { briefly "c" }
            |} with { briefly "d" }
            |""".stripMargin

        val decoded = roundTrip(src, s"after-${td.name}")

        whenConditionIn(decoded) mustBe a[NotExpression]

        Finder(decoded)
          .recursiveFindByType[LetStatement]
          .find(_.identifier.value == "after")
          .getOrElse(fail("the `let after` statement did not survive"))
          .expression match
          case ls: LiteralString => ls.s mustBe "still-here"
          case other             => fail(s"expected a LiteralString, got $other")

        Finder(decoded)
          .recursiveFindByType[Type]
          .find(_.id.value == "AfterType")
          .getOrElse(fail("the sibling `AfterType` definition did not survive"))
          .typEx mustBe a[String_]
      }
  }
}
