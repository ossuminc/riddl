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

/** Task 5 (processor-instance identity): `terminate` is a new [[com.ossuminc.riddl.language.AST.Statement]]
  * (BAST statement sub-kind 20), so it needs its own targeted reflectivity proof rather than
  * relying on the coarse domain/context/entity-level [[DeepASTComparison]] (which does not
  * descend into statement-level nodes). `terminate` is a bare statement, so
  * [[com.ossuminc.riddl.language.Finder]]'s `recursiveFindByType` DOES find it directly (unlike
  * `initiate`, a value typically wrapped in a `let`) -- mirrors `InitiateBASTRoundTripTest`'s
  * style otherwise.
  *
  * JVM-only, like `BASTRoundTripTest` itself (BAST I/O has no Native-friendly harness in this test
  * suite). The PRETTIFY round trip is a separate, cross-platform concern -- see
  * `passes/src/test/scala-jvm-native/.../prettify/TerminateRoundTripTest.scala`.
  */
class TerminateBASTRoundTripTest extends AnyWordSpec with Matchers {

  private val src =
    """domain Dom is {
      |  context Ctx is {
      |    record R is { total: String } with { briefly "r" }
      |    entity Order is {
      |      state S of record R is {
      |        handler H is {
      |          on init { do "start" }
      |          on term(oid: Id(entity Order)) { do "end" }
      |        } with { briefly "h" }
      |      } with { briefly "s" }
      |    } with { briefly "e" }
      |    entity Caller is {
      |      state CS of record R is {
      |        handler CH is {
      |          on init {
      |            let oid = initiate entity Order
      |            terminate entity Order(oid)
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

  private def terminateIn(root: Root): TerminateStatement =
    Finder(root).recursiveFindByType[TerminateStatement].headOption
      .getOrElse(fail("no TerminateStatement found"))

  "terminate" should {
    "round-trip through BAST (write sub-kind 20, read it back)" in {
      val original = parse(src, "src")
      val originalTerm = terminateIn(original)

      val writerResult =
        Pass.runThesePasses(PassInput(original), Seq(BASTWriterPass.creator()))
      val output = writerResult.outputOf[BASTOutput](BASTWriterPass.name).getOrElse {
        fail("BASTWriterPass produced no output")
      }

      BASTReader.read(output.bytes) match
        case Right(module) =>
          val reconstructedTerm = Finder(module).recursiveFindByType[TerminateStatement].headOption
            .getOrElse(fail("no TerminateStatement found in reconstructed module"))

          reconstructedTerm.processor.pathId.format mustBe originalTerm.processor.pathId.format
          reconstructedTerm.args.size mustBe originalTerm.args.size
          reconstructedTerm.args.map(_.value.format) mustBe originalTerm.args.map(_.value.format)
        case Left(errors) =>
          fail(s"BAST deserialization failed: ${errors.format}")
    }
  }
}
