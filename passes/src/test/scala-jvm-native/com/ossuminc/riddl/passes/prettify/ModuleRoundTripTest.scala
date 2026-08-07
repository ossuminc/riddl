/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.prettify

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.passes.validate.AbstractValidatingTest
import com.ossuminc.riddl.passes.{Pass, PassInput, PassesOutput}
import com.ossuminc.riddl.utils.pc

import org.scalatest.*

/** S61-1: a `Module` is a FLAT collection of ANY top-level definition. RIDDL is reflective, so the
  * `module <id> is { ... }` wrapper must not only parse and validate but also EMIT and re-parse to
  * the same shape — with every member still inside the module, not relocated or dropped.
  *
  * This closes the sleeper gap: `Pass.openContainer` had no Module case, so `PrettifyVisitor` never
  * saw the wrapper and never emitted it.
  */
class ModuleRoundTripTest extends AbstractValidatingTest {

  private val mixedModule: String =
    """module M is {
      |  author Reid is { name is "Reid Spencer" email is "reid@ossuminc.com" }
      |  type Amount is Number
      |  constant Limit is Number = "100"
      |  user Shopper is "a person who buys things"
      |  invariant Positive is "the limit is positive"
      |  function Compute is { ??? }
      |  context Ordering is {
      |    event Placed is { when: TimeStamp }
      |  }
      |  entity Loose is { handler Anything is { ??? } }
      |  adaptor FromOrdering from context Ordering is { ??? }
      |  projector Totals is {
      |    record Snapshot is { total: Number }
      |    handler Updates is { ??? }
      |  }
      |  repository Ledger is { ??? }
      |  saga Checkout is {
      |    step ReserveStock is {
      |      do "reserve"
      |    } reverted by {
      |      do "release"
      |    }
      |    step ChargeCard is {
      |      do "charge"
      |    } reverted by {
      |      do "refund"
      |    }
      |  }
      |  epic Buying is {
      |    user Shopper wants to "buy something" so that "they own it"
      |    type Cart is String
      |  }
      |  domain Retail is { context Store is { ??? } }
      |  module Nested is { type Inner is String }
      |}
      |""".stripMargin

  private def parse(src: String, origin: String): Root =
    TopLevelParser.parseInput(RiddlParserInput(src, origin)) match
      case Right(root) => root
      case Left(msgs)  => fail(s"parse of $origin failed:\n${msgs.format}")

  /** Run the prettifier (flatten) over a Root and return the rendered source. */
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

  /** Every member name the module is expected to carry, keyed by its accessor. */
  private def census(m: Module): Map[String, Seq[String]] = Map(
    "authors" -> m.authors.map(_.id.value),
    "types" -> m.types.map(_.id.value),
    "constants" -> m.constants.map(_.id.value),
    "users" -> m.users.map(_.id.value),
    "invariants" -> m.invariants.map(_.id.value),
    "functions" -> m.functions.map(_.id.value),
    "contexts" -> m.contexts.map(_.id.value),
    "entities" -> m.entities.map(_.id.value),
    "adaptors" -> m.adaptors.map(_.id.value),
    "projectors" -> m.projectors.map(_.id.value),
    "repositories" -> m.repositories.map(_.id.value),
    "sagas" -> m.sagas.map(_.id.value),
    "epics" -> m.epics.map(_.id.value),
    "domains" -> m.domains.map(_.id.value),
    "modules" -> m.modules.map(_.id.value)
  )

  "A mixed-contents Module" should {

    "validate without spurious errors" in { (td: TestData) =>
      val result = Pass.runThesePasses(
        PassInput(parse(mixedModule, td.name)),
        Pass.standardPasses
      )
      val errors = result.messages.filter(_.kind.isError)
      errors mustBe empty
    }

    "round-trip through prettify with every member still inside the module" in { (td: TestData) =>
      val root1 = parse(mixedModule, td.name)
      val module1 = root1.modules.headOption.getOrElse(fail("no module parsed"))

      val pretty = prettify(root1)
      // The wrapper itself must be emitted — this is what was missing before S61-1.
      pretty must include("module M is")
      pretty must include("module Nested is")

      val root2 = parse(pretty, "regen")
      val module2 = root2.modules.headOption.getOrElse(fail("module lost in round trip"))
      module2.id.value mustBe "M"

      // Nothing dropped, nothing relocated out of the module.
      census(module2) mustBe census(module1)
      // The definitions did NOT leak up to Root level.
      root2.domains mustBe empty
    }
  }

  "The deprecated anonymous nebula" should {

    "yield a Module carrying exactly one deprecation message" in { (td: TestData) =>
      val input = RiddlParserInput(
        """type Foo is Integer
          |entity Bar is { ??? }
          |""".stripMargin,
        td.name
      )
      val tlp = TopLevelParser(input, false)
      tlp.parseNebula match
        case Left(msgs) => fail(msgs.format)
        case Right(module) =>
          Module.isSynthetic(module) mustBe true
          tlp.accumulatedMessages.count(_.kind == Messages.Deprecation) mustBe 1
    }
  }
}
