/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.utils.{PlatformContext, pc}

import org.scalatest.*
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** `Pass.runThesePasses`'s `NonFatal` handler must NEVER emit an empty message.
  *
  * It is the last-resort reporter for every in-pass exception, so whatever it prints is all the
  * author gets. It used to print `ExceptionUtils.getRootCauseStackTrace(...).mkString("\n")` and
  * nothing else, and that can render to the EMPTY STRING -- which surfaces as a bare
  * `[severe] empty(1:1->1):` carrying no text, no source line and no location. It says only
  * "something threw somewhere".
  *
  * This has now cost real time twice. ossum.tech bisected a model by hand for about half an hour to
  * find a `ClassCastException` in ResolutionPass, and the note at `DefinitionValidation.scala:451`
  * records an earlier hunt where a regex throwing at class-initialisation under Scala Native
  * produced exactly `Message(empty(0->0), "", Severe, ...)`.
  *
  * The contract pinned here is deliberately weaker than "reports the right thing" and stronger than
  * "does not crash": whatever happens, the author is told SOMETHING identifiable.
  */
class PassExceptionReportingTest extends AnyWordSpec with Matchers {

  given io: PlatformContext = pc

  /** A pass that throws with NO stack trace, which is the condition that produced an empty message.
    * `fillInStackTrace` is overridden to return `this` without capturing frames.
    */
  private class ThrowingPass(in: PassInput, out: PassesOutput)(using PlatformContext)
      extends Pass(in, out) {
    override def name: String = "throwing"
    override protected def process(value: RiddlValue, parents: ParentStack): Unit =
      throw new RuntimeException("deliberate test failure") {
        override def fillInStackTrace(): Throwable = this
      }
    override def result(root: PassRoot): PassOutput = PassOutput.empty
  }

  private def rootOf(src: String): Root =
    TopLevelParser.parseInput(RiddlParserInput(src, "throwing-pass-test")) match
      case Right(root) => root
      case Left(msgs)  => fail(s"fixture did not parse:\n${msgs.format}")

  "the NonFatal handler in runThesePasses" should {

    "never produce a severe message with empty text" in {
      val root = rootOf("""domain D is { ??? } with { briefly "d" }""")
      val result = Pass.runThesePasses(
        PassInput(root),
        Seq((in: PassInput, out: PassesOutput) => ThrowingPass(in, out))
      )
      val severes = result.messages.filter(_.kind.isSevereError)
      withClue(s"expected a severe message, got:\n${result.messages.format}\n") {
        severes mustNot be(empty)
      }
      // THE assertion. An empty message is the defect; anything identifiable is a pass.
      severes.foreach { m =>
        withClue(s"severe message had empty text: $m\n") {
          m.message.trim mustNot be(empty)
        }
      }
    }

    "name the exception when there is no stack trace to show" in {
      val root = rootOf("""domain D is { ??? } with { briefly "d" }""")
      val result = Pass.runThesePasses(
        PassInput(root),
        Seq((in: PassInput, out: PassesOutput) => ThrowingPass(in, out))
      )
      val text = result.messages.filter(_.kind.isSevereError).map(_.message).mkString("\n")
      // Either the class name or the message it carried -- enough to start from.
      withClue(s"severe text was: '$text'\n") {
        (text.contains("RuntimeException") || text.contains("deliberate test failure")) mustBe true
      }
    }
  }
}
