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

/** Every definition prettify writes starts on its own line (ossum.tech, 2026-11-17).
  *
  * A one-line leaf (`user`, `constant`, `inlet`, `connector`) ended in `emitMetaData`, which writes
  * nothing when there is no `with { … }` block -- so with no metadata the line was never ended, and
  * the NEXT definition (or the container's closing brace) was appended to it:
  * `inlet I is command Cm    }`, `connector X is from … to …    entity E is { ??? }`. The output
  * still parsed, which is why nothing caught it. Five other leaves had been fixed one at a time
  * (`invariant`, `version`, `copyright`, `input`, `output`); this suite covers the rest by shape:
  * no line may hold a run of three or more blanks between two tokens.
  *
  * Also: a function written `requires … returns … ???` came back with no `???`. The `???` leaves no
  * trace in the AST, so prettify writes it whenever a function body holds nothing but its clauses.
  */
class LeafLineTerminationTest extends AbstractValidatingTest {

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

  private val src: String =
    """domain D is {
      |  user U is "someone"
      |  user U2 is "other"
      |  context C is {
      |    constant K: Integer = 5
      |    constant K2: Integer = 6
      |    command Cm is { x: Integer }
      |    record RR is { r: Id(entity E) }
      |    streamlet S as source is { outlet O is command Cm outlet O2 is command Cm }
      |    streamlet K3 as sink is { inlet I is command Cm inlet I2 is command Cm }
      |    connector X is { from outlet S.O to inlet K3.I }
      |    connector X2 is { from outlet S.O2 to inlet K3.I2 }
      |    entity E is { ??? }
      |    function fn is { requires { a: Integer } returns { b: Integer } ??? }
      |    function fn2 is { ??? }
      |  }
      |}
      |""".stripMargin

  /** A run of 3+ blanks between two tokens is where a line that should have ended was continued. */
  private val runOn = "\\S {3,}\\S".r

  "Prettify" should {
    "start every definition on its own line (the ossum.tech repro)" in { (td: TestData) =>
      val pretty = prettify(parse(src, "src"))
      val joined = pretty.linesIterator.filter(l => runOn.findFirstIn(l).nonEmpty).toSeq
      withClue(pretty) { joined mustBe empty }
      val lines = pretty.linesIterator.map(_.trim).toSeq
      lines must contain("user U is \"someone\"")
      lines must contain("constant K: Integer = 5")
      lines must contain("inlet I is command Cm")
      lines must contain("connector X is from outlet S.O to inlet K3.I")
    }

    "keep a `???` function body" in { (td: TestData) =>
      val pretty = prettify(parse(src, "src"))
      val fn = pretty.linesIterator.dropWhile(!_.contains("function fn is")).take(5).mkString("\n")
      withClue(pretty) { fn must include("???") }
      val fn2 = pretty.linesIterator.dropWhile(!_.contains("function fn2 is")).take(3).mkString("\n")
      withClue(pretty) { fn2 must include("???") }
    }

    "converge: prettifying the prettified text changes nothing" in { (td: TestData) =>
      val once = prettify(parse(src, "src"))
      val twice = prettify(parse(once, "regen"))
      twice mustBe once
      Finder(parse(once, "regen")).recursiveFindByType[Connector].map(_.id.value) mustBe
        Seq("X", "X2")
    }
  }
}
