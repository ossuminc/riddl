/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{Contents, Finder}
import com.ossuminc.riddl.language.bast.BASTReader
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.utils.{ec, pc}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** `require invariant X` and `when invariant X` must survive BAST.
  *
  * Written because the two path-identifier codecs are NOT interchangeable and the pairing is easy
  * to get wrong silently: `writePathIdentifier` emits a leading `NODE_PATH_IDENTIFIER` tag byte,
  * while `readPathIdentifierInline` starts at the location and never consumes one. Pair them across
  * a write/read and the stream misaligns AFTER the path — which surfaces far downstream as "Invalid
  * string table index" or a nonsense node, not as an error at the path itself.
  */
class RequireInvariantBASTTest extends AnyWordSpec with Matchers {

  private def roundTrip(src: String, origin: String): Module =
    TopLevelParser.parseInput(RiddlParserInput(src, origin)) match
      case Left(msgs) => fail(s"parse failed:\n${msgs.format}")
      case Right(root) =>
        val out = Pass
          .runThesePasses(PassInput(root), Seq(BASTWriterPass.creator()))
          .outputOf[BASTOutput](BASTWriterPass.name)
          .getOrElse(fail("BASTWriterPass produced no output"))
        BASTReader.read(out.bytes) match
          case Left(errs)    => fail(s"BAST read failed: ${errs.format}")
          case Right(module) => module

  "a require statement naming an invariant" should {
    "survive a BAST round trip with its path intact" in {
      val src =
        """domain dd is { context cc is {
          |  record Limits is { ceiling: Integer, used: Integer }
          |  command Poke is { x: Integer }
          |  invariant UnderLimit requires record Limits is used <= ceiling
          |  handler H is {
          |    on command Poke {
          |      require invariant UnderLimit with record Limits(ceiling = "10", used = "1")
          |    }
          |  }
          |}}
          |""".stripMargin
      val module = roundTrip(src, "require-bast")
      val reqs = Finder(module.contents).recursiveFindByType[RequireStatement]
      reqs.size mustBe 1
      reqs.head.condition mustBe a[InvariantRef]
      reqs.head.condition.asInstanceOf[InvariantRef].pathId.format mustBe "UnderLimit"
      reqs.head.argument.get mustBe a[Constructor]
    }
  }

  "an invariant named in a when condition" should {
    "survive a BAST round trip, bare and with an argument" in {
      val src =
        """domain dd is { context cc is {
          |  record R is { balance: Integer, floor: Integer }
          |  record Limits is { ceiling: Integer, used: Integer }
          |  event Rev is { x: Integer }
          |  entity E is {
          |    state S of record R
          |    invariant NonNeg is balance >= floor
          |    handler H is {
          |      on event Rev {
          |        when not invariant NonNeg then
          |          ???
          |        end
          |      }
          |    }
          |  }
          |}}
          |""".stripMargin
      val module = roundTrip(src, "when-bast")
      // The condition lives in a FIELD of WhenStatement (wrapped in a NotExpression here), not in
      // any container's contents, so `Finder` cannot reach it — it walks Containers.
      val whens = Finder(module.contents).recursiveFindByType[WhenStatement]
      whens.size mustBe 1
      val ic = whens.head.condition match
        case NotExpression(_, inner: InvariantCondition) => inner
        case other => fail(s"expected NotExpression(InvariantCondition), got: $other")
      ic.ref.pathId.format mustBe "NonNeg"
      ic.argument mustBe None
    }
  }
}
