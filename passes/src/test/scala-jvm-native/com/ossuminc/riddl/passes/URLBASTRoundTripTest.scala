/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{toSeq, Finder}
import com.ossuminc.riddl.language.bast.{BASTReader, FORMAT_REVISION}
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.passes.validate.AbstractValidatingTest
import com.ossuminc.riddl.passes.{BASTOutput, BASTWriterPass, Pass, PassInput}
import com.ossuminc.riddl.utils.{ec, pc, Await, URL}

import org.scalatest.TestData

import scala.concurrent.duration.DurationInt

/** `BASTWriter.writeURL` wrote only `basis`/`path` and `BASTReader.readURL` rebuilt the other two
  * fields (`scheme`, `authority`) as hardcoded `file`/`""`, so EVERY `URL` through BAST -- not just
  * a `ShownBy` -- lost its scheme and host. `shown by { https://ossum.tech/... }` came back as
  * `shown by { file:///... }`; only `described at <url>` escaped notice, and only because it stores
  * the whole authored string as a plain `path` field on `URLDescription`, never going through
  * `writeURL`/`readURL` at all -- it was never at risk, not "lucky" in the sense of sharing the
  * code path and dodging the bug.
  *
  * Reported by riddl-models 2026-08-14
  * (`task/2026-08-14-shown-by-loses-its-url-scheme-and-host-through-bast.md`), reduced from their
  * `language-coverage/ViewerApp.riddl` repro.
  */
class URLBASTRoundTripTest extends AbstractValidatingTest {

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

  // `shown by` sits inside an application context's page; `described at` sits on the same
  // context's metadata; a SIBLING context follows both so a misaligned field count derails on
  // something the test can name, rather than on whatever the string table happened to hold next.
  private val src =
    """domain Dom is {
      |  application context ui is {
      |    type Amount is Integer
      |    result Total is { sum: Integer }
      |    page picker is {
      |      input amount enters type Dom.ui.Amount
      |      shown by { https://ossum.tech/mockups/survey-map }
      |      output total presents result Dom.ui.Total
      |    }
      |  } with { described at https://ossum.tech/docs/riddl/dom-ui }
      |  context After is {
      |    type Marker is Integer
      |  }
      |}
      |""".stripMargin

  "a `shown by` URL" should {

    "keep its scheme, authority AND path through a BAST round trip" in { (td: TestData) =>
      val root = roundTrip(src, "shownBy-scheme-authority-path")
      val shown = Finder(root)
        .recursiveFindByType[ShownBy]
        .headOption
        .getOrElse(fail("the ShownBy did not survive the BAST round trip"))
      val url = shown.urls.headOption.getOrElse(fail("the ShownBy carried no URL"))
      // Before the fix this was ("file", "", ..., "mockups/survey-map") -- scheme and authority
      // silently replaced by the reader's hardcoded defaults.
      url.scheme mustBe "https"
      url.authority mustBe "ossum.tech"
      url.path mustBe "mockups/survey-map"
    }
  }

  "a `described at` URL" should {

    "keep its full authored text through a BAST round trip (the case that worked by luck)" in {
      (td: TestData) =>
        val root = roundTrip(src, "describedAt-luck")
        val context = Finder(root)
          .recursiveFindByType[Context]
          .find(_.id.value == "ui")
          .getOrElse(fail("the 'ui' context did not survive"))
        val described = context.metadata.toSeq
          .find(_.isInstanceOf[URLDescription])
          .map(_.asInstanceOf[URLDescription])
          .getOrElse(fail("the URLDescription did not survive"))
        // URLDescription stores the whole authored string as `path` and never goes through
        // writeURL/readURL, which is WHY this one was never at risk from the bug -- pinned here so
        // a future change that routes it through the same codec as ShownBy cannot silently regress
        // it without this test noticing.
        described.path mustBe "https://ossum.tech/docs/riddl/dom-ui"
    }
  }

  "a definition AFTER the URL-bearing constructs" should {

    "still decode intact" in { (td: TestData) =>
      // The actual failure mode a wrong field count produces: misalignment surfaces at whatever
      // comes NEXT, not at the URL itself. Before the fix this passed too (the old reader consumed
      // exactly as many bytes as the old writer wrote, just mis-assigning them) -- so this guards
      // the field COUNT staying in sync between writer and reader, not merely "something decoded".
      val root = roundTrip(src, "after-url-definitions")
      val after = Finder(root)
        .recursiveFindByType[Context]
        .find(_.id.value == "After")
        .getOrElse(fail("the 'After' context did not survive"))
      val marker = Finder(after).recursiveFindByType[Type].find(_.id.value == "Marker")
      marker must not be empty
    }
  }

  "a relative/file URL (the case that already worked)" should {

    "still round-trip through BAST after the fix" in { (td: TestData) =>
      // Include.origin is a plain file-scheme URL with no scheme/authority in the source text --
      // exactly the shape the old reader's hardcoded (`file`, "") reconstruction happened to match.
      // This pins that it is unaffected now that both fields are written and read explicitly rather
      // than assumed.
      val url = URL.fromCwdPath("language/input/includes/domainIncludes.riddl")
      val future = RiddlParserInput.fromURL(url).map { rpi =>
        TopLevelParser.parseInput(rpi) match
          case Left(errors) => fail(s"parse failed:\n${errors.format}")
          case Right(root) =>
            val bytes = Pass
              .runThesePasses(PassInput(root), Seq(BASTWriterPass.creator()))
              .outputOf[BASTOutput](BASTWriterPass.name)
              .getOrElse(fail("BASTWriterPass produced no output"))
              .bytes
            BASTReader(bytes).read() match
              case Left(msgs) => fail(s"BAST round trip failed:\n${msgs.format}")
              case Right(decoded) =>
                val include = Finder(decoded)
                  .recursiveFindByType[Include[?]]
                  .headOption
                  .getOrElse(fail("the Include did not survive the BAST round trip"))
                include.origin.scheme mustBe URL.fileScheme
                include.origin.authority mustBe ""
                // The included file's own content must have decoded too -- proof the reader did not
                // merely recover by luck after misreading the origin.
                val includedTypeNames =
                  Finder(decoded).recursiveFindByType[Type].map(_.id.value)
                includedTypeNames must contain("foo")
        end match
      }
      Await.result(future, 10.seconds)
    }
  }

  "the format revision" should {

    // Was "stay at 18": that assertion was correct only while 18 was UNRELEASED and this fix
    // could ride it. 18 shipped in 2.0.0-rc.15, so the next wire-format change had to bump, and
    // the `forward` statement (sub-kind 21) is it. The assertion is kept rather than deleted --
    // it is what makes an accidental bump visible -- but it now tracks the shipped revision.
    "be 22 -- `system` bumped it; 21 has shipped and cannot be ridden again" in {
      (td: TestData) =>
        FORMAT_REVISION mustBe 22.toShort
    }
  }
}
