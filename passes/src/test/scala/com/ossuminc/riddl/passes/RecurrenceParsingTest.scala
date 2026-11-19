/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Finder
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.utils.{AbstractTestingBasisWithTestData, pc}

import org.scalatest.TestData

/** The two recurrence spellings (Reid, 2026-11-19) parse to the nodes they claim:
  *
  *   - `send <msg> to <outlet> every <period> [starting [at] <instant>]` -> `SendStatement.every`
  *   - `on every <period> is { … }` -> [[OnEveryClause]]
  *
  * and `at`/`every` on one send is a parse error, since the grammar makes them alternatives.
  */
class RecurrenceParsingTest extends AbstractTestingBasisWithTestData {

  private def model(initBody: String, clauses: String = ""): String =
    s"""domain D is {
       |  context C is {
       |    command Tick is { at: TimeStamp }
       |    constant Monthly: Duration = 30 days
       |    constant First: TimeStamp = "2026-12-01T00:00:00Z"
       |    entity E is {
       |      outlet O is command Tick
       |      handler H is {
       |        on init is {
       |$initBody
       |        }
       |$clauses
       |      }
       |    }
       |  }
       |}
       |""".stripMargin

  private def parse(src: String, td: TestData): Either[String, Root] =
    // TopLevelParser, not `Riddl.parse`: this suite is shared, and `Riddl.parse` reaches
    // `java.nio.file` (BAST generation), which Scala.js cannot link.
    TopLevelParser.parseInput(RiddlParserInput(src, td)) match
      case Right(r)   => Right(r)
      case Left(msgs) => Left(msgs.map(_.format).mkString("\n"))

  private def sends(root: Root): Seq[SendStatement] =
    Finder(root).recursiveFindByType[SendStatement]

  private def everyClauses(root: Root): Seq[OnEveryClause] =
    Finder(root).recursiveFindByType[OnEveryClause]

  private val send = "          send command Tick(at = system.now) to outlet E.O"

  "a recurring send" should {
    "carry its period and no start" in { (td: TestData) =>
      val root = parse(model(s"$send every 30 days"), td).fold(fail(_), identity)
      val s = sends(root).head
      s.at mustBe None
      s.every.map(_.period.format) mustBe Some("30 days")
      s.every.flatMap(_.starting) mustBe None
    }
    "carry `starting at` and the bare `starting`" in { (td: TestData) =>
      Seq("starting at First", "starting First").foreach { tail =>
        val root = parse(model(s"$send every Monthly $tail"), td).fold(fail(_), identity)
        val r = sends(root).head.every.getOrElse(fail("no recurrence"))
        r.period.format mustBe "Monthly"
        r.starting.map(_.format) mustBe Some("First")
      }
    }
    "take an expression as the start" in { (td: TestData) =>
      val root = parse(model(s"""$send every "PT1H" starting at system.now + 1 hour"""), td).fold(fail(_), identity)
      sends(root).head.every.flatMap(_.starting).map(_.format) mustBe Some("system.now + 1 hour")
    }
    "render with `starting at`" in { (td: TestData) =>
      val root = parse(model(s"$send every Monthly starting First"), td).fold(fail(_), identity)
      sends(root).head.format must endWith("every Monthly starting at First")
    }
    "not combine with `at`" in { (td: TestData) =>
      parse(model(s"$send at First every 30 days"), td).isLeft mustBe true
      parse(model(s"$send every 30 days at First"), td).isLeft mustBe true
    }
    "leave a plain scheduled send unchanged" in { (td: TestData) =>
      val root = parse(model(s"$send at First"), td).fold(fail(_), identity)
      val s = sends(root).head
      s.at.map(_.format) mustBe Some("First")
      s.every mustBe None
    }
  }

  "an `on every` clause" should {
    "parse each period spelling, with and without `is`" in { (td: TestData) =>
      val clauses =
        """        on every 30 days is { do "a" }
          |        on every Monthly { do "b" }
          |        on every "PT15M" is { do "c" }""".stripMargin
      val root = parse(model("""          do "start"""", clauses), td).fold(fail(_), identity)
      everyClauses(root).map(_.period.format) mustBe Seq("30 days", "Monthly", "\"PT15M\"")
      everyClauses(root).map(_.format) mustBe Seq("on every 30 days", "on every Monthly", "on every \"PT15M\"")
    }
    "leave `every` usable as a field name" in { (td: TestData) =>
      val src = model("""          do "start"""").replace("{ at: TimeStamp }", "{ every: Integer, starting: Integer }")
      parse(src, td).isRight mustBe true
    }
  }
}
