/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes

import com.ossuminc.riddl.language.AST.{Comment, Root}
import com.ossuminc.riddl.language.toSeq
import com.ossuminc.riddl.language.bast.BASTReader
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.passes.prettify.{PrettifyOutput, PrettifyPass}
import com.ossuminc.riddl.utils.pc
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** A comment introducing a `???` stub must survive EVERY emitting surface.
  *
  * RIDDL is reflective, so a comment that parses but cannot be written back is a defect. The
  * comment is kept as the container's CONTENTS rather than discarded, which is what lets prettify,
  * BAST and JSON carry it with no special-casing — but that has to be proved, not assumed.
  *
  * Note what prettify emits: `domain D is { // c }`, WITHOUT the `???`. That is not a loss. The
  * `???` is not represented in the AST at all — `{ // c ??? }` and `{ // c }` parse to the same
  * tree — so this is normalisation to the canonical spelling, exactly as `String(0,255)` renders as
  * `String`.
  */
class CommentedStubSurfacesTest extends AnyWordSpec with Matchers {

  private val src =
    """domain Stub is {
      |  // Describe the bounded contexts here.
      |  ???
      |}
      |""".stripMargin

  private def parse(text: String, origin: String): Root =
    TopLevelParser.parseInput(RiddlParserInput(text, origin)) match
      case Right(root) => root
      case Left(msgs)  => fail(s"parse of $origin failed:\n${msgs.format}")

  private def commentsOf(root: Root): Seq[String] =
    root.domains.head.contents.toSeq.collect { case c: Comment => c.format }

  "a comment introducing a `???` stub" should {

    "survive PRETTIFY" in {
      val root = parse(src, "src")
      commentsOf(root) must have size 1
      val pretty = Pass
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
      withClue(s"prettify dropped the stub's comment:\n$pretty") {
        pretty must include("Describe the bounded contexts here")
      }
      // The `???` must come back too. It records deliberate intent that a bare comment does not,
      // so dropping it is a LOSS, not a normalisation — `openDef` emits `{ ??? }` only for a wholly
      // empty body, and a comment makes the body non-empty.
      withClue(s"prettify dropped the `???` marker:\n$pretty") {
        pretty must include("???")
      }
      // The comment must survive with its TEXT intact, not merely be present. Asserting the COUNT
      // is what let the missing `???` through the first time: a comment that had swallowed the
      // marker would still count as one comment.
      commentsOf(parse(pretty, "regen")) mustBe commentsOf(root)
    }

    "survive BAST" in {
      val root = parse(src, "src")
      val written = Pass
        .runThesePasses(PassInput(root), Seq(BASTWriterPass.creator()))
        .outputOf[BASTOutput](BASTWriterPass.name)
        .getOrElse(fail("no BAST output"))
      BASTReader.read(written.bytes) match
        case Right(back) =>
          val comments = back.contents.toSeq
            .collect { case d: com.ossuminc.riddl.language.AST.Domain => d }
            .flatMap(_.contents.toSeq.collect { case c: Comment => c.format })
          withClue("BAST dropped the stub's comment: ") {
            comments.exists(_.contains("Describe the bounded contexts here")) mustBe true
          }
        case Left(errors) => fail(s"BAST read failed: ${errors.format}")
      end match
    }
  }
}
