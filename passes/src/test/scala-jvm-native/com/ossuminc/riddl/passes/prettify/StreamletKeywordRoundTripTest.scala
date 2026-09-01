/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.prettify

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{Finder, toSeq}
import com.ossuminc.riddl.language.{Fix, RuleId}
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.passes.validate.AbstractValidatingTest
import com.ossuminc.riddl.passes.{Pass, PassInput, PassesOutput}
import com.ossuminc.riddl.utils.pc

import org.scalatest.*

/** `streamlet` is the canonical spelling of the generic streaming processor; `processor` is the
  * deprecated original ([5.1]).
  *
  * Every other kind of processor already names a THING -- entity, repository, projector, adaptor --
  * so the generic keyword named the abstraction instead, and did not match the AST node it has
  * always produced. The old spelling is CONSUMED rather than tolerated: both build the identical
  * `Streamlet`, so prettify converges and `autoFixable` is honest.
  *
  * Under the 3.0 never-delete rule `processor` goes on parsing indefinitely. A test that only
  * pinned the new spelling would not notice the old one being dropped, which is the failure this
  * suite exists to prevent.
  */
class StreamletKeywordRoundTripTest extends AbstractValidatingTest {

  /** Deprecations travel on a DIFFERENT channel from validation messages -- `parseInput` and
    * `parseAndValidate` both discard them, which is why this helper exists rather than a
    * `.parseInputWithMessages(...)._2`.
    */
  private def parseMessages(src: String, origin: String) =
    TopLevelParser.parseInputWithMessages(RiddlParserInput(src, origin)) match
      case Right((_, msgs)) => msgs
      case Left(msgs)       => fail(s"parse of $origin failed:\n${msgs.format}")

  private def parse(src: String, origin: String): Root =
    TopLevelParser.parseInput(RiddlParserInput(src, origin)) match
      case Right(root) => root
      case Left(msgs)  => fail(s"parse of $origin failed:\n${msgs.format}")

  private def prettify(root: Root): String =
    val creators = Pass.standardPasses :+ { (in: PassInput, out: PassesOutput) =>
      PrettifyPass(in, out, PrettifyPass.Options(flatten = true, inputDir = ""))
    }
    Pass
      .runThesePasses(PassInput(root), creators)
      .outputs
      .outputOf[PrettifyOutput](PrettifyPass.name)
      .getOrElse(fail("PrettifyPass produced no output"))
      .state
      .filesAsString

  private def streamletOf(root: Root, name: String): Streamlet =
    Finder(root)
      .recursiveFindByType[Streamlet]
      .find(_.id.value == name)
      .getOrElse(fail(s"no Streamlet named '$name' in the parsed tree"))

  private def model(keyword: String, shape: String = ""): String =
    s"""domain D is {
       |  type Pkg is { id: String } with { briefly "p" }
       |  context C is {
       |    $keyword S$shape is {
       |      inlet feed is type D.Pkg with { briefly "i" }
       |      outlet done is type D.Pkg with { briefly "o" }
       |    } with { briefly "s" }
       |  } with { briefly "c" }
       |} with { briefly "d" }
       |""".stripMargin

  "the `streamlet` keyword" should {

    "parse to a Streamlet, exactly as `processor` does" in { (td: TestData) =>
      val fresh = streamletOf(parse(model("streamlet"), "streamlet"), "S")
      val legacy = streamletOf(parse(model("processor"), "processor"), "S")
      // Definition.equals is structural and skips Contents, so comparing the whole node would
      // compare the two `At`s. The question here is whether the SHAPE decision agrees.
      fresh.ascribedShape mustBe legacy.ascribedShape
      fresh.effectiveShape.getClass mustBe legacy.effectiveShape.getClass
      fresh.inlets.toSeq.map(_.id.value) mustBe legacy.inlets.toSeq.map(_.id.value)
      fresh.outlets.toSeq.map(_.id.value) mustBe legacy.outlets.toSeq.map(_.id.value)
    }

    "accept an ascribed shape" in { (td: TestData) =>
      val s = streamletOf(parse(model("streamlet", " as flow"), "shaped"), "S")
      s.ascribedShape.map(_.getClass.getSimpleName) mustBe Some("Flow")
    }

    "draw NO deprecation of its own" in { (td: TestData) =>
      val msgs = parseMessages(model("streamlet"), "streamlet")
      msgs.filter(_.ruleId.contains(RuleId.ProcessorKeyword)) mustBe empty
    }
  }

  "the deprecated `processor` keyword" should {

    "still parse -- from 3.0 on a deprecated spelling is never deleted" in { (td: TestData) =>
      streamletOf(parse(model("processor"), "processor"), "S").id.value mustBe "S"
    }

    "emit a Deprecation naming `streamlet`, marked auto-fixable" in { (td: TestData) =>
      val msgs = parseMessages(model("processor"), "processor")
      val dep = msgs
        .find(_.ruleId.contains(RuleId.ProcessorKeyword))
        .getOrElse(fail(s"no processor-keyword deprecation in:\n${msgs.format}"))
      dep.message must include("streamlet")
      dep.autoFixable mustBe true
      // The reported span must cover exactly the keyword, or `validate --fix` -- which is a pure
      // span replacement for this rule -- would eat the identifier after it.
      val src = model("processor")
      src.substring(dep.loc.offset, dep.loc.endOffset) mustBe "processor"
    }

    "carry a CONSTANT mechanical fix, so `--fix` can apply it" in { (td: TestData) =>
      RuleId.ProcessorKeyword.mechanicalFix mustBe Some(Fix.Constant("streamlet"))
      RuleId.ProcessorKeyword.deprecates mustBe true
    }
  }

  "prettify" should {

    "emit `streamlet`, converging the deprecated spelling onto the canonical one" in {
      (td: TestData) =>
        val pretty = prettify(parse(model("processor"), "processor"))
        pretty must include("streamlet S is")
        pretty mustNot include("processor S")
    }

    "leave the canonical spelling alone" in { (td: TestData) =>
      prettify(parse(model("streamlet"), "streamlet")) must include("streamlet S is")
    }

    "produce output that re-parses to the same streamlet, from EITHER spelling" in {
      (td: TestData) =>
        for spelling <- Seq("streamlet", "processor") do
          val once = parse(model(spelling), spelling)
          val again = parse(prettify(once), s"$spelling-regen")
          // By CLASS, not by value: a StreamletShape carries its `loc`, and prettified source is
          // not the same length as what was written, so the shapes are equal in every way that
          // means anything and unequal as values.
          streamletOf(again, "S").effectiveShape.getClass mustBe
            streamletOf(once, "S").effectiveShape.getClass
          streamletOf(again, "S").inlets.toSeq.map(_.id.value) mustBe
            streamletOf(once, "S").inlets.toSeq.map(_.id.value)
        end for
    }

    "emit no deprecation on the SECOND pass -- the old spelling is consumed, not carried" in {
      (td: TestData) =>
        val regen = prettify(parse(model("processor"), "processor"))
        val msgs = parseMessages(regen, "regen")
        msgs.filter(_.ruleId.contains(RuleId.ProcessorKeyword)) mustBe empty
    }
  }

  "`AST.Streamlet.format`" should {
    // This and RiddlFileEmitter.openDef are the same decision written twice -- the dual-dispatch
    // shape that has bitten this repo repeatedly, most recently with
    // EntityReferenceTypeExpression.format, where a golden test pinned the WRONG copy.
    "agree with the prettifier about which keyword is canonical" in { (td: TestData) =>
      val s = streamletOf(parse(model("processor"), "processor"), "S")
      s.format must startWith("streamlet ")
    }
  }
}
