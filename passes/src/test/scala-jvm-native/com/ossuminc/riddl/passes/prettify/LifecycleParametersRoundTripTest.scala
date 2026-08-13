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

/** Task 3: RIDDL is reflective, so `on init`/`on term` parameter lists must survive
  * prettify -> re-parse, just like A55's message binding and A57's envelope binding before them.
  */
class LifecycleParametersRoundTripTest extends AbstractValidatingTest {

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
      |    record r is { total: Integer } with { briefly "r" }
      |    entity Order is {
      |      state s of record r is {
      |        handler h is {
      |          on init(total: Integer) is { do "start" }
      |          on term(oid: Id(entity Order), reason: String) is { do "end" }
      |        } with { briefly "h" }
      |      } with { briefly "s" }
      |    } with { briefly "e" }
      |  } with { briefly "c" }
      |}
      |""".stripMargin

  "on init/on term parameter lists" should {

    "survive a prettify round trip" in { (td: TestData) =>
      val root1 = parse(src, "src")
      val oic1 = Finder(root1).recursiveFindByType[OnInitializationClause].headOption
      oic1.map(_.parameters.map(_.name)) mustBe Some(Seq("total"))
      val otc1 = Finder(root1).recursiveFindByType[OnTerminationClause].headOption
      otc1.map(_.parameters.map(_.name)) mustBe Some(Seq("oid", "reason"))

      val pretty = prettify(root1)
      pretty must include("on init(total: Integer) is")
      pretty must include("on term(oid: Id(entity Order), reason: String) is")

      val root2 = parse(pretty, "regen")
      val oic2 = Finder(root2).recursiveFindByType[OnInitializationClause].headOption
      oic2.map(_.parameters.map(a => a.name -> a.typeEx.format)) mustBe
        Some(Seq("total" -> "Integer"))
      val otc2 = Finder(root2).recursiveFindByType[OnTerminationClause].headOption
      otc2.map(_.parameters.map(_.name)) mustBe Some(Seq("oid", "reason"))
      otc2.flatMap(_.parameters.headOption.map(_.typeEx)) mustBe a[Some[?]]
      otc2.flatMap(_.parameters.headOption.map(_.typeEx)).get mustBe a[UniqueId]
    }

    "keep 'on init' without parameters bare" in { (td: TestData) =>
      val bareSrc =
        """domain d is {
          |  context c is {
          |    record r is { total: Integer } with { briefly "r" }
          |    entity Order is {
          |      state s of record r is {
          |        handler h is {
          |          on init is { do "start" }
          |        } with { briefly "h" }
          |      } with { briefly "s" }
          |    } with { briefly "e" }
          |  } with { briefly "c" }
          |}
          |""".stripMargin
      val root1 = parse(bareSrc, "bare")
      val pretty = prettify(root1)
      pretty must include("on init is")
      pretty must not include "on init("
    }
  }
}
