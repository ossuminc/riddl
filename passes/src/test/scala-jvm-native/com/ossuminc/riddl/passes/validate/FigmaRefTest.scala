/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Finder
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.passes.prettify.{PrettifyOutput, PrettifyPass}
import com.ossuminc.riddl.passes.{Pass, PassInput, PassesOutput}
import com.ossuminc.riddl.utils.{CommonOptions, FigmaClient, FigmaLookup, pc}

import org.scalatest.{Assertion, TestData}

/** A42: a `figma "<fileKey>" node "<nodeId>"` reference is metadata that names one frame of a Figma
  * design file. It is legal only on the UI-bearing definitions (Input, Output, Group and an
  * application-intended Context) and, when drift checking is explicitly enabled, is checked against
  * the Figma REST API.
  *
  * NO TEST HERE TOUCHES THE NETWORK. The default state of the feature is off, and every test of the
  * enabled state supplies a stub client through `FigmaClient.withClient`.
  */
class FigmaRefTest extends AbstractValidatingTest {

  /** A stub standing in for the Figma REST API. Records what it was asked so a test can assert that
    * it was NOT asked at all.
    */
  private class StubFigmaClient(answers: Map[(String, String), FigmaLookup]) extends FigmaClient {
    var calls: Seq[(String, String)] = Seq.empty
    override def lookupNode(fileKey: String, nodeId: String): FigmaLookup =
      calls = calls :+ (fileKey, nodeId)
      answers.getOrElse((fileKey, nodeId), FigmaLookup.Missing)
    end lookupNode
  }

  /** The canonical, well-placed model: every kind of definition that may carry a reference does. */
  private val wellPlacedModel: String =
    """domain Storefront is {
      |  application context Checkout is {
      |    command PlaceOrder is { item: String }
      |    result Confirmation is { text: String }
      |    group PaymentScreen is {
      |      input CardNumber acquires command Storefront.Checkout.PlaceOrder with {
      |        figma "FILEKEY" node "12:34"
      |      }
      |      output OrderSummary presents result Storefront.Checkout.Confirmation with {
      |        figma "FILEKEY" node "12:36"
      |      }
      |    } with {
      |      figma "FILEKEY" node "12:30"
      |    }
      |  } with {
      |    figma "FILEKEY" node "12:1"
      |  }
      |}
      |""".stripMargin

  private def validating(input: String, td: TestData)(check: Messages => Assertion): Assertion =
    parseAndValidateInput(RiddlParserInput(input, td), shouldFailOnErrors = false) {
      case (_, _, msgs: Messages) => check(msgs)
    }

  private def parse(src: String, origin: String): Root =
    TopLevelParser.parseInput(RiddlParserInput(src, origin)) match
      case Right(root) => root
      case Left(msgs)  => fail(s"parse of $origin failed:\n${msgs.format}")

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

  private def figmaMessages(msgs: Messages): Messages =
    msgs.filter(m => m.message.toLowerCase.contains("figma"))

  "A42 figma reference parsing" should {

    "parse on every permitted definition and keep both literal strings" in { (_: TestData) =>
      val root = parse(wellPlacedModel, "wellPlaced")
      val refs = Finder(root).recursiveFindByType[Group].head.figmaRefs
      refs.size mustBe 1
      refs.head.fileKey.s mustBe "FILEKEY"
      refs.head.nodeId.s mustBe "12:30"

      val all = Finder(root).recursiveFindByType[Input].head.figmaRefs ++
        Finder(root).recursiveFindByType[Output].head.figmaRefs ++
        Finder(root).recursiveFindByType[Context].head.figmaRefs
      all.map(_.nodeId.s) must contain theSameElementsAs Seq("12:34", "12:36", "12:1")
    }

    "round-trip through prettify at the same place on every permitted definition" in {
      (_: TestData) =>
        val pretty = prettify(parse(wellPlacedModel, "wellPlaced"))
        pretty must include("""figma "FILEKEY" node "12:30"""")

        val again = parse(pretty, "regen")
        Finder(again).recursiveFindByType[Group].head.figmaRefs.head.nodeId.s mustBe "12:30"
        Finder(again).recursiveFindByType[Input].head.figmaRefs.head.nodeId.s mustBe "12:34"
        Finder(again).recursiveFindByType[Output].head.figmaRefs.head.nodeId.s mustBe "12:36"
        Finder(again).recursiveFindByType[Context].head.figmaRefs.head.nodeId.s mustBe "12:1"
    }
  }

  "A42 figma reference placement" should {

    "be accepted on input, output, group and an application-intended context" in { (td: TestData) =>
      validating(wellPlacedModel, td)(msgs => figmaMessages(msgs).justErrors mustBe empty)
    }

    "be rejected on an entity" in { (td: TestData) =>
      validating(
        """domain D is {
          |  context C is {
          |    entity E is {
          |      handler H is { ??? }
          |    } with {
          |      figma "FILEKEY" node "1:2"
          |    }
          |  }
          |}
          |""".stripMargin,
        td
      ) { msgs =>
        val errors = figmaMessages(msgs).justErrors
        errors.size mustBe 1
        errors.head.message must include("not allowed on Entity 'E'")
      }
    }

    "be rejected on a context that is not application-intended" in { (td: TestData) =>
      validating(
        """domain D is {
          |  service context C is {
          |    type T is String
          |  } with {
          |    figma "FILEKEY" node "1:2"
          |  }
          |}
          |""".stripMargin,
        td
      ) { msgs =>
        val errors = figmaMessages(msgs).justErrors
        errors.size mustBe 1
        errors.head.message must include("not allowed on Context 'C'")
      }
    }

    "be rejected on a domain" in { (td: TestData) =>
      validating(
        """domain D is {
          |  type T is String
          |} with {
          |  figma "FILEKEY" node "1:2"
          |}
          |""".stripMargin,
        td
      ) { msgs =>
        figmaMessages(msgs).justErrors.size mustBe 1
      }
    }
  }

  "A42 figma drift validation" should {

    "make no request and emit nothing when the flag is off (the default)" in { (td: TestData) =>
      val stub = StubFigmaClient(Map.empty)
      CommonOptions.default.checkFigmaDrift mustBe false
      FigmaClient.withClient(stub) {
        validating(wellPlacedModel, td) { msgs =>
          stub.calls mustBe empty
          figmaMessages(msgs) mustBe empty
        }
      }
    }

    "report a missing node as an error when enabled" in { (td: TestData) =>
      val stub = StubFigmaClient(
        Map(
          ("FILEKEY", "12:34") -> FigmaLookup.Found("CardNumber"),
          ("FILEKEY", "12:36") -> FigmaLookup.Found("OrderSummary"),
          ("FILEKEY", "12:30") -> FigmaLookup.Missing,
          ("FILEKEY", "12:1") -> FigmaLookup.Found("Checkout")
        )
      )
      FigmaClient.withClient(stub) {
        pc.withOptions[Assertion](CommonOptions.default.copy(checkFigmaDrift = true)) { _ =>
          validating(wellPlacedModel, td) { msgs =>
            val errors = figmaMessages(msgs).justErrors
            errors.size mustBe 1
            errors.head.message must include("'12:30'")
            errors.head.message must include("does not exist")
          }
        }
      }
    }

    "report a frame name that does not correspond as a warning when enabled" in { (td: TestData) =>
      val stub = StubFigmaClient(
        Map(
          ("FILEKEY", "12:34") -> FigmaLookup.Found("CardNumber"),
          ("FILEKEY", "12:36") -> FigmaLookup.Found("OrderSummary"),
          ("FILEKEY", "12:30") -> FigmaLookup.Found("Shipping Screen"),
          ("FILEKEY", "12:1") -> FigmaLookup.Found("Checkout")
        )
      )
      FigmaClient.withClient(stub) {
        pc.withOptions[Assertion](CommonOptions.default.copy(checkFigmaDrift = true)) { _ =>
          validating(wellPlacedModel, td) { msgs =>
            figmaMessages(msgs).justErrors mustBe empty
            val warnings = figmaMessages(msgs).filter(_.kind == Warning)
            warnings.size mustBe 1
            warnings.head.message must include("'Shipping Screen'")
            warnings.head.message must include("drifted apart")
          }
        }
      }
    }

    "accept a frame name that differs only in case, spacing and separators" in { (td: TestData) =>
      val stub = StubFigmaClient(
        Map(
          ("FILEKEY", "12:34") -> FigmaLookup.Found("card number"),
          ("FILEKEY", "12:36") -> FigmaLookup.Found("Order_Summary"),
          ("FILEKEY", "12:30") -> FigmaLookup.Found("Payment Screen"),
          ("FILEKEY", "12:1") -> FigmaLookup.Found("CHECKOUT")
        )
      )
      FigmaClient.withClient(stub) {
        pc.withOptions[Assertion](CommonOptions.default.copy(checkFigmaDrift = true)) { _ =>
          validating(wellPlacedModel, td)(msgs => figmaMessages(msgs) mustBe empty)
        }
      }
    }

    "never fail the build when the API is unreachable" in { (td: TestData) =>
      val stub = StubFigmaClient(
        Map(
          ("FILEKEY", "12:34") -> FigmaLookup.Unavailable("connect timed out"),
          ("FILEKEY", "12:36") -> FigmaLookup.Unavailable("connect timed out"),
          ("FILEKEY", "12:30") -> FigmaLookup.Unavailable("connect timed out"),
          ("FILEKEY", "12:1") -> FigmaLookup.Unavailable("connect timed out")
        )
      )
      FigmaClient.withClient(stub) {
        pc.withOptions[Assertion](CommonOptions.default.copy(checkFigmaDrift = true)) { _ =>
          validating(wellPlacedModel, td)(msgs => figmaMessages(msgs) mustBe empty)
        }
      }
    }

    "report a denied file as an error when enabled, admitting the access ambiguity" in {
      (td: TestData) =>
        val denied = FigmaLookup.FileNotFound("the Figma API answered HTTP 404 for file 'FILEKEY'")
        val stub = StubFigmaClient(
          Map(
            ("FILEKEY", "12:34") -> denied,
            ("FILEKEY", "12:36") -> denied,
            ("FILEKEY", "12:30") -> denied,
            ("FILEKEY", "12:1") -> denied
          )
        )
        FigmaClient.withClient(stub) {
          pc.withOptions[Assertion](CommonOptions.default.copy(checkFigmaDrift = true)) { _ =>
            validating(wellPlacedModel, td) { msgs =>
              val errors = figmaMessages(msgs).justErrors
              // One per reference, as a missing node is; all four name the file, and each says
              // both of the things a 404 can mean so the reader is not sent after the wrong one.
              errors.size mustBe 4
              errors.foreach { e =>
                e.message must include("'FILEKEY'")
                e.message must include("could not be read")
                e.message must include("cannot see it")
              }
              succeed
            }
          }
        }
    }

    "keep a denied file silent while the flag is off" in { (td: TestData) =>
      val stub = StubFigmaClient(
        Map(("FILEKEY", "12:30") -> FigmaLookup.FileNotFound("HTTP 404"))
      )
      FigmaClient.withClient(stub) {
        validating(wellPlacedModel, td) { msgs =>
          stub.calls mustBe empty
          figmaMessages(msgs) mustBe empty
        }
      }
    }

    "ask about each distinct node only once" in { (td: TestData) =>
      val stub = StubFigmaClient(
        Map(
          ("FILEKEY", "12:34") -> FigmaLookup.Found("CardNumber"),
          ("FILEKEY", "12:36") -> FigmaLookup.Found("OrderSummary"),
          ("FILEKEY", "12:30") -> FigmaLookup.Found("PaymentScreen"),
          ("FILEKEY", "12:1") -> FigmaLookup.Found("Checkout")
        )
      )
      FigmaClient.withClient(stub) {
        pc.withOptions[Assertion](CommonOptions.default.copy(checkFigmaDrift = true)) { _ =>
          validating(wellPlacedModel, td) { msgs =>
            figmaMessages(msgs) mustBe empty
            stub.calls.distinct.size mustBe stub.calls.size
          }
        }
      }
    }
  }
}
