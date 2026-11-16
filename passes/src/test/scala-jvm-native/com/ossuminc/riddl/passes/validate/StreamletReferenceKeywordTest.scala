/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.{Finder, Messages, RuleId}
import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.language.bast.BASTReader
import com.ossuminc.riddl.passes.{BASTOutput, BASTWriterPass, Pass, PassInput, PassesOutput, Riddl}
import com.ossuminc.riddl.passes.prettify.{PrettifyOutput, PrettifyPass}
import com.ossuminc.riddl.utils.pc

import org.scalatest.TestData

/** BACKLOG [5.2], and synapify's 2026-11-15 task: a streamlet could be referenced only as `source`,
  * `sink`, `merge`, `split` or `void`. A `flow` or `router` streamlet, or any streamlet referred to
  * by the canonical declaration keyword `streamlet`, had no reference spelling at all.
  *
  * `processor` is accepted too, as the deprecated synonym of `streamlet` it already is for
  * declarations: it draws the same `stream-processor-keyword` deprecation and is CONSUMED into
  * `streamlet`, so prettify converges.
  *
  * `flow` is also a GROUP alias. In the one position that admits both a processor and a group
  * reference -- an epic step's `send ... from <ref>` -- `flow X` stays a group reference, as it
  * parsed before this change.
  */
class StreamletReferenceKeywordTest extends AbstractValidatingTest {

  private def model(refKeyword: String, target: String): String =
    s"""domain D is {
       |  context C is {
       |    command Cmd is { x: Integer }
       |    streamlet F as flow is {
       |      inlet i is command Cmd
       |      outlet o is command Cmd
       |    }
       |    streamlet R as router is {
       |      inlet i1 is command Cmd
       |      inlet i2 is command Cmd
       |      outlet o1 is command Cmd
       |      outlet o2 is command Cmd
       |    }
       |    relationship Rel to $refKeyword C.$target as 1:N
       |  }
       |}
       |""".stripMargin

  private def parseWithMessages(src: String, td: TestData): (Root, Messages.Messages) =
    TopLevelParser.parseInputWithMessages(RiddlParserInput(src, td)) match
      case Right((root, msgs)) => (root, msgs)
      case Left(errors)        => fail(errors.format)

  private def relationshipRef(root: Root): ProcessorRef[?] =
    Finder(root).recursiveFindByType[Relationship].head.withProcessor

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

  "A streamlet reference" should {
    for (kw, target) <- Seq("flow" -> "F", "router" -> "R", "streamlet" -> "F") do
      s"parse `$kw` as a StreamletRef keeping its keyword, with no message" in { (td: TestData) =>
        val (root, msgs) = parseWithMessages(model(kw, target), td)
        relationshipRef(root) match
          case sr: StreamletRef =>
            sr.keyword mustBe kw
            sr.pathId.value mustBe Seq("C", target)
          case other => fail(s"expected a StreamletRef, got $other")
        msgs mustBe empty
      }

      s"resolve `relationship ... to $kw C.$target` with no error" in { (td: TestData) =>
        Riddl.parseAndValidate(RiddlParserInput(model(kw, target), td), shouldFailOnError = false) match
          case Left(errors) => fail(errors.format)
          case Right(result) =>
            withClue(result.messages.justErrors.format) {
              result.messages.justErrors mustBe empty
            }
      }
    end for

    "accept `processor` as a deprecated spelling, consumed into `streamlet`" in { (td: TestData) =>
      val (root, msgs) = parseWithMessages(model("processor", "F"), td)
      relationshipRef(root) match
        case sr: StreamletRef => sr.keyword mustBe "streamlet"
        case other            => fail(s"expected a StreamletRef, got $other")
      val deprecations = msgs.filter(_.isDeprecation)
      deprecations.size mustBe 1
      deprecations.head.ruleId mustBe Some(RuleId.ProcessorKeyword)
      deprecations.head.autoFixable mustBe true
    }

    "round-trip each spelling through prettify, `processor` converging to `streamlet`" in {
      (td: TestData) =>
        for (kw, target, expected) <- Seq(
            ("flow", "F", "flow"),
            ("router", "R", "router"),
            ("streamlet", "F", "streamlet"),
            ("processor", "F", "streamlet")
          )
        do
          val (root1, _) = parseWithMessages(model(kw, target), td)
          val pretty = prettify(root1)
          pretty must include(s"to $expected C.$target")
          val (root2, msgs2) = parseWithMessages(pretty, td)
          // Compare the reference, not its location: prettify re-lays the source out.
          def shape(r: ProcessorRef[?]) = r match
            case sr: StreamletRef => (sr.keyword, sr.pathId.value)
            case other            => fail(s"expected a StreamletRef, got $other")
          shape(relationshipRef(root2)) mustBe shape(relationshipRef(root1))
          msgs2.filter(_.isDeprecation) mustBe empty
        end for
    }

    "survive a BAST round trip with each new keyword" in { (td: TestData) =>
      // The keyword is a free string on the wire (writeStreamletRef), so no format change -- proved.
      for (kw, target) <- Seq("flow" -> "F", "router" -> "R", "streamlet" -> "F") do
        val (root, _) = parseWithMessages(model(kw, target), td)
        val bytes = Pass
          .runThesePasses(PassInput(root), Seq(BASTWriterPass.creator()))
          .outputOf[BASTOutput](BASTWriterPass.name)
          .get
          .bytes
        BASTReader.read(bytes) match
          case Right(m) =>
            Finder(m).recursiveFindByType[Relationship].head.withProcessor match
              case sr: StreamletRef =>
                (sr.keyword, sr.pathId.value) mustBe (kw, Seq("C", target))
              case other => fail(s"expected a StreamletRef, got $other")
          case Left(msgs) => fail(msgs.format)
      end for
    }

    "still read `send ... from flow G` in an epic step as a GROUP reference" in { (td: TestData) =>
      val src =
        """domain D is {
          |  user U is "a user"
          |  command Cmd is { ??? }
          |  application context App is {
          |    group G is { ??? }
          |  }
          |  context Gateway is { ??? }
          |  epic E is {
          |    user U wants to "act" so that "it happens"
          |    case one is {
          |      user U wants to "act" so that "it happens"
          |      step send command Cmd from flow App.G to context Gateway
          |    }
          |  }
          |}
          |""".stripMargin
      val (root, _) = parseWithMessages(src, td)
      Finder(root).recursiveFindByType[SendMessageInteraction].head.from match
        case gr: GroupRef => gr.keyword mustBe "flow"
        case other        => fail(s"expected the group reference to survive, got $other")
    }
  }
}
