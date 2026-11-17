/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.{Finder, Messages, RuleId}
import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.bast.BASTReader
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.passes.{BASTOutput, BASTWriterPass, Pass, PassInput, PassesOutput, Riddl}
import com.ossuminc.riddl.passes.prettify.{PrettifyOutput, PrettifyPass}
import com.ossuminc.riddl.utils.pc

import org.scalatest.TestData

/** The value that brings an instance into being is spelled `create` (Reid, 2026-11-16).
  *
  * It was `initiate`, which named the creation after its second half: CREATION sets aside the
  * instance and gives it an `Id`, and INITIALIZATION is the `on init` clause creation then runs.
  * `initiate` stays a deprecated spelling forever (the compatibility policy), CONSUMED into the
  * same `Create` node so prettify converges and the `--fix` codemod is honest. `Create` is an
  * alias: the JVM class keeps the name `Initiate` so code compiled against 2.6.0 still links.
  */
class CreateValueTest extends AbstractValidatingTest {

  private def model(keyword: String): String =
    s"""domain D is {
       |  context C is {
       |    command Go is { why: String }
       |    record Fields is { total: String }
       |    entity Order is {
       |      state S of record Fields is {
       |        handler H is {
       |          on init(total: String) {
       |            do "seed the initial total"
       |          }
       |        }
       |      }
       |    }
       |    entity Widget is {
       |      state S of record Fields is {
       |        handler H is {
       |          on init { do "no parameters" }
       |        }
       |      }
       |    }
       |    entity Caller is {
       |      state CS of record Fields is {
       |        handler CH is {
       |          on command Go {
       |            let orderId = $keyword entity Order("5")
       |            let widgetId = $keyword entity Widget
       |          }
       |        }
       |      }
       |    }
       |  }
       |}
       |""".stripMargin

  private def parseWithMessages(src: String, td: TestData): (Root, Messages.Messages) =
    TopLevelParser.parseInputWithMessages(RiddlParserInput(src, td)) match
      case Right((root, msgs)) => (root, msgs)
      case Left(errors)        => fail(errors.format)

  /** Compare what a value MEANS, not where it sits: prettify re-lays the source out. */
  private def shapes(root: Root): Seq[(Seq[String], Int)] =
    Finder(root).recursiveFindByType[Create].map(c => (c.processor.pathId.value, c.args.size))

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

  "The create value" should {
    "parse `create` to a Create node, with no parse message" in { (td: TestData) =>
      val (root, msgs) = parseWithMessages(model("create"), td)
      shapes(root) mustBe Seq((Seq("Order"), 1), (Seq("Widget"), 0))
      msgs mustBe empty
    }

    "format as `create`" in { (td: TestData) =>
      val (root, _) = parseWithMessages(model("create"), td)
      Finder(root).recursiveFindByType[Create].head.format mustBe "create entity Order(\"5\")"
    }

    "validate with no error" in { (td: TestData) =>
      Riddl.parseAndValidate(RiddlParserInput(model("create"), td), shouldFailOnError = false) match
        case Left(errors) => fail(errors.format)
        case Right(result) =>
          withClue(result.messages.justErrors.format) { result.messages.justErrors mustBe empty }
    }

    "accept `initiate` as a deprecated spelling, consumed into the same Create" in {
      (td: TestData) =>
        val (root, msgs) = parseWithMessages(model("initiate"), td)
        shapes(root) mustBe Seq((Seq("Order"), 1), (Seq("Widget"), 0))
        val deprecations = msgs.filter(_.isDeprecation)
        deprecations.size mustBe 2 // one per `initiate`
        deprecations.foreach { d =>
          d.ruleId mustBe Some(RuleId.InitiateKeyword)
          d.autoFixable mustBe true
          d.message must include("create")
        }
    }

    "prettify `initiate` to `create`, converging" in { (td: TestData) =>
      val (root1, _) = parseWithMessages(model("initiate"), td)
      val pretty = prettify(root1)
      pretty must include("create entity Order(")
      pretty must not include "initiate"
      val (root2, msgs2) = parseWithMessages(pretty, td)
      shapes(root2) mustBe shapes(root1)
      msgs2.filter(_.isDeprecation) mustBe empty
    }

    "survive a BAST round trip (no format change: the keyword is not on the wire)" in {
      (td: TestData) =>
        val (root, _) = parseWithMessages(model("create"), td)
        val bytes = Pass
          .runThesePasses(PassInput(root), Seq(BASTWriterPass.creator()))
          .outputOf[BASTOutput](BASTWriterPass.name)
          .get
          .bytes
        BASTReader.read(bytes) match
          case Right(m) =>
            Finder(m).recursiveFindByType[Create].map(c => (c.processor.pathId.value, c.args.size)) mustBe
              shapes(root)
          case Left(errors) => fail(errors.format)
    }

    "keep `Initiate` and `Create` the SAME node, so 2.6.0 binaries still link" in {
      (td: TestData) =>
        // The JVM class stays `Initiate` (MiMa against 2.6.0 rejected removing it); `Create` is the
        // name to write. Consumer code that constructs or matches either name sees one node.
        def viaOldName(v: RiddlValue): Option[Int] = v match
          case Initiate(_, _, args) => Some(args.size)
          case _                    => None
        val (root, _) = parseWithMessages(model("create"), td)
        val created = Finder(root).recursiveFindByType[Create]
        created.flatMap(viaOldName) mustBe Seq(1, 0)
        val built: Create = Initiate(created.head.loc, created.head.processor, created.head.args)
        built mustBe created.head
        created.head.kind mustBe "Create"
    }
  }
}
