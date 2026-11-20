/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{At, Finder}
import com.ossuminc.riddl.language.bast.BASTReader
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.passes.prettify.{PrettifyOutput, PrettifyPass}
import com.ossuminc.riddl.utils.pc
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** BACKLOG [1.32]: calendar schedules (`every month on day 1 at 09:00 in "UTC"`) and the cron
  * escape hatch (`every cron "0 9 1 * *"`) in both recurrence positions -- `on every` and `send …
  * every`. Compares TREES, not text (the B5 lesson), with every location erased.
  */
class CalendarScheduleRoundTripTest extends AnyWordSpec with Matchers {

  /** One `on every <period>` clause per entry; each is round-tripped and its tree compared. */
  private val periods: Seq[String] = Seq(
    "month on day 1 at 09:00 in \"America/Toronto\"",
    "month on last day",
    "month on the third wednesday at 10:00",
    "month on the last friday",
    "3 months on day 1",
    "year on march 31 at 17:00",
    "year on the first monday of september",
    "2 years on february 28",
    "day at 06:00",
    "weekday at 08:30:15",
    "monday, wednesday and friday at 07:00",
    "saturday and sunday",
    "sunday",
    "cron \"0 9 1 * *\" in \"UTC\"",
    "cron \"*/5 * * * *\"",
    "30 days", // control: a Duration is still a value
    "C.Monthly" // control: a path is still a value
  )

  /** `send … every <schedule> [starting at <instant>]`, in `on init`. */
  private val sendPeriods: Seq[String] = Seq(
    "month on last day in \"UTC\" starting at system.now",
    "cron \"0 0 * * 0\""
  )

  private def src(ps: Seq[String], sps: Seq[String]): String =
    s"""domain D is {
       |  context C is {
       |    command Tick is { n: Integer } with { briefly "t" }
       |    constant Monthly: Duration = 30 days
       |    entity E is {
       |      outlet O is command C.Tick
       |      handler H is {
       |        on init is {
       |${sps.map(p => s"          send command C.Tick(n = 1) to outlet E.O every $p").mkString("\n")}
       |        }
       |${ps.map(p => s"        on every $p is { do \"x\" }").mkString("\n")}
       |      } with { briefly "h" }
       |    } with { briefly "e" }
       |  } with { briefly "c" }
       |} with { briefly "d" }
       |""".stripMargin

  private val source: String = src(periods, sendPeriods)

  private[passes] def parse(text: String, origin: String): Root =
    TopLevelParser.parseInput(RiddlParserInput(text, origin)) match
      case Right(root) => root
      case Left(msgs)  => fail(s"parse of $origin failed:\n${msgs.format}\n$text")

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
      .getOrElse(fail("no prettify output"))
      .state
      .filesAsString

  /** A node's structure with every location erased. */
  private[passes] def sig(x: Any): String = x match
    case _: At           => ""
    case s: String       => s"\"$s\""
    case it: Iterable[?] => it.map(sig).mkString("[", ",", "]")
    case o: Option[?]    => o.map(sig).map(s => s"Some($s)").getOrElse("None")
    case p: Product      =>
      p.productPrefix + p.productIterator.map(sig).filter(_.nonEmpty).mkString("(", ",", ")")
    case other => other.toString

  /** The `on every` periods, then the `send … every` recurrences, in source order. */
  private[passes] def values(root: Container[?]): Seq[RiddlValue] =
    val f = Finder(root.contents)
    f.recursiveFindByType[OnEveryClause].map(_.period) ++
      f.recursiveFindByType[SendStatement].flatMap(_.every)

  private[passes] def trees(root: Container[?]): Seq[String] = values(root).map(sig)

  "calendar schedules" should {

    "parse to schedule nodes, and leave Durations and paths as values" in {
      val vs = values(parse(source, "src"))
      vs.size mustBe periods.size + sendPeriods.size
      vs.take(13).foreach(_ mustBe a[CalendarSchedule])
      vs.slice(13, 15).foreach(_ mustBe a[CronSchedule])
      vs(15) mustBe a[DurationLiteral]
      vs(16) mustBe a[ValueRef]
    }

    "format as written (control)" in {
      val vs = values(parse(source, "src"))
      vs.take(periods.size).map(_.format) mustBe periods
      vs.drop(periods.size).map(_.format) mustBe sendPeriods.map(p => s"every $p")
    }

    "PRETTIFY and re-parse to the same trees" in {
      val original = parse(source, "src")
      val again = parse(prettify(original), "regen")
      trees(again) mustBe trees(original)
    }

    "survive BAST to the same trees" in {
      val original = parse(source, "src")
      val written = Pass
        .runThesePasses(PassInput(original), Seq(BASTWriterPass.creator()))
        .outputOf[BASTOutput](BASTWriterPass.name)
        .getOrElse(fail("no BAST output"))
      BASTReader.read(written.bytes) match
        case Right(back)  => trees(back) mustBe trees(original)
        case Left(errors) => fail(s"BAST read failed: ${errors.format}")
    }

    "fall back to a VALUE when the words after `every` are not a whole schedule" in {
      // A Duration constant named `month` is a value: `month` alone is not a schedule (a month
      // cadence needs its `on …` anchor), so the schedule parse fails and `value` takes over.
      val text =
        """domain D is { context C is {
          |  constant month: Duration = 30 days
          |  constant cron: Duration = 1 hour
          |  handler H is {
          |    on every month is { do "x" }
          |    on every cron is { do "y" }
          |  } with { briefly "h" }
          |} with { briefly "c" } } with { briefly "d" }
          |""".stripMargin
      val vs = values(parse(text, "fallback"))
      vs.size mustBe 2
      vs.foreach(_ mustBe a[ValueRef])
    }

    "prefer the schedule when the words ARE a whole schedule (documented precedence)" in {
      // `every day` is a complete schedule (daily at midnight), so it wins over a constant named
      // `day`. The EBNF records this precedence.
      val text =
        """domain D is { context C is {
          |  constant day: Duration = 1 day
          |  handler H is { on every day is { do "x" } } with { briefly "h" }
          |} with { briefly "c" } } with { briefly "d" }
          |""".stripMargin
      values(parse(text, "precedence")).head mustBe a[CalendarSchedule]
    }
  }
}
