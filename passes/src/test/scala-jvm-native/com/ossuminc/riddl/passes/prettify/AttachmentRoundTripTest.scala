/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.prettify

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{filter, toSeq, Finder}
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.passes.validate.AbstractValidatingTest
import com.ossuminc.riddl.passes.{Pass, PassInput, PassesOutput}
import com.ossuminc.riddl.utils.pc

import org.scalatest.*

/** All three `attachment` forms were emitted with the mime type QUOTED, and with no trailing
  * newline.
  *
  * `namedAttachmentBody` (`CommonParser:372`) parses the mime type as a bare token, so
  * `attachment N is "text/markdown" as "x"` is a hard parse error (`Expected ("ULID")` — the ULID
  * branch is tried first and the quote makes the named branch fail too). Prettify was emitting
  * source riddlc rejects.
  *
  * The missing newline is the second half: the attachment landed on the same line as the closing
  * `}` of the metadata block. That alone does not break the parse, but it is emitted by the same
  * three one-line methods and is fixed with them.
  *
  * The ULID form's argument IS quoted — `ulidAttachmentBody` takes a `literalString` — so it keeps
  * its quotes while the other two lose theirs. That asymmetry is exactly the sort of thing a
  * uniform "fix" would break, so all three forms are asserted here.
  *
  * Reported by riddl-models (`task/2026-08-14-prettify-emitter-drops-method-and-shown-by.md`).
  */
class AttachmentRoundTripTest extends AbstractValidatingTest {

  private def parse(src: String, origin: String): Root =
    TopLevelParser.parseInput(RiddlParserInput(src, origin)) match
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
      .getOrElse(fail("PrettifyPass produced no output"))
      .state
      .filesAsString

  private val src =
    """domain Dom is {
      |  type Amount is Integer
      |} with {
      |  briefly "D"
      |  attachment Notes is text/markdown as "hello"
      |  attachment Spec is application/json in file "spec.json"
      |  attachment ULID is "01ARZ3NDEKTSV4RRFFQ69G5FAV"
      |}
      |""".stripMargin

  private def attachmentsOf(root: Root): Seq[Attachment] =
    Finder(root)
      .recursiveFindByType[Domain]
      .headOption
      .getOrElse(fail("no domain was parsed"))
      .metadata
      .filter[Attachment]
      .toSeq

  "the `attachment` metadata forms" should {

    "emit an unquoted mime type so the output re-parses" in { (_: TestData) =>
      val pretty = prettify(parse(src, "attachments"))
      withClue(s"prettified output was:\n$pretty") {
        pretty must include("attachment Notes is text/markdown as \"hello\"")
        pretty must include("attachment Spec is application/json in file \"spec.json\"")

        val again = attachmentsOf(parse(pretty, "regen"))
        again.size mustBe 3
      }
    }

    "keep the ULID form's quotes, which its parser requires" in { (_: TestData) =>
      val pretty = prettify(parse(src, "attachments"))
      withClue(s"prettified output was:\n$pretty") {
        pretty must include("attachment ULID is \"01ARZ3NDEKTSV4RRFFQ69G5FAV\"")

        val again = attachmentsOf(parse(pretty, "regen"))
        again.collect { case u: ULIDAttachment => u }.size mustBe 1
      }
    }

    "not run the last attachment onto the closing brace" in { (_: TestData) =>
      val pretty = prettify(parse(src, "attachments"))
      withClue(s"prettified output was:\n$pretty") {
        pretty.linesIterator.exists(l => l.contains("attachment") && l.trim.endsWith("}")) mustBe
          false
      }
    }
  }
}
