/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.language.{Finder, Messages, PredefinedModule, *}
import com.ossuminc.riddl.passes.prettify.{PrettifyOutput, PrettifyPass}
import com.ossuminc.riddl.passes.{
  BASTOutput,
  BASTWriterPass,
  Pass,
  PassInput,
  PassRoot,
  PassesOutput,
  PassesResult
}
import com.ossuminc.riddl.utils.pc

import org.scalatest.TestData

/** The predefined `Riddl` standard module and its two terminators.
  *
  * Under the unified streaming model every port is the endpoint of exactly ONE connector (A31), so
  * every outlet must terminate somewhere and every inlet must be fed. `BottomlessPit` and
  * `ForeverEmpty` are the shared way to say "deliberately terminated here" — available with no
  * import — and they are exempt from the cardinality and reachability rules they exist to satisfy.
  *
  * The module is NEVER injected into the user's AST: a model that ignores it is byte-for-byte
  * unchanged. That non-injection is asserted here directly.
  */
class PredefinedTerminatorsTest extends AbstractValidatingTest {

  private def validate(src: String, td: TestData): PassesResult =
    TopLevelParser.parseInput(RiddlParserInput(src, td)) match
      case Left(messages) => fail(s"parse failed:\n${messages.format}")
      case Right(root)    => Pass.runStandardPasses(root)

  private def bastBytes(root: PassRoot): Array[Byte] =
    Pass
      .runThesePasses(PassInput(root), Seq(BASTWriterPass.creator()))
      .outputOf[BASTOutput](BASTWriterPass.name)
      .getOrElse(fail("BASTWriterPass produced no output"))
      .bytes

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

  /** Two DIFFERENT outlets, in two different contexts, both terminated in the one universal drain.
    * Without the A31 exemption this is an error on `hole`; with it, the model is clean.
    */
  private val twoIntoTheSameDrain: String =
    """domain Plumbing is {
      |  type Water is String
      |  context Kitchen is {
      |    processor Faucet as source is {
      |      outlet flowing is type Plumbing.Water
      |    }
      |  }
      |  context Bathroom is {
      |    processor Shower as source is {
      |      outlet raining is type Plumbing.Water
      |    }
      |  }
      |  connector KitchenDrain is {
      |    from outlet Kitchen.Faucet.flowing to inlet BottomlessPit.hole
      |  } with { option persistent }
      |  connector BathroomDrain is {
      |    from outlet Bathroom.Shower.raining to inlet BottomlessPit.hole
      |  } with { option persistent }
      |}
      |""".stripMargin

  private val fedByForeverEmpty: String =
    """domain Plumbing is {
      |  type Water is String
      |  context Kitchen is {
      |    processor Basin as sink is {
      |      inlet filling is type Plumbing.Water
      |    }
      |  }
      |  connector NeverFills is {
      |    from outlet ForeverEmpty.void to inlet Kitchen.Basin.filling
      |  } with { option persistent }
      |}
      |""".stripMargin

  /** A model that never mentions the standard module. Its output must be untouched by it. */
  private val obliviousModel: String =
    """domain Simple is {
      |  type Thing is String
      |  context Only is {
      |    processor Producer as source is {
      |      outlet out is type Simple.Thing
      |    }
      |    processor Consumer as sink is {
      |      inlet in is type Simple.Thing
      |    }
      |    connector Wire is { from outlet Producer.out to inlet Consumer.in }
      |  }
      |}
      |""".stripMargin

  "the predefined Riddl module" must {

    "be a single cached instance" in { (td: TestData) =>
      PredefinedModule.module must be theSameInstanceAs PredefinedModule.module
      PredefinedModule.module.id.value mustBe PredefinedModule.name
    }

    "hold its processors DIRECTLY, with no domain/context wrapping" in { (td: TestData) =>
      // Operations joined the two terminators: it is the default destination for hard-error
      // notifications, so a generator can count on one existing. Listing them exhaustively is
      // deliberate -- anything added to the standard module is always in every model's scope,
      // which is a decision that should never happen by accident.
      val streamlets = PredefinedModule.module.contents.filter[Streamlet]
      streamlets.map(_.id.value) must contain theSameElementsAs
        Seq(
          PredefinedModule.bottomlessPit,
          PredefinedModule.foreverEmpty,
          PredefinedModule.operations
        )
      val pit = streamlets.find(_.id.value == PredefinedModule.bottomlessPit).get
      pit.effectiveShape mustBe a[Sink]
      pit.inlets.size mustBe 1
      pit.outlets mustBe empty
      val spring = streamlets.find(_.id.value == PredefinedModule.foreverEmpty).get
      spring.effectiveShape mustBe a[Source]
      spring.outlets.size mustBe 1
      spring.inlets mustBe empty
      // `Drain` is the universal type: the dual of `Nothing`.
      PredefinedModule.module.contents.filter[Type].find(_.id.value == "Drain") match
        case Some(drain) => drain.typEx mustBe a[Anything]
        case None        => fail("the predefined `Drain` type was not found")
    }

    "parse AND validate cleanly on its own" in { (td: TestData) =>
      val result = Pass.runStandardPasses(PredefinedModule.root)
      if result.messages.nonEmpty then
        fail(s"the predefined module is not clean:\n${result.messages.format}")
      end if
    }
  }

  "the predefined Operations sink" must {

    "carry an inlet typed by HardError" in { (td: TestData) =>
      val ops = PredefinedModule.module.contents
        .filter[Streamlet]
        .find(_.id.value == PredefinedModule.operations)
        .getOrElse(fail("the predefined Operations processor was not found"))
      ops.effectiveShape mustBe a[Sink]
      ops.inlets.size mustBe 1
      ops.outlets mustBe empty
      ops.inlets.head.type_.pathId.format must include(PredefinedModule.hardError)
    }

    "carry the HardError record it is typed by" in { (td: TestData) =>
      PredefinedModule.module.contents
        .filter[Type]
        .find(_.id.value == PredefinedModule.hardError)
        .getOrElse(fail("the predefined HardError record was not found"))
      succeed
    }
  }

  "BottomlessPit" must {

    "accept TWO different outlets on its single inlet (A31 exemption)" in { (td: TestData) =>
      val result = validate(twoIntoTheSameDrain, td)
      withClue(result.messages.format) {
        result.messages.justErrors mustBe empty
        result.messages.filter(_.message.contains("is connected by")) mustBe empty
        result.messages.filter(_.message.contains("is not connected")) mustBe empty
        result.messages.filter(_.message.contains("no downstream path")) mustBe empty
      }
    }

    "be reachable with no import statement at all" in { (td: TestData) =>
      twoIntoTheSameDrain mustNot include("import")
    }
  }

  "ForeverEmpty" must {
    "feed a user inlet cleanly" in { (td: TestData) =>
      val result = validate(fedByForeverEmpty, td)
      withClue(result.messages.format) {
        result.messages.justErrors mustBe empty
        result.messages.filter(_.message.contains("is not connected")) mustBe empty
        result.messages.filter(_.message.contains("no upstream path")) mustBe empty
      }
    }
  }

  "a model that never mentions the terminators" must {

    "validate with NO message referring to the standard module" in { (td: TestData) =>
      val result = validate(obliviousModel, td)
      withClue(result.messages.format) {
        result.messages.justErrors mustBe empty
        result.messages.filter { (m: Messages.Message) =>
          m.message.contains(PredefinedModule.name) ||
          m.message.contains(PredefinedModule.bottomlessPit) ||
          m.message.contains(PredefinedModule.foreverEmpty) ||
          m.message.contains("Drain")
        } mustBe empty
      }
    }

    "keep the standard module OUT of its AST (non-injection)" in { (td: TestData) =>
      val result = validate(obliviousModel, td)
      val root = result.root
      root.contents.filter[Module] mustBe empty
      // Nothing anywhere in the tree is one of the predefined definitions.
      val allDefinitions = Finder(root).recursiveFindByType[Definition]
      allDefinitions.exists(PredefinedModule.isPredefined) mustBe false
    }

    "serialize to BAST bytes containing nothing from the standard module" in { (td: TestData) =>
      val root = TopLevelParser.parseInput(RiddlParserInput(obliviousModel, td)) match
        case Left(messages) => fail(messages.format)
        case Right(r)       => r
      // Bytes written BEFORE any pass runs (so no seeding could have happened) must equal the
      // bytes written AFTER the standard passes: the passes do not touch the user's AST.
      val before = bastBytes(root)
      val after = bastBytes(validate(obliviousModel, td).root)
      after mustBe before
      val text = new String(before.map(b => (b & 0xff).toChar))
      text mustNot include(PredefinedModule.bottomlessPit)
      text mustNot include(PredefinedModule.foreverEmpty)
      text mustNot include("Drain")
    }

    "prettify to output containing nothing from the standard module" in { (td: TestData) =>
      val root = TopLevelParser.parseInput(RiddlParserInput(obliviousModel, td)) match
        case Left(messages) => fail(messages.format)
        case Right(r)       => r
      val pretty = prettify(root)
      pretty mustNot include("module Riddl")
      pretty mustNot include(PredefinedModule.bottomlessPit)
      pretty mustNot include(PredefinedModule.foreverEmpty)
      pretty mustNot include("Drain")
      // and it still round-trips
      TopLevelParser.parseInput(RiddlParserInput(pretty, "reparsed")) match
        case Left(messages) => fail(s"prettified output did not re-parse:\n${messages.format}")
        case Right(again)   => again.contents.filter[Module] mustBe empty
    }
  }

  "a user definition that shadows a predefined name" must {

    "win: the user's definition is what resolves" in { (td: TestData) =>
      val src =
        """domain Mine is {
          |  type Water is String
          |  context Only is {
          |    processor Faucet as source is {
          |      outlet flowing is type Mine.Water
          |    }
          |    processor BottomlessPit as sink is {
          |      inlet hole is type Mine.Water
          |    }
          |    connector Wire is { from outlet Faucet.flowing to inlet BottomlessPit.hole }
          |  }
          |}
          |""".stripMargin
      val result = validate(src, td)
      withClue(result.messages.format) { result.messages.justErrors mustBe empty }
      // The connector's inlet resolved to the USER's BottomlessPit, not the predefined one, so it
      // is subject to the ordinary A31 rules (proved by a SECOND connector into it erroring).
      val mine = Finder(result.root)
        .recursiveFindByType[Streamlet]
        .find(_.id.value == PredefinedModule.bottomlessPit)
        .getOrElse(fail("the user's BottomlessPit was not found"))
      PredefinedModule.isPredefined(mine) mustBe false
    }

    "still be subject to A31 (the exemption does NOT transfer by name)" in { (td: TestData) =>
      val src =
        """domain Mine is {
          |  type Water is String
          |  context Only is {
          |    processor Faucet as source is {
          |      outlet flowing is type Mine.Water
          |    }
          |    processor Shower as source is {
          |      outlet raining is type Mine.Water
          |    }
          |    processor BottomlessPit as sink is {
          |      inlet hole is type Mine.Water
          |    }
          |    connector One is { from outlet Faucet.flowing to inlet BottomlessPit.hole }
          |    connector Two is { from outlet Shower.raining to inlet BottomlessPit.hole }
          |  }
          |}
          |""".stripMargin
      val result = validate(src, td)
      result.messages.justErrors.exists(_.message.contains("is connected by 2 connectors")) mustBe
        true
    }
  }
}
