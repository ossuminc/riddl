/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.utils.{CommonOptions, pc}
import org.scalatest.TestData

/** A32: the shape a processor's arity denotes, across the WHOLE arity space.
  *
  * `Processor.shapeForArity` must be TOTAL. It was not: `sink` was pinned to exactly one inlet and
  * `source` to exactly one outlet, which left `(0 outlets, >=2 inlets)` and `(>=2 outlets, 0
  * inlets)` — an ordinary fan-in drain and fan-out origin — with no shape at all. They fell to a
  * catch-all returning `Void`, so a correct model drew
  *
  * Repository 'MachineRegistry' is ascribed 'as sink' but its arity (0 outlets, 2 inlets) is void
  *
  * which is a confident wrong answer, not a near miss. Found 2026-08-12 in a synapify Domain Model
  * Wizard run, where it was the last remaining error and the repair loop would have "fixed" a valid
  * model to satisfy it. Reid widened both shapes: a SINK is any pure drain and a SOURCE any pure
  * origin, whatever the port count — which is what A31 already assumed when it said fan-in/out is
  * modelled by declaring multiple ports.
  *
  * **The controls are the point of this suite.** Every widened case is paired with a mismatched
  * ascription that must STILL be rejected, and with the exact-arity cases that already worked. A
  * change that simply stopped checking would pass every positive case here and fail every control.
  */
class ProcessorArityShapeTest extends AbstractValidatingTest {

  private def errorsFor(src: String, origin: String): String =
    var captured: Messages = Messages.empty
    pc.withOptions(CommonOptions(showStyleWarnings = true, showWarnings = true)) { _ =>
      parseAndValidate(src, origin, shouldFailOnErrors = false) { (_, _, messages) =>
        captured = messages
        succeed
      }
    }
    captured.justErrors.map(_.message).mkString("\n")

  /** A repository with `inlets` inlets and no outlets, ascribed `as $shape`. */
  private def repositoryWithInlets(inlets: Int, shape: String): String =
    val ports = (1 to inlets).map(n => s"      inlet in$n is command C$n").mkString("\n")
    val cmds = (1 to inlets)
      .map(n => s"""    command C$n is { f: String } with { briefly "c$n" }""")
      .mkString("\n")
    s"""domain D is {
       |  context C is {
       |$cmds
       |    repository R as $shape is {
       |$ports
       |      handler H is { on command C1 is { do "store it" } } with { briefly "h" }
       |    } with { briefly "r" }
       |  } with { briefly "c" }
       |} with { briefly "d" }
       |""".stripMargin

  /** A generic processor with `outlets` outlets and no inlets, ascribed `as $shape`. */
  private def processorWithOutlets(outlets: Int, shape: String): String =
    val ports = (1 to outlets).map(n => s"      outlet out$n is event E$n").mkString("\n")
    val evts = (1 to outlets)
      .map(n => s"""    event E$n is { f: String } with { briefly "e$n" }""")
      .mkString("\n")
    s"""domain D is {
       |  context C is {
       |$evts
       |    processor P as $shape is {
       |$ports
       |    } with { briefly "p" }
       |  } with { briefly "c" }
       |} with { briefly "d" }
       |""".stripMargin

  "a SINK" should {

    "accept two inlets and no outlets" in { (td: TestData) =>
      // The reported case, reduced: `repository MachineRegistry as sink` with two inlets.
      errorsFor(repositoryWithInlets(2, "sink"), "sink-two-inlets") must be("")
    }

    "accept five inlets and no outlets" in { (td: TestData) =>
      // Guards against a fix that special-cased 2 rather than making the arm general.
      errorsFor(repositoryWithInlets(5, "sink"), "sink-five-inlets") must be("")
    }

    "still accept exactly one inlet" in { (td: TestData) =>
      errorsFor(repositoryWithInlets(1, "sink"), "sink-one-inlet") must be("")
    }

    "CONTROL: reject a fan-in drain ascribed 'as flow'" in { (td: TestData) =>
      // Widening must not become "stop checking". (0 outlets, 2 inlets) is a sink, so `as flow`
      // is still wrong -- and the message must now say `sink`, never the old `void`.
      val errors = errorsFor(repositoryWithInlets(2, "flow"), "fanin-as-flow")
      errors must include("is ascribed 'as flow'")
      errors must include("is sink")
      errors must not(include("is void"))
    }
  }

  "a SOURCE" should {

    "accept two outlets and no inlets" in { (td: TestData) =>
      errorsFor(processorWithOutlets(2, "source"), "source-two-outlets") must be("")
    }

    "still accept exactly one outlet" in { (td: TestData) =>
      errorsFor(processorWithOutlets(1, "source"), "source-one-outlet") must be("")
    }

    "CONTROL: reject a fan-out origin ascribed 'as split'" in { (td: TestData) =>
      // A split needs an INLET; (2 outlets, 0 inlets) is a source.
      val errors = errorsFor(processorWithOutlets(2, "split"), "fanout-as-split")
      errors must include("is ascribed 'as split'")
      errors must include("is source")
      errors must not(include("is void"))
    }
  }

  "the streamlet keyword forms" should {

    "accept `sink` with two inlets, which used to be a PARSE error" in { (td: TestData) =>
      // The parser pinned `sink` to maxInlets = 1, so this failed before reaching validation at
      // all. Both surfaces have to agree, or `repository R as sink` and `sink R` disagree about
      // what a sink is.
      val src =
        """domain D is {
          |  context C is {
          |    command A is { a: String } with { briefly "c" }
          |    command B is { b: String } with { briefly "c2" }
          |    sink Drain is {
          |      inlet one is command A
          |      inlet two is command B
          |    } with { briefly "s" }
          |  } with { briefly "c" }
          |} with { briefly "d" }
          |""".stripMargin
      errorsFor(src, "sink-keyword-two-inlets") must be("")
    }

    "accept `source` with two outlets, likewise" in { (td: TestData) =>
      val src =
        """domain D is {
          |  context C is {
          |    event A is { a: String } with { briefly "e" }
          |    event B is { b: String } with { briefly "e2" }
          |    source Feed is {
          |      outlet one is event A
          |      outlet two is event B
          |    } with { briefly "s" }
          |  } with { briefly "c" }
          |} with { briefly "d" }
          |""".stripMargin
      errorsFor(src, "source-keyword-two-outlets") must be("")
    }
  }

  "the fan-in / fan-out BOUNDARY" should {

    // Widening `sink` and `source` to any port count must not weaken the rule that makes them
    // sinks and sources at all: a sink has NO outlets, a source has NO inlets. Those assertions
    // live in `validateStreamlet` -- a THIRD encoding of the arity rules, separate from
    // `shapeForArity` and the parser's per-shape limits -- and were not covered when the widening
    // landed. Reid asked for them by name.

    "reject a sink that also has an outlet" in { (td: TestData) =>
      val src =
        """domain D is {
          |  context C is {
          |    command A is { a: String } with { briefly "c" }
          |    event E is { e: String } with { briefly "e" }
          |    sink Drain is {
          |      inlet one is command A
          |      inlet two is command A
          |      outlet leak is event E
          |    } with { briefly "s" }
          |  } with { briefly "c" }
          |} with { briefly "d" }
          |""".stripMargin
      val errors = errorsFor(src, "sink-with-outlet")
      // The boundary rule itself, from validateStreamlet.
      errors must include("is a sink but has 1 outlets; sinks must have none")
      // And the ascription check agrees independently: (1 outlet, 2 inlets) is a merge.
      errors must include("is merge")
    }

    "reject a source that also has an inlet" in { (td: TestData) =>
      val src =
        """domain D is {
          |  context C is {
          |    event A is { a: String } with { briefly "e" }
          |    command B is { b: String } with { briefly "c" }
          |    source Feed is {
          |      outlet one is event A
          |      outlet two is event A
          |      inlet back is command B
          |    } with { briefly "s" }
          |  } with { briefly "c" }
          |} with { briefly "d" }
          |""".stripMargin
      val errors = errorsFor(src, "source-with-inlet")
      errors must include("sources must have none")
    }
  }

  "the rest of the arity space" should {

    "still derive flow, merge, split and router unchanged" in { (td: TestData) =>
      // The widened arms are ordered before these, so this asserts they did not swallow them:
      // (1,1) flow, (1,2) merge, (2,1) split, (2,2) router.
      val src =
        """domain D is {
          |  context C is {
          |    event A is { a: String } with { briefly "e" }
          |    event B is { b: String } with { briefly "e2" }
          |    flow F is {
          |      inlet i1 is event A
          |      outlet o1 is event B
          |    } with { briefly "f" }
          |    merge M is {
          |      inlet i1 is event A
          |      inlet i2 is event B
          |      outlet o1 is event A
          |    } with { briefly "m" }
          |    split S is {
          |      inlet i1 is event A
          |      outlet o1 is event A
          |      outlet o2 is event B
          |    } with { briefly "sp" }
          |    router R is {
          |      inlet i1 is event A
          |      inlet i2 is event B
          |      outlet o1 is event A
          |      outlet o2 is event B
          |    } with { briefly "rt" }
          |  } with { briefly "c" }
          |} with { briefly "d" }
          |""".stripMargin
      errorsFor(src, "other-arities-unchanged") must be("")
    }
  }
}
