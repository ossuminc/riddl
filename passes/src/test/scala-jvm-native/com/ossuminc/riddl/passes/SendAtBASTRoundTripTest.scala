/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Finder
import com.ossuminc.riddl.language.bast.BASTReader
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.passes.validate.AbstractValidatingTest
import com.ossuminc.riddl.utils.pc

import org.scalatest.TestData

/** `send ... at <instant>` must survive a BAST round trip. The instant rides sub-kind 5's payload as
  * `writeOption(at)(writeValue)` AFTER the portlet ref (`FORMAT_REVISION` 24), so a reader that does
  * not know about it misreads the option byte as the next node's tag; the second case counts the
  * statements that FOLLOW to catch exactly that derailment.
  */
class SendAtBASTRoundTripTest extends AbstractValidatingTest {

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

  private val src =
    """domain D is {
      |  context C is {
      |    command Book is { id: String, startsAt: TimeStamp } with { briefly "c" }
      |    event ReminderDue is { id: String } with { briefly "e" }
      |    outlet Notify is event ReminderDue with { briefly "o" }
      |    handler H is {
      |      on b: command Book is {
      |        send event ReminderDue(id = b.id) to outlet Notify at b.startsAt
      |        do "one"
      |        send event ReminderDue(id = b.id) to outlet Notify at system.now
      |        do "two"
      |        send event ReminderDue(id = b.id) to outlet Notify
      |        do "three"
      |      }
      |    } with { briefly "h" }
      |  } with { briefly "c" }
      |} with { briefly "d" }
      |""".stripMargin

  "send ... at" should {

    "survive a BAST round trip with the instant intact, and absent where absent" in { (td: TestData) =>
      val root = roundTrip(src, "send-at-bast")
      val sends = Finder(root).recursiveFindByType[SendStatement]
      sends.size mustBe 3
      sends.map(_.at.map(_.format)) mustBe Seq(Some("b.startsAt"), Some("system.now"), None)
      sends.flatMap(_.at).map(_.getClass.getSimpleName) mustBe Seq("ValueRef", "SystemValue")
    }

    "not corrupt the statements that follow a scheduled send" in { (td: TestData) =>
      val root = roundTrip(src, "send-at-bast-followers")
      Finder(root).recursiveFindByType[DoStatement].map(_.text) mustBe Seq("one", "two", "three")
    }
  }
}
