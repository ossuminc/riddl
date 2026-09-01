/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Finder
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.utils.{pc, ec}
import org.scalatest.TestData

/** `format` renders how the model DECLARES a definition.
  *
  * RIDDL 2.0 put meaning into prefixes and suffixes -- entity intentions, a context's intention,
  * `initial` on handlers and states, `yields` on a message type, `as <shape>` on a processor. A
  * `format` that drops them tells a consumer `entity Order` for something whose declaration is the
  * difference between a model that must satisfy the event-sourcing rules and one that need not.
  * Reported by riddl-generator, which had begun re-deriving the composition locally.
  *
  * The rendering matches `RiddlFileEmitter.openDef` exactly, because they share one implementation.
  * Notably that means a Streamlet renders the canonical `streamlet` keyword -- `processor`, the
  * previous canonical spelling, was itself deprecated by [5.1] -- and shows a shape only
  * when the author ASCRIBED one -- never the shape keywords 2.0 deprecated.
  */
class DeclarationFormatTest extends AbstractValidatingTest {

  private val model: String =
    """domain D is {
      |  application context Storefront is {
      |    aggregate consistent event-sourced entity Order is {
      |      record Fields is { total: Integer }
      |      command Place yields event Placed is { total: Integer }
      |      event Placed is { total: Integer }
      |      initial state Main of record Order.Fields is {
      |        initial handler Behavior is {
      |          on command Order.Place { yield event Order.Placed }
      |          on event Order.Placed { set field Main.total to "1" }
      |        }
      |      }
      |    }
      |    processor Ascribed as source is {
      |      outlet Out is event Storefront.Placed
      |    }
      |    processor Derived is {
      |      outlet Only is event Storefront.Placed
      |    }
      |  }
      |}
      |""".stripMargin

  private def formatOf[T <: Definition: reflect.ClassTag](
    root: Root,
    name: String
  ): String =
    Finder(root).recursiveFindByType[T].find(_.id.value == name) match
      case Some(d) => d.format
      case None    => fail(s"no ${reflect.classTag[T].runtimeClass.getSimpleName} named '$name'")
  end formatOf

  "format" should {

    "render a Context's intention" in { (td: TestData) =>
      parseAndValidateInput(RiddlParserInput(model, td), shouldFailOnErrors = false) {
        (root, _, _) =>
          formatOf[Context](root, "Storefront") mustBe "application context Storefront"
      }
    }

    "render an Entity's intentions, in canonical order" in { (td: TestData) =>
      // The parser sorts intentions canonically, so written order is gone before the AST exists;
      // canonical is the only renderable answer, and it is what prettify emits too.
      parseAndValidateInput(RiddlParserInput(model, td), shouldFailOnErrors = false) {
        (root, _, _) =>
          formatOf[Entity](root, "Order") mustBe
            "aggregate consistent event-sourced entity Order"
      }
    }

    "render a Handler's `initial` marker" in { (td: TestData) =>
      parseAndValidateInput(RiddlParserInput(model, td), shouldFailOnErrors = false) {
        (root, _, _) => formatOf[Handler](root, "Behavior") mustBe "initial handler Behavior"
      }
    }

    "render a State's `initial` marker" in { (td: TestData) =>
      parseAndValidateInput(RiddlParserInput(model, td), shouldFailOnErrors = false) {
        (root, _, _) => formatOf[State](root, "Main") mustBe "initial state Main"
      }
    }

    "render a message Type's `yields` clause" in { (td: TestData) =>
      parseAndValidateInput(RiddlParserInput(model, td), shouldFailOnErrors = false) {
        (root, _, _) =>
          formatOf[Type](root, "Place") mustBe "command Place yields event Placed"
      }
    }

    "render an ASCRIBED streamlet shape as an ascription, never the deprecated keyword" in {
      (td: TestData) =>
        parseAndValidateInput(RiddlParserInput(model, td), shouldFailOnErrors = false) {
          (root, _, _) =>
            formatOf[Streamlet](root, "Ascribed") mustBe "streamlet Ascribed as source"
        }
    }

    "render a DERIVED streamlet shape with no ascription, matching prettify" in { (td: TestData) =>
      // Deliberate: prettify emits nothing when the shape was not written down, so format does
      // not either. The alternative -- showing effectiveShape -- would disagree with the
      // prettifier about the same definition, which is the defect being fixed.
      parseAndValidateInput(RiddlParserInput(model, td), shouldFailOnErrors = false) {
        (root, _, _) => formatOf[Streamlet](root, "Derived") mustBe "streamlet Derived"
      }
    }
  }
}
