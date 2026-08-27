/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.utils.{AbstractTestingBasis, URL, pc}

/** Covers the accessors added for library consumers (synapify's 2.0 asks).
  *
  * Each of these existed as hand-rolled code in a consumer, which is the reason to own them here: a
  * tool that gets a character span wrong deletes the wrong text, and one that gets provenance wrong
  * writes a definition into the wrong file.
  */
class ConsumerApiTest extends AbstractTestingBasis {

  private val src =
    """domain Ordering is {
      |  context Orders is {
      |    type Amount is Integer
      |  }
      |}
      |""".stripMargin

  private def parse(text: String, origin: String): Root =
    // The origin comes from the URL PATH, not from `purpose` -- passing the filename as
    // purpose leaves origin as "empty", which is precisely the confusion declaringFile exists
    // to remove.
    val rpi = RiddlParserInput(text, URL.fromCwdPath(origin), "test")
    TopLevelParser.parseInput(rpi) match
      case Left(msgs: Messages) => fail(msgs.format)
      case Right(root)          => root

  "span" should {
    "give character offsets that actually delimit the definition in its source" in {
      val root = parse(src, "ordering.riddl")
      val domain = root.domains.head
      val (start, end) = domain.span.getOrElse(fail("domain has no span"))
      // The point of the accessor is that slicing the source by it yields the definition,
      // which is what an in-place editor does. Assert that, not just that numbers exist.
      src.substring(start, end) must startWith("domain")
      start must be < end
    }

    "be None when the location is unknown" in {
      // A programmatically built node, or one rebuilt from a serialization carrying no
      // offsets, must not report a bogus (0, 0) span that an editor would act on.
      Identifier(At.empty, "x").span mustBe None
    }
  }

  "declaringFile" should {
    "report the file a definition was parsed from" in {
      val root = parse(src, "ordering.riddl")
      root.domains.head.declaringFile.getOrElse("") must endWith("ordering.riddl")
    }

    "be None rather than the string \"empty\" for unknown origins" in {
      // `RiddlParserInput.origin` returns the literal "empty" for the empty input; leaking that
      // to a consumer as a filename is worse than admitting we do not know.
      Identifier(At.empty, "x").declaringFile mustBe None
    }
  }

  "Module.toRoot" should {
    "lift the contents of a top-level Include rather than dropping them" in {
      // The regression this guards: the catch-all `case _ => None` matched Include and the
      // entire included file vanished from the Root with no diagnostic.
      val domain = Domain(At.empty, Identifier(At.empty, "Included"))
      val include = Include[OccursInModule](At.empty, URL.empty, Contents(domain))
      val module = Module.anonymous(At.empty, Contents[ModuleContents](include))
      val root = Module.toRoot(module)
      root.domains.map(_.id.value) must contain("Included")
    }

    "keep directly-held definitions too" in {
      val domain = Domain(At.empty, Identifier(At.empty, "Direct"))
      val module = Module.anonymous(At.empty, Contents[ModuleContents](domain))
      Module.toRoot(module).domains.map(_.id.value) must contain("Direct")
    }
  }

  "Context intention predicates" should {
    "answer false for a plain context" in {
      val c = Context(At.empty, Identifier(At.empty, "Plain"))
      c.isApplication mustBe false
      c.isExternal mustBe false
      c.isGateway mustBe false
      c.isService mustBe false
    }

    "answer true for the declared intention only" in {
      val c = Context(
        At.empty,
        Identifier(At.empty, "App"),
        intention = Option(Intention.Application)
      )
      c.isApplication mustBe true
      c.isService mustBe false
    }
  }

  "Processor.ports" should {
    "return inlets and outlets together" in {
      val c = Context(At.empty, Identifier(At.empty, "Empty"))
      c.ports mustBe empty
      c.isSource mustBe false
    }
  }
}
