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

/** Task 4 (processor-instance identity): `initiate` is a new
  * [[com.ossuminc.riddl.language.AST.Value]] (BAST tag 8), so it needs its own targeted
  * reflectivity proof rather than relying on the coarse domain/context/entity-level
  * [[DeepASTComparison]] (which does not descend into statement-level Value nodes) or
  * [[com.ossuminc.riddl.language.Finder]] (whose `recursiveFindByType` does not descend into a
  * `LetStatement`'s `expression` field). Walked to the `Initiate` node directly and compared
  * field-by-field, mirroring `BASTRoundTripTest`'s style.
  *
  * JVM-only, like `BASTRoundTripTest` itself (BAST I/O has no Native-friendly harness in this test
  * suite). The PRETTIFY round trip is a separate, cross-platform concern -- see
  * `passes/src/test/scala-jvm-native/.../prettify/InitiateRoundTripTest.scala`.
  */
class InitiateBASTRoundTripTest extends AnyWordSpec with Matchers {

  private val src =
    """domain Dom is {
      |  context Ctx is {
      |    record R is { total: String } with { briefly "r" }
      |    entity Order is {
      |      state S of record R is {
      |        handler H is {
      |          on init(total: String) { do "start" }
      |        } with { briefly "h" }
      |      } with { briefly "s" }
      |    } with { briefly "e" }
      |    entity Caller is {
      |      state CS of record R is {
      |        handler CH is {
      |          on init { let oid = initiate entity Order("5") }
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

  /** Walk down to the `Caller` entity's `on init` clause and pull out its single `let`'s `Initiate`
    * expression -- mirrors `InitiateFileTest`'s direct walk, for the same reason (Finder does not
    * descend into LetStatement).
    */
  private def initiateIn(root: Root): Initiate =
    val domain =
      root.contents.toSeq.collectFirst { case d: Domain => d }.getOrElse(fail("no domain"))
    val context =
      domain.contents.toSeq.collectFirst { case c: Context => c }.getOrElse(fail("no context"))
    val caller = context.contents.toSeq
      .collectFirst { case e: Entity if e.id.value == "Caller" => e }
      .getOrElse(fail("no Caller entity"))
    val state =
      caller.contents.toSeq.collectFirst { case s: State => s }.getOrElse(fail("no state"))
    val handler =
      state.contents.toSeq.collectFirst { case h: Handler => h }.getOrElse(fail("no handler"))
    val onInit = handler.clauses
      .collectFirst { case oic: OnInitializationClause => oic }
      .getOrElse(fail("no on-init clause"))
    val let = onInit.contents.toSeq
      .collectFirst { case ls: LetStatement => ls }
      .getOrElse(fail("no let statement"))
    let.expression match
      case init: Initiate => init
      case other          => fail(s"expected an Initiate, got $other")

  "initiate" should {
    "round-trip through BAST (write tag 8, read it back)" in {
      val original = parse(src, "src")
      val originalInit = initiateIn(original)

      val writerResult =
        Pass.runThesePasses(PassInput(original), Seq(BASTWriterPass.creator()))
      val output = writerResult.outputOf[BASTOutput](BASTWriterPass.name).getOrElse {
        fail("BASTWriterPass produced no output")
      }

      BASTReader.read(output.bytes) match
        case Right(module) =>
          val domain =
            module.contents.toSeq.collectFirst { case d: Domain => d }.getOrElse(fail("no domain"))
          val context = domain.contents.toSeq
            .collectFirst { case c: Context => c }
            .getOrElse(fail("no context"))
          val caller = context.contents.toSeq
            .collectFirst { case e: Entity if e.id.value == "Caller" => e }
            .getOrElse(fail("no Caller entity"))
          val state =
            caller.contents.toSeq.collectFirst { case s: State => s }.getOrElse(fail("no state"))
          val handler =
            state.contents.toSeq.collectFirst { case h: Handler => h }.getOrElse(fail("no handler"))
          val onInit = handler.clauses
            .collectFirst { case oic: OnInitializationClause => oic }
            .getOrElse(fail("no on-init clause"))
          val let = onInit.contents.toSeq
            .collectFirst { case ls: LetStatement => ls }
            .getOrElse(fail("no let statement"))
          val reconstructedInit = let.expression match
            case init: Initiate => init
            case other          => fail(s"expected an Initiate, got $other")

          reconstructedInit.processor.pathId.format mustBe originalInit.processor.pathId.format
          reconstructedInit.args.size mustBe originalInit.args.size
          reconstructedInit.args.map(_.value.format) mustBe originalInit.args.map(_.value.format)
        case Left(errors) =>
          fail(s"BAST deserialization failed: ${errors.format}")
    }
  }
}
