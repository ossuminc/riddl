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

/** A55: RIDDL is reflective, so an on-clause's optional message binding (`on foo: command Foo`)
  * must survive prettify → re-parse. The same test guards the `from [<name>:] <origin>` clause,
  * which prettify never emitted before A55 (it was silently dropped on every round trip).
  */
class OnClauseBindingRoundTripTest extends AbstractValidatingTest {

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
      |    command DoIt is { a: Integer }
      |    event Done is { b: Integer }
      |    inlet in is type DoIt
      |    handler h is {
      |      on foo: command DoIt { do "handle" }
      |      on evt: event Done from context c { do "note" }
      |      on command DoIt from di: context c { do "again" }
      |    }
      |  }
      |}
      |""".stripMargin

  "A55 on-clause binding" should {

    "survive a prettify round trip, together with the `from` clause" in { (td: TestData) =>
      val root1 = parse(src, "src")
      val clauses1 = Finder(root1).recursiveFindByType[OnMessageLikeClause]
      clauses1.size mustBe 3
      clauses1.flatMap(_.binding.map(_.value)) mustBe Seq("foo", "evt")

      val pretty = prettify(root1)
      pretty must include("on foo: command DoIt is")
      pretty must include("on evt: event Done from context c is")
      // The `from <name>: <origin>` clause was dropped entirely before A55.
      pretty must include("on command DoIt from di: context c is")

      val root2 = parse(pretty, "regen")
      val clauses2 = Finder(root2).recursiveFindByType[OnMessageLikeClause]
      clauses2.size mustBe 3
      clauses2.flatMap(_.binding.map(_.value)) mustBe Seq("foo", "evt")
      // Both the anonymous and the named `from` origins survive.
      clauses2.flatMap(_.from).size mustBe 2
      clauses2.flatMap(_.from).flatMap(_._1.map(_.value)) mustBe Seq("di")
    }
  }
}
