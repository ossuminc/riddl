/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.analysis

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.passes.{Pass, PassInput, PassesResult}
import com.ossuminc.riddl.passes.validate.AbstractValidatingTest
import com.ossuminc.riddl.utils.{pc, ec}

import org.scalatest.*

/** `DependencyAnalysisPass` had NO tests at all, which is how `typeDeps` came to be permanently
  * empty without anyone noticing: it was filled only under
  * `parents.collectFirst { case t: Type => t }`, and a `tell` statement's parents are its on-clause,
  * handler, processor, context, domain — never a `Type`. The guard could not succeed, so a public
  * output field documented as "map from each type to types it references" answered "nothing
  * references anything" for every model ever analyzed.
  *
  * Same family as `MessageFlowPass`'s `let`-local blindness (see `LetLocalMessageFlowTest`) and
  * reported alongside it: an empty analysis result is indistinguishable from a model that does not
  * use the construct, so nothing about the output looks wrong.
  */
class DependencyAnalysisPassTest extends AbstractValidatingTest {

  private def runDependencyPass(
    input: String,
    origin: String = "test"
  )(
    check: DependencyOutput => Assertion
  ): Assertion =
    val rpi = RiddlParserInput(input, origin)
    parseValidateAndThen(rpi, shouldFailOnErrors = false) {
      (pr: PassesResult, root: Root, _, _: Messages.Messages) =>
        val passInput = PassInput(root)
        val outputs = pr.outputs
        val pass = DependencyAnalysisPass(passInput, outputs)
        check(Pass.runPass[DependencyOutput](passInput, outputs, pass))
    }
  end runDependencyPass

  private val crossContextTell =
    """domain D is {
      |  context Ordering is {
      |    command PlaceOrder is { id: String }
      |    entity Order is {
      |      handler H is {
      |        on command D.Ordering.PlaceOrder is {
      |          tell command D.Shipping.ShipOrder(id = "an id") to entity D.Shipping.Shipment
      |        }
      |      }
      |    }
      |  }
      |  context Shipping is {
      |    command ShipOrder is { id: String }
      |    entity Shipment is {
      |      handler H is { on command D.Shipping.ShipOrder is { ??? } }
      |    }
      |  }
      |}
      |""".stripMargin

  "DependencyAnalysisPass" should {

    "record the context dependency a cross-context tell creates" in { (td: TestData) =>
      runDependencyPass(crossContextTell) { out =>
        val deps = out.contextDeps.map { case (k, v) => k.id.value -> v.map(_.id.value) }
        deps.get("Ordering") mustBe Some(scala.collection.immutable.Set("Shipping"))
      }
    }

    "record the entity dependency a tell creates" in { (td: TestData) =>
      runDependencyPass(crossContextTell) { out =>
        val deps = out.entityDeps.map { case (k, v) => k.id.value -> v.map(_.id.value) }
        deps.get("Order") mustBe Some(scala.collection.immutable.Set("Shipment"))
      }
    }

    /* The handled message is what `typeDeps`' source has to be: it is the only Type in the picture
     * at a `tell`, and "handling PlaceOrder leads to ShipOrder" is exactly the edge the field's own
     * documentation describes. Nothing else in a tell's surroundings is a Type. */
    "record a type dependency from the HANDLED message to the message told" in { (td: TestData) =>
      runDependencyPass(crossContextTell) { out =>
        val deps = out.typeDeps.map { case (k, v) => k.id.value -> v.map(_.id.value) }
        deps.get("PlaceOrder") mustBe Some(scala.collection.immutable.Set("ShipOrder"))
      }
    }

    "record a type dependency when the told operand is a let-local" in { (td: TestData) =>
      runDependencyPass(
        """domain D is {
          |  context Ordering is {
          |    command PlaceOrder is { id: String }
          |    entity Order is {
          |      handler H is {
          |        on command D.Ordering.PlaceOrder is {
          |          let ship: D.Shipping.ShipOrder = prompt("the shipment request for this order")
          |          tell ship to entity D.Shipping.Shipment
          |        }
          |      }
          |    }
          |  }
          |  context Shipping is {
          |    command ShipOrder is { id: String }
          |    entity Shipment is {
          |      handler H is { on command D.Shipping.ShipOrder is { ??? } }
          |    }
          |  }
          |}
          |""".stripMargin
      ) { out =>
        val deps = out.typeDeps.map { case (k, v) => k.id.value -> v.map(_.id.value) }
        deps.get("PlaceOrder") mustBe Some(scala.collection.immutable.Set("ShipOrder"))
      }
    }

    "record the adaptor bridge and the types it carries" in { (td: TestData) =>
      runDependencyPass(
        """domain D is {
          |  context Source is {
          |    adaptor ToTarget to context D.Target is {
          |      handler AH is {
          |        on command D.Target.DoIt is { do "convert and forward" }
          |      }
          |    }
          |  }
          |  context Target is {
          |    command DoIt is { id: String }
          |    entity E is {
          |      handler H is { on command D.Target.DoIt is { ??? } }
          |    }
          |  }
          |}
          |""".stripMargin
      ) { out =>
        out.adaptorBridges must not be empty
        val bridge = out.adaptorBridges.head
        bridge.sourceContext.id.value mustBe "Source"
        bridge.targetContext.id.value mustBe "Target"
        bridge.bridgedTypes.map(_.id.value) mustBe Seq("DoIt")
      }
    }
  }
}
