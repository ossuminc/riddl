/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Finder
import com.ossuminc.riddl.language.bast.{BASTReader, FORMAT_REVISION}
import com.ossuminc.riddl.passes.{BASTOutput, BASTWriterPass, Pass, PassInput}
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.utils.pc
import com.ossuminc.riddl.passes.validate.AbstractValidatingTest

import org.scalatest.TestData

/** `Constant` and `Method` used to share `NODE_FIELD` with `Field`, and corrupt everything after.
  *
  * Both write MORE than a Field: a Constant appends its literal value, a Method its argument list.
  * The reader could not tell the three apart and read a Field, leaving those extra bytes in the
  * stream — so every byte after such a node was misread. The reader carried the admission in a
  * comment: *"This is ambiguous … For now, assume Field. Writer should disambiguate better."*
  *
  * Reported by riddl-models 2026-08-13 with a 13-node repro, and it cost this session hours: in
  * reactive-bbq the SAME constant surfaced as `Invalid invariant condition kind: 67`, which sent
  * the investigation to bisect an entirely innocent invariant. **A deserialization error names
  * where the reader DERAILED, not what derailed it** — which is why the second case below asserts
  * the value survives rather than merely that nothing threw.
  */
class ConstantAndMethodBASTRoundTripTest extends AbstractValidatingTest {

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

  "a constant" should {

    "survive a BAST round trip" in { (td: TestData) =>
      // The reporters' repro, reduced. Before the fix this threw "Invalid string table index".
      val src =
        """domain D is {
          |  context C is {
          |    constant Rate is Natural = "10" with { briefly "a constant" }
          |    type T is String with { briefly "a type" }
          |  } with { briefly "c" }
          |} with { briefly "d" }
          |""".stripMargin
      val root = roundTrip(src, "constant-roundtrip")
      Finder(root).recursiveFindByType[Constant] must not be empty
    }

    "keep its VALUE, not decode as a Field" in { (td: TestData) =>
      // THE case that distinguishes a real fix from a tag change. Reading a Constant as a Field
      // silently drops the value; only asserting the value catches that.
      val src =
        """domain D is {
          |  context C is {
          |    constant Rate is Natural = "10" with { briefly "a constant" }
          |    type T is String with { briefly "a type" }
          |  } with { briefly "c" }
          |} with { briefly "d" }
          |""".stripMargin
      val c = Finder(roundTrip(src, "constant-value"))
        .recursiveFindByType[Constant]
        .headOption
        .getOrElse(fail("the Constant did not survive as a Constant"))
      c.id.value mustBe "Rate"
      // `Constant.value` widened to `ConstantValue` in the numeric-literals plan (Task 4); this
      // fixture's quoted `"10"` still parses as a LiteralString (Task 6 moves the wire format).
      c.value match
        case ls: LiteralString => ls.s mustBe "10"
        case other             => fail(s"expected a LiteralString value, got $other")
    }

    "not corrupt the nodes that FOLLOW it" in { (td: TestData) =>
      // The actual failure mode: misalignment surfaces at whatever comes next, which is how an
      // innocent invariant got blamed. An invariant after the constant is the reported shape.
      val src =
        """domain D is {
          |  context C is {
          |    constant Rate is Natural = "10" with { briefly "k" }
          |    record R is { total: Integer, floor: Integer } with { briefly "r" }
          |    entity E is {
          |      invariant NonNeg is total >= floor with { briefly "inv" }
          |      state S of record R is { ??? } with { briefly "st" }
          |    } with { briefly "en" }
          |  } with { briefly "c" }
          |} with { briefly "d" }
          |""".stripMargin
      val root = roundTrip(src, "constant-then-invariant")
      Finder(root).recursiveFindByType[Constant] must not be empty
      Finder(root).recursiveFindByType[Invariant] must not be empty
    }
  }

  "a method" should {

    "survive a BAST round trip, with its ARGUMENTS" in { (td: TestData) =>
      // The Method half of the same tag collision. It never had a reported repro -- riddl-models
      // hit the Constant -- but it was written with NODE_FIELD by the same reasoning and appends an
      // argument list the reader never consumed. Asserting the ARGUMENTS, not just the Method, is
      // what makes this a test of the fix rather than of the tag.
      val src =
        """domain D is {
          |  context C is {
          |    record R is {
          |      total: Integer,
          |      scaled(factor: Integer, offset: Integer): Integer
          |    } with { briefly "a record with a method" }
          |    type T is String with { briefly "a type" }
          |  } with { briefly "c" }
          |} with { briefly "d" }
          |""".stripMargin
      val m = Finder(roundTrip(src, "method-roundtrip"))
        .recursiveFindByType[Method]
        .headOption
        .getOrElse(fail("the Method did not survive as a Method"))
      m.id.value mustBe "scaled"
      m.args.map(_.name) mustBe Seq("factor", "offset")
    }
  }

  "the format revision" should {
    "be at least 14, where Constant and Method got distinct tags" in { (td: TestData) =>
      // Pins the bump to the change: a reader without the new tags cannot decode these files, and
      // the revision is what stops it trying.
      FORMAT_REVISION must be >= 14.toShort
    }
  }
}
