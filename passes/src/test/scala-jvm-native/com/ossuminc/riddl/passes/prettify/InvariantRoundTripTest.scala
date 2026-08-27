/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.prettify

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{Finder, toSeq}
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.passes.validate.AbstractValidatingTest
import com.ossuminc.riddl.passes.{Pass, PassInput, PassesOutput}
import com.ossuminc.riddl.utils.pc

import org.scalatest.*

/** The 2026-08-04 invariant surface must round-trip: `requires`, the block condition, and the
  * `with <expr>` argument on a `require` statement.
  *
  * `requires` in particular is SEMANTIC — it decides where the invariant applies — so losing it in
  * prettify would silently widen a state-scoped rule to the whole entity while the output still
  * parsed and validated.
  */
class InvariantRoundTripTest extends AbstractValidatingTest {

  private def parse(src: String, origin: String): Root =
    TopLevelParser.parseInput(RiddlParserInput(src, origin)) match
      case Right(root) => root
      case Left(msgs)  => fail(s"parse of $origin failed:\n${msgs.format}")

  private def prettify(root: Root): String =
    Pass
      .runThesePasses(
        PassInput(root),
        Pass.standardPasses :+ { (in: PassInput, out: PassesOutput) =>
          PrettifyPass(in, out, PrettifyPass.Options(flatten = true, inputDir = ""))
        }
      )
      .outputs
      .outputOf[PrettifyOutput](PrettifyPass.name)
      .getOrElse(fail("PrettifyPass produced no output"))
      .state
      .filesAsString

  private val src =
    """domain dom is {
      |  context ctx is {
      |    record Balance is { amount: Integer, floor: Integer }
      |    record Limits is { ceiling: Integer, used: Integer }
      |    command Deposit is { by: Integer }
      |    entity Account is {
      |      state Open of record Balance
      |      state Frozen of record Balance
      |      invariant NonNegative is amount >= floor
      |      invariant OpenOnly requires state Open is amount >= floor
      |      invariant Blocky requires state Open is {
      |        let headroom = floor
      |        headroom <= amount
      |      }
      |      handler H is { on command Deposit { set field Account.Open.amount to "1" } }
      |    }
      |    invariant UnderLimit requires record Limits is used <= ceiling
      |    handler CtxRules is {
      |      on command Deposit {
      |        require invariant UnderLimit with record Limits(ceiling = "10", used = "1")
      |      }
      |    }
      |  }
      |}
      |""".stripMargin

  "the invariant surface" should {

    "keep `requires` through a prettify round trip" in { (td: TestData) =>
      val pretty = prettify(parse(src, "src"))
      pretty must include("requires state Open")
      pretty must include("requires record Limits")

      val invs = Finder(parse(pretty, "regen")).recursiveFindByType[Invariant]
      val byName = invs.map(i => i.id.value -> i).toMap

      // The un-scoped one stays un-scoped; the scoped ones keep their exact scope.
      byName("NonNegative").requires mustBe None
      byName("OpenOnly").requires.get mustBe a[StateRef]
      byName("UnderLimit").requires.get mustBe a[TypeRef]
    }

    "keep the block condition and its statements" in { (td: TestData) =>
      val pretty = prettify(parse(src, "src"))
      val blocky = Finder(parse(pretty, "regen"))
        .recursiveFindByType[Invariant]
        .find(_.id.value == "Blocky")
        .getOrElse(fail("Blocky did not survive the round trip"))
      blocky.condition.get mustBe a[InvariantBlock]
      val blk = blocky.condition.get.asInstanceOf[InvariantBlock]
      // The `let` is the whole reason the block form exists; losing it would leave a predicate
      // referring to a name nothing binds.
      blk.statements.toSeq.collect { case l: LetStatement => l.identifier.value } mustBe Seq(
        "headroom"
      )
    }

    "keep the `with <expr>` argument on a require statement" in { (td: TestData) =>
      val pretty = prettify(parse(src, "src"))
      pretty must include("require invariant UnderLimit with record Limits")

      val requires = Finder(parse(pretty, "regen"))
        .recursiveFindByType[RequireStatement]
        .filter(_.condition.isInstanceOf[InvariantRef])
      requires.size mustBe 1
      requires.head.argument.get mustBe a[Constructor]
    }

    "give a metadata-less invariant its own line" in { (td: TestData) =>
      // Regression: `doInvariant` did not terminate its line, so a metadata-less invariant ran
      // into whatever followed it — `invariant X is a >= b      handler H is {` on one line. It
      // still re-parsed, which is why it went unnoticed.
      val pretty = prettify(parse(src, "src"))
      val offenders = pretty.linesIterator.filter { l =>
        l.contains("invariant ") && l.contains("handler ")
      }.toSeq
      offenders mustBe empty
    }
  }
}
