/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.prettify

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Finder
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.passes.validate.AbstractValidatingTest
import com.ossuminc.riddl.passes.{Pass, PassInput, PassesOutput}
import com.ossuminc.riddl.utils.pc

import org.scalatest.*

/** `send <msg> to <portlet> at <instant>` must survive parse -> prettify -> re-parse with the instant
  * intact, in every instant shape the grammar admits: a bare path (ValueRef), `system.now`
  * (SystemValue) and an ascribed typed hole (`prompt("...") as TimeStamp`). The last one is why the
  * emitter must route the instant through `emitValue` rather than `.format`.
  */
class SendAtRoundTripTest extends AbstractValidatingTest {

  private def parse(src: String, origin: String): Root =
    TopLevelParser.parseInput(RiddlParserInput(src, origin)) match
      case Right(root) => root
      case Left(msgs)  => fail(s"parse of $origin failed:\n${msgs.format}")

  private def prettify(root: Root): String =
    val creators = Pass.standardPasses :+ { (in: PassInput, out: PassesOutput) =>
      PrettifyPass(in, out, PrettifyPass.Options(flatten = true, inputDir = ""))
    }
    val result = Pass.runThesePasses(PassInput(root), creators)
    result.outputs
      .outputOf[PrettifyOutput](PrettifyPass.name)
      .getOrElse(fail("PrettifyPass produced no output"))
      .state
      .filesAsString

  private val src =
    """domain d is {
      |  context c is {
      |    command Book is { id: String, startsAt: TimeStamp }
      |    event ReminderDue is { id: String }
      |    constant Opening: TimeStamp = "2026-01-01T09:00:00Z"
      |    outlet Notify is event ReminderDue
      |    handler h is {
      |      on b: command Book {
      |        send event ReminderDue(id = b.id) to outlet Notify at b.startsAt
      |        send event ReminderDue(id = b.id) to outlet Notify at system.now
      |        send event ReminderDue(id = b.id) to outlet Notify at Opening
      |        send event ReminderDue(id = b.id) to outlet Notify at prompt("the due moment") as TimeStamp
      |        send event ReminderDue(id = b.id) to outlet Notify
      |      }
      |    }
      |  }
      |}
      |""".stripMargin

  private def instants(root: Root): Seq[Option[String]] =
    Finder(root).recursiveFindByType[SendStatement].map(_.at.map(_.format))

  "send ... at" should {

    "round-trip every instant shape through prettify, and leave the plain send alone" in {
      (td: TestData) =>
        val root1 = parse(src, "src")
        val expected = Seq(
          Some("b.startsAt"),
          Some("system.now"),
          Some("Opening"),
          Some("prompt(\"the due moment\") as TimeStamp"),
          None
        )
        instants(root1) mustBe expected

        val text = prettify(root1)
        text must include("to outlet Notify at b.startsAt")
        text must include("to outlet Notify at system.now")
        text must include("to outlet Notify at Opening")
        text must include("at prompt(\"the due moment\") as TimeStamp")

        val root2 = parse(text, "prettified")
        instants(root2) mustBe expected
        val kinds = Finder(root2).recursiveFindByType[SendStatement].flatMap(_.at).map(_.getClass.getSimpleName)
        kinds mustBe Seq("ValueRef", "SystemValue", "ValueRef", "PromptValue")
    }

    "converge: prettifying the prettified text is a fixed point" in { (td: TestData) =>
      val once = prettify(parse(src, "src"))
      val twice = prettify(parse(once, "once"))
      twice mustBe once
    }
  }
}
