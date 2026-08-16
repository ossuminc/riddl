/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{Contents, *}
import com.ossuminc.riddl.language.bast.BASTReader
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.utils.pc
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.must.Matchers

/** Task 6 (processor-instance identity): `tell ... by <field>` adds an optional trailing
  * [[com.ossuminc.riddl.language.AST.Identifier]] to [[com.ossuminc.riddl.language.AST.TellStatement]]
  * (BAST statement sub-kind 9, unchanged -- `by` is appended after the existing fields, per
  * `FORMAT_REVISION` 15's note), so it needs its own targeted reflectivity proof. `tell` is a bare
  * statement, so [[com.ossuminc.riddl.language.Finder]]'s `recursiveFindByType` finds it directly.
  *
  * JVM-only, like `BASTRoundTripTest` itself (BAST I/O has no Native-friendly harness in this test
  * suite). The PRETTIFY round trip is a separate, cross-platform concern -- see
  * `passes/src/test/scala-jvm-native/.../prettify/TellByRoundTripTest.scala`.
  */
class TellByBASTRoundTripTest extends AnyWordSpec with Matchers {

  private val src =
    """domain Dom is {
      |  context Ctx is {
      |    command Ship is { fromOrder: Id(entity Order), toOrder: Id(entity Order) } with { briefly "s" }
      |    record R is { total: String } with { briefly "r" }
      |    entity Order is {
      |      state S of record R is {
      |        handler OH is { on command Ship { do "ship" } } with { briefly "oh" }
      |      } with { briefly "s" }
      |    } with { briefly "e" }
      |    entity Caller is {
      |      state CS of record R is {
      |        handler CH is {
      |          on init {
      |            tell command Ship(fromOrder = f, toOrder = t) to entity Order by toOrder
      |          }
      |        } with { briefly "ch" }
      |      } with { briefly "cs" }
      |    } with { briefly "ce" }
      |  } with { briefly "c" }
      |} with { briefly "d" }
      |""".stripMargin

  private def parse(text: String, origin: String): Root =
    TopLevelParser.parseInput(RiddlParserInput(text, origin)) match
      case Right(root) => root
      case Left(msgs)  => fail(s"parse of $origin failed:\n${msgs.format}")

  private def tellIn(root: Root): TellStatement =
    Finder(root).recursiveFindByType[TellStatement].headOption
      .getOrElse(fail("no TellStatement found"))

  "tell ... by" should {
    "round-trip through BAST (write sub-kind 9 with the appended 'by', read it back)" in {
      val original = parse(src, "src")
      val originalTell = tellIn(original)
      originalTell.by.map(_.value) mustBe Some("toOrder")

      val writerResult =
        Pass.runThesePasses(PassInput(original), Seq(BASTWriterPass.creator()))
      val output = writerResult.outputOf[BASTOutput](BASTWriterPass.name).getOrElse {
        fail("BASTWriterPass produced no output")
      }

      BASTReader.read(output.bytes) match
        case Right(module) =>
          val reconstructedTell = Finder(module).recursiveFindByType[TellStatement].headOption
            .getOrElse(fail("no TellStatement found in reconstructed module"))

          reconstructedTell.processorRef.pathId.format mustBe originalTell.processorRef.pathId.format
          reconstructedTell.by.map(_.value) mustBe originalTell.by.map(_.value)
        case Left(errors) =>
          fail(s"BAST deserialization failed: ${errors.format}")
    }

    "round-trip the bare (no-'by') form as None" in {
      val bareSrc =
        """domain Dom is {
          |  context Ctx is {
          |    command Ship is { orderId: Id(entity Order) } with { briefly "s" }
          |    record R is { total: String } with { briefly "r" }
          |    entity Order is {
          |      state S of record R is {
          |        handler OH is { on command Ship { do "ship" } } with { briefly "oh" }
          |      } with { briefly "s" }
          |    } with { briefly "e" }
          |    entity Caller is {
          |      state CS of record R is {
          |        handler CH is {
          |          on init { tell command Ship(orderId = o) to entity Order }
          |        } with { briefly "ch" }
          |      } with { briefly "cs" }
          |    } with { briefly "ce" }
          |  } with { briefly "c" }
          |} with { briefly "d" }
          |""".stripMargin
      val original = parse(bareSrc, "bare")
      val writerResult =
        Pass.runThesePasses(PassInput(original), Seq(BASTWriterPass.creator()))
      val output = writerResult.outputOf[BASTOutput](BASTWriterPass.name).getOrElse {
        fail("BASTWriterPass produced no output")
      }
      BASTReader.read(output.bytes) match
        case Right(module) =>
          val reconstructedTell = Finder(module).recursiveFindByType[TellStatement].headOption
            .getOrElse(fail("no TellStatement found in reconstructed module"))
          reconstructedTell.by mustBe None
        case Left(errors) =>
          fail(s"BAST deserialization failed: ${errors.format}")
    }
  }
}
