/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{Finder, toSeq}
import com.ossuminc.riddl.language.bast.{BASTReader, FORMAT_REVISION}
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.utils.pc
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** `sequence { ... }` / `parallel { ... }` / `optional { ... }` interaction blocks broke the BAST
  * round trip: `InteractionContainer` (the base of all three) extends `Container` but not `Branch`
  * -- it has no `id`, so it cannot be a `Definition` -- and BASTWriterPass's `traverse` had no case
  * for it, so it fell to the generic `wm: WithMetaData` fallback. That fallback calls `process()`
  * only, which writes the block's header and its contents COUNT (via `writeContents`) but never
  * descends into the steps. The reader's `readContentsDeferred` then consumed N nodes that were
  * never written, desynchronising the stream -- reported far away as "Invalid string table index",
  * or in a large model as a node-boundary overrun naming some innocent earlier node.
  *
  * Reported by riddl-models 2026-08-13 (`task/2026-08-13-interaction-blocks-break-bast-round-trip.md`),
  * same family and same reporting mechanism as the `constant`/`method` fix in
  * `ConstantAndMethodBASTRoundTripTest`: the node count went DOWN when the block was added (9
  * without, 8 with), which is the tell that a writer emitted a container without its children.
  *
  * Sweeping for the same defect shape turned up two more, fixed alongside this one and covered
  * below: an `invariant ... is { <statements> <predicate> }` block (A28 + 2026-08-04) has the
  * identical gap for its statements, and `relationship` -- which reuses `NODE_PIPE` with the 13
  * Interaction kinds -- wrote no discriminator byte at all, so the reader misread every
  * relationship's location as its own dispatch byte.
  */
class InteractionBlockBASTRoundTripTest extends AnyWordSpec with Matchers {

  /** parse -> BAST -> decode, plus the node count the writer reported. Decodes to a Module (the
    * nebula the writer wraps a Root in), not a Root.
    */
  private def roundTrip(src: String, origin: String): (Module, Int) = {
    val root = TopLevelParser.parseInput(RiddlParserInput(src, origin), true) match
      case Right(r)   => r
      case Left(msgs) => fail(s"parse failed:\n${msgs.format}")
    val output = Pass
      .runThesePasses(PassInput(root), Seq(BASTWriterPass.creator()))
      .outputOf[BASTOutput](BASTWriterPass.name)
      .getOrElse(fail("BASTWriterPass produced no output"))
    val decoded = BASTReader(output.bytes).read() match
      case Right(decoded) => decoded
      case Left(msgs)     => fail(s"BAST round trip failed:\n${msgs.format}")
    (decoded, output.nodeCount)
  }

  /** The reporter's repro, parameterized on the block keyword. */
  private def repro(keyword: String): String =
    s"""domain D is {
       |  user U is "A person"
       |  context C is {
       |    type T is String
       |  }
       |  epic E is {
       |    user U wants to "do a thing" so that "a purpose is served"
       |    case K is {
       |      user U wants to "do a step" so that "the step happens"
       |      $keyword {
       |        step for context C is "does the first part"
       |        step for context C is "does the second part"
       |      }
       |    }
       |  }
       |}
       |""".stripMargin

  /** The reporter's negative control: the same two steps, with no wrapping block. */
  private val withoutBlock: String =
    """domain D is {
      |  user U is "A person"
      |  context C is {
      |    type T is String
      |  }
      |  epic E is {
      |    user U wants to "do a thing" so that "a purpose is served"
      |    case K is {
      |      user U wants to "do a step" so that "the step happens"
      |      step for context C is "does the first part"
      |      step for context C is "does the second part"
      |    }
      |  }
      |}
      |""".stripMargin

  "an interaction block" should {

    "survive a BAST round trip: sequence" in {
      val (decoded, _) = roundTrip(repro("sequence"), "seq-bast")
      val block = Finder(decoded).recursiveFindByType[SequentialInteractions] match
        case Seq(one) => one
        case other    => fail(s"expected exactly one SequentialInteractions, got ${other.size}")
      block.contents.toSeq.size mustBe 2
      block.contents.toSeq.collect { case si: SelfInteraction => si.relationship.s } mustBe Seq(
        "does the first part",
        "does the second part"
      )
    }

    "survive a BAST round trip: parallel" in {
      val (decoded, _) = roundTrip(repro("parallel"), "par-bast")
      val block = Finder(decoded).recursiveFindByType[ParallelInteractions] match
        case Seq(one) => one
        case other    => fail(s"expected exactly one ParallelInteractions, got ${other.size}")
      block.contents.toSeq.size mustBe 2
    }

    "survive a BAST round trip: optional" in {
      val (decoded, _) = roundTrip(repro("optional"), "opt-bast")
      val block = Finder(decoded).recursiveFindByType[OptionalInteractions] match
        case Seq(one) => one
        case other    => fail(s"expected exactly one OptionalInteractions, got ${other.size}")
      block.contents.toSeq.size mustBe 2
    }

    "survive a BAST round trip when NESTED inside another block" in {
      val src =
        """domain D is {
          |  user U is "A person"
          |  context C is {
          |    type T is String
          |  }
          |  epic E is {
          |    user U wants to "do a thing" so that "a purpose is served"
          |    case K is {
          |      user U wants to "do a step" so that "the step happens"
          |      sequence {
          |        step for context C is "outer first"
          |        parallel {
          |          step for context C is "inner one"
          |          step for context C is "inner two"
          |        }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin
      val (decoded, _) = roundTrip(src, "nested-bast")
      val outer = Finder(decoded).recursiveFindByType[SequentialInteractions] match
        case Seq(one) => one
        case other    => fail(s"expected exactly one SequentialInteractions, got ${other.size}")
      outer.contents.toSeq.size mustBe 2
      val inner = outer.contents.toSeq.collect { case pi: ParallelInteractions => pi } match
        case Seq(one) => one
        case other    => fail(s"expected exactly one nested ParallelInteractions, got ${other.size}")
      inner.contents.toSeq.size mustBe 2
      // The whole tree must be reachable from the root too, not just from `outer` -- proves the
      // nested block's own contents were themselves traversed (recursively), not merely counted.
      Finder(decoded).recursiveFindByType[ParallelInteractions] must have size 1
      Finder(decoded).recursiveFindByType[SelfInteraction] must have size 3
    }

    "not make the node count go DOWN when the block is ADDED" in {
      // The exact tell that caught this bug: the reporter saw 9 nodes without a sequence block
      // and 8 WITH one, for a file that strictly adds a construct. Fixed, the block must add
      // exactly one node (the container itself) over the same two steps written bare.
      val (_, withoutCount) = roundTrip(withoutBlock, "nocount-without")
      val (_, withCount) = roundTrip(repro("sequence"), "nocount-with")
      withCount must be > withoutCount
      (withCount - withoutCount) mustBe 1
    }
  }

  "an invariant block" should {

    "survive a BAST round trip WITH its statements" in {
      // Same defect shape, different node: `Invariant` is a Leaf and its block's statements live
      // in a field of `InvariantBlock` (itself not even a Container), so nothing generic walked
      // them either. Found while sweeping for every other site with the same gap.
      val src =
        """domain D is {
          |  context C is {
          |    record R is { total: Integer, floor: Integer } with { briefly "r" }
          |    entity E is {
          |      invariant NonNeg is {
          |        let bound: Integer = "5"
          |        total >= floor
          |      } with { briefly "inv" }
          |      state S of record R is { ??? } with { briefly "st" }
          |    } with { briefly "en" }
          |  } with { briefly "c" }
          |} with { briefly "d" }
          |""".stripMargin
      val (decoded, _) = roundTrip(src, "invariant-block-bast")
      val inv = Finder(decoded).recursiveFindByType[Invariant] match
        case Seq(one) => one
        case other    => fail(s"expected exactly one Invariant, got ${other.size}")
      inv.condition match
        case Some(blk: InvariantBlock) =>
          blk.statements.toSeq.size mustBe 1
          blk.statements.toSeq.head mustBe a[LetStatement]
        case other => fail(s"expected an InvariantBlock condition, got $other")
    }
  }

  "a relationship" should {

    "survive a BAST round trip" in {
      // `relationship` reuses NODE_PIPE with the 13 Interaction kinds via a discriminator byte,
      // which the writer never wrote at all -- every relationship's location was misread as its
      // own dispatch byte. Found while checking, as directed, that Relationship's discriminator
      // cannot collide with the Interaction kinds' 0/1/2/10-19.
      val src =
        """domain D is {
          |  context C is {
          |    relationship R to context C as 1:1 with { briefly "self link" }
          |  } with { briefly "c" }
          |} with { briefly "d" }
          |""".stripMargin
      val (decoded, _) = roundTrip(src, "relationship-bast")
      val rel = Finder(decoded).recursiveFindByType[Relationship] match
        case Seq(one) => one
        case other    => fail(s"expected exactly one Relationship, got ${other.size}")
      rel.id.value mustBe "R"
      rel.cardinality mustBe RelationshipCardinality.OneToOne
    }

    "not corrupt the nodes that FOLLOW it" in {
      val src =
        """domain D is {
          |  context C is {
          |    relationship R to context C as 1:1
          |    type T is String with { briefly "after the relationship" }
          |  } with { briefly "c" }
          |} with { briefly "d" }
          |""".stripMargin
      val (decoded, _) = roundTrip(src, "relationship-then-type-bast")
      Finder(decoded).recursiveFindByType[Relationship] must not be empty
      val t = Finder(decoded).recursiveFindByType[Type] match
        case Seq(one) => one
        case other    => fail(s"expected exactly one Type, got ${other.size}")
      t.id.value mustBe "T"
    }
  }

  "the format revision" should {
    "be at least 16, where interaction blocks, invariant block statements, and the relationship " +
      "discriminator were fixed" in {
        FORMAT_REVISION must be >= 16.toShort
      }
  }
}
