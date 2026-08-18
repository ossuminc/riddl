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
  * `parents.collectFirst { case t: Type => t }`, and a `tell` statement's parents are its
  * on-clause, handler, processor, context, domain — never a `Type`. The guard could not succeed, so
  * a public output field documented as "map from each type to types it references" answered
  * "nothing references anything" for every model ever analyzed.
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

    /* [4.2]=A, RULED 2026-08-18: `typeDeps` is PURELY STRUCTURAL. A `tell` records a message-flow
     * edge, which `MessageFlowPass` already answers properly — and carrying it here made two
     * processors telling each other's messages look like a type cycle, so cycle detection reported
     * a healthy protocol as a defect. These two cases asserted that edge and now assert its
     * ABSENCE, which is the guarantee consumers actually need. */
    "record NO type dependency for a tell — that is MessageFlowPass's question" in {
      (td: TestData) =>
        runDependencyPass(crossContextTell) { out =>
          val deps = out.typeDeps.map { case (k, v) => k.id.value -> v.map(_.id.value) }
          deps.get("PlaceOrder") mustBe None
          // The context and entity dependencies a tell creates are unaffected — only the TYPE
          // edge was wrong, and dropping it must not take the other two with it.
          out.contextDeps must not be empty
          out.entityDeps must not be empty
        }
    }

    "keep a message-flow cycle OUT of typeDeps, so a cycle there means a real type loop" in {
      (td: TestData) =>
        runDependencyPass(
          """domain D is {
            |  context C is {
            |    command Ping is { id: String }
            |    command Pong is { id: String }
            |    entity A is {
            |      handler H is {
            |        on command D.C.Ping is { tell command D.C.Pong(id = "x") to entity D.C.B }
            |      }
            |    }
            |    entity B is {
            |      handler H is {
            |        on command D.C.Pong is { tell command D.C.Ping(id = "y") to entity D.C.A }
            |      }
            |    }
            |  }
            |}
            |""".stripMargin
        ) { out =>
          // Ping -> Pong -> Ping is a perfectly good protocol and must not appear as a type loop.
          val deps = out.typeDeps.map { case (k, v) => k.id.value -> v.map(_.id.value) }
          deps.get("Ping") mustBe None
          deps.get("Pong") mustBe None
        }
    }

    /* [4.2], RULED 2026-08-17 by Reid, in his own example: *"if a record references a set that has
     * a value that references a named integer type then record->set->named-integer-type must be
     * represented in that map"*. The edges are DIRECT, so a consumer walks the chain — which is
     * what makes both cycle detection and hierarchy traversal possible. */
    "record the chain record -> set -> named integer type" in { (td: TestData) =>
      runDependencyPass(
        """domain D is {
          |  context C is {
          |    type Count is Integer
          |    type Counters is set of D.C.Count
          |    record Tally is { counters: D.C.Counters }
          |  }
          |}
          |""".stripMargin
      ) { out =>
        val deps = out.typeDeps.map { case (k, v) => k.id.value -> v.map(_.id.value) }
        // The record depends on the set it names -- recorded against the FIELD by the resolver,
        // and folded up to the owning type here. This is the half that answers nothing without
        // the field walk.
        deps.get("Tally") mustBe Some(scala.collection.immutable.Set("Counters"))
        // ...and the set depends on the named integer type it holds.
        deps.get("Counters") mustBe Some(scala.collection.immutable.Set("Count"))
      }
    }

    "walk through cardinality and collection wrappers, and through nested aggregates" in {
      (td: TestData) =>
        runDependencyPass(
          """domain D is {
            |  context C is {
            |    type Name is String
            |    type Age is Integer
            |    record Inner is { name: D.C.Name }
            |    record Outer is {
            |      maybe: D.C.Age?,
            |      many: many D.C.Name,
            |      nested: { deep: D.C.Age }
            |    }
            |  }
            |}
            |""".stripMargin
        ) { out =>
          val deps = out.typeDeps.map { case (k, v) => k.id.value -> v.map(_.id.value) }
          deps.get("Inner") mustBe Some(scala.collection.immutable.Set("Name"))
          // `?` and `many` are cardinality wrappers, and `nested` is an inline aggregate whose
          // field sits a level down -- all three must still reach the owning record.
          deps.get("Outer") mustBe Some(scala.collection.immutable.Set("Age", "Name"))
        }
    }

    /* A recursive type is legal and must NOT appear as a one-node cycle, or every consumer looking
     * for loops finds a false one in every model that has a tree in it. */
    "not record a type as its own dependency" in { (td: TestData) =>
      runDependencyPass(
        """domain D is {
          |  context C is {
          |    record Node is { children: many D.C.Node }
          |  }
          |}
          |""".stripMargin
      ) { out =>
        val deps = out.typeDeps.map { case (k, v) => k.id.value -> v.map(_.id.value) }
        deps.get("Node") mustBe None
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
