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

/** Task 2 (processor-instance identity): `self` is a [[com.ossuminc.riddl.language.AST.Value]]
  * (BAST tag 9), and was the only construct this branch adds with NO round-trip test on any
  * surface. Mirrors `InitiateBASTRoundTripTest` (tag 8) exactly.
  *
  * The bare `self` and the field form `self.<name>` differ only in an `Option[Identifier]`, which
  * is precisely the shape a codec can drop silently -- so both are written and both are compared.
  *
  * JVM-only, like `BASTRoundTripTest` itself (BAST I/O has no Native-friendly harness in this test
  * suite). The PRETTIFY round trip is a separate, cross-platform concern -- see
  * `passes/src/test/scala-jvm-native/.../prettify/SelfRoundTripTest.scala`.
  */
class SelfBASTRoundTripTest extends AnyWordSpec with Matchers {

  private val src =
    """domain Dom is {
      |  context Ctx is {
      |    command Go is { why: String } with { briefly "c" }
      |    record R is { total: Integer } with { briefly "r" }
      |    entity Order is {
      |      state S of record R is {
      |        handler H is {
      |          on command Go {
      |            let me = self
      |            let mine = self.id
      |            let v = self.version
      |          }
      |        } with { briefly "h" }
      |      } with { briefly "s" }
      |    } with { briefly "e" }
      |  } with { briefly "c" }
      |} with { briefly "d" }
      |""".stripMargin

  private def parse(text: String, origin: String): Root =
    TopLevelParser.parseInput(RiddlParserInput(text, origin)) match
      case Right(root) => root
      case Left(msgs)  => fail(s"parse of $origin failed:\n${msgs.format}")

  /** Walk to the on-clause's `let`s and collect their `SelfValue` expressions -- `Finder` does not
    * descend into a `LetStatement`'s `expression` field.
    */
  private def selvesIn(container: Container[?]): Seq[SelfValue] =
    val domain =
      container.contents.toSeq.collectFirst { case d: Domain => d }.getOrElse(fail("no domain"))
    val context =
      domain.contents.toSeq.collectFirst { case c: Context => c }.getOrElse(fail("no context"))
    val entity =
      context.contents.toSeq.collectFirst { case e: Entity => e }.getOrElse(fail("no entity"))
    val state =
      entity.contents.toSeq.collectFirst { case s: State => s }.getOrElse(fail("no state"))
    val handler =
      state.contents.toSeq.collectFirst { case h: Handler => h }.getOrElse(fail("no handler"))
    val clause = handler.clauses.headOption.getOrElse(fail("no on-clause"))
    clause.contents.toSeq.collect { case ls: LetStatement => ls }.collect {
      case ls if ls.expression.isInstanceOf[SelfValue] => ls.expression.asInstanceOf[SelfValue]
    }

  "self" should {
    "round-trip through BAST (write tag 9, read it back)" in {
      val original = parse(src, "src")
      val originalSelves = selvesIn(original)
      originalSelves.map(_.field.map(_.value)) mustBe Seq(None, Some("id"), Some("version"))

      val writerResult = Pass.runThesePasses(PassInput(original), Seq(BASTWriterPass.creator()))
      val output = writerResult.outputOf[BASTOutput](BASTWriterPass.name).getOrElse {
        fail("BASTWriterPass produced no output")
      }

      BASTReader.read(output.bytes) match
        case Right(module) =>
          selvesIn(module).map(_.field.map(_.value)) mustBe
            originalSelves.map(_.field.map(_.value))
        case Left(errors) =>
          fail(s"BAST deserialization failed: ${errors.format}")
    }
  }
}
