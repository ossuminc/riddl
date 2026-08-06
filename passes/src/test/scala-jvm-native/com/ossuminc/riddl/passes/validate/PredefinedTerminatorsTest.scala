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
      // Listing them exhaustively is deliberate: anything added to the standard module is
      // always in every model's scope, which is a decision that should never happen by
      // accident. An `Operations` sink was proposed alongside GeneratorError and REJECTED -- a
      // context that handles alerts is the model's own, not the standard library's. What the
      // module owes a generator is the SHAPE of the notification; `option error-sink` names its
      // destination.
      val streamlets = PredefinedModule.module.contents.filter[Streamlet]
      streamlets.map(_.id.value) must contain theSameElementsAs
        Seq(PredefinedModule.bottomlessPit, PredefinedModule.foreverEmpty)
      val pit = streamlets.find(_.id.value == PredefinedModule.bottomlessPit).get
      pit.effectiveShape mustBe a[Sink]
      pit.inlets.size mustBe 1
      pit.outlets mustBe empty
      val spring = streamlets.find(_.id.value == PredefinedModule.foreverEmpty).get
      spring.effectiveShape mustBe a[Source]
      spring.outlets.size mustBe 1
      spring.inlets mustBe empty
      // The records: the shape a generator sends, and the metadata a message travels with.
      // Exhaustive for the same reason — adding one to the standard module is a decision, and
      // this list is where it has to be made deliberately.
      PredefinedModule.module.contents
        .filter[Type]
        .filter(_.typEx.isInstanceOf[AggregateUseCaseTypeExpression])
        .map(_.id.value) mustBe Seq(PredefinedModule.generatorError, PredefinedModule.envelope)
      // `Drain` is the universal type: the dual of `Nothing`.
      PredefinedModule.module.contents.filter[Type].find(_.id.value == "Drain") match
        case Some(drain) => drain.typEx mustBe a[Anything]
        case None        => fail("the predefined `Drain` type was not found")
    }

    "parse AND validate cleanly on its own, but for its two records being unused" in {
      (td: TestData) =>
        // `GeneratorError` has no predefined receiver ON PURPOSE -- where hard errors go is the
        // model's to say -- so validating the module ALONE necessarily reports it as unused. That
        // report is the design working, not a defect: it is the nudge that tells a modeller to
        // declare an `error-sink`. `Envelope` is unused for the same reason and by the same
        // design: it is opted into with `option message_envelope`, never imposed. Everything else
        // must still be silent.
        val result = Pass.runStandardPasses(PredefinedModule.root)
        val (unused, rest) = result.messages.partition(_.message.contains("is unused"))
        withClue(s"unexpected messages:\n${rest.format}") { rest mustBe empty }
        unused.map(_.message.takeWhile(_ != '\n')) mustBe
          Seq(
            s"Record '${PredefinedModule.generatorError}' is unused",
            s"Record '${PredefinedModule.envelope}' is unused"
          )
    }

    "report GeneratorError as USED once a model declares an error-sink inlet of that type" in {
      (td: TestData) =>
        // The converse of the case above, and the point of the whole design: the unused warning is
        // not noise to be suppressed, it is a signal that CLEARS when the modeller does the thing
        // it is asking for. Asserting only the absence would be vacuous without the pairing --
        // absence also holds if the model fails to parse, or if usage tracking never sees an
        // inlet's type at all.
        val used = validate(PredefinedModule.source + errorSinkUser, td)
        withClue(used.messages.format) {
          used.messages.justErrors mustBe empty
          // Scoped to GeneratorError deliberately. `Envelope` is still unused in this fixture,
          // which is correct -- nothing here opts into `message_envelope` -- so asserting "no
          // unused messages at all" would couple this test to an unrelated definition.
          used.messages.filter { (m: Messages.Message) =>
            m.message.contains(PredefinedModule.generatorError) && m.message.contains("is unused")
          } mustBe empty
        }
        // ...and the SAME source without that one context still reports it, so the difference is
        // attributable to the error-sink inlet and nothing else about this fixture.
        val unused = validate(PredefinedModule.source, td)
        unused.messages.filter { (m: Messages.Message) =>
          m.message.contains(PredefinedModule.generatorError) && m.message.contains("is unused")
        } must not be empty
    }
  }

  /** A context whose inlet accepts `GeneratorError`, marked as the destination for hard errors.
    * Appended to the module source so both live in one Root and usage resolution can see across.
    */
  private val errorSinkUser: String =
    """domain Ops is {
      |  context Alerting is {
      |    processor Receiver as sink is {
      |      inlet alerts is record Riddl.GeneratorError with {
      |        option error-sink
      |        briefly "Where generators report the unrecoverable"
      |      }
      |      handler Record is {
      |        on other { do "record the failure" } with { briefly "h" }
      |      } with { briefly "Records what arrives" }
      |    } with { briefly "Receives hard errors" }
      |  } with { briefly "Operational alerting" }
      |} with { briefly "Operations" }
      |""".stripMargin

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
