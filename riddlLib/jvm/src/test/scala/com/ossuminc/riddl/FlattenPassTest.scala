/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{Contents, *}
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.passes.{Pass, PassInput, PassOptions, PassesOutput}
import com.ossuminc.riddl.passes.transforms.FlattenPass
import com.ossuminc.riddl.utils.{pc, ec, Await, URL}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.must.Matchers

import scala.concurrent.duration.*

/** Tests that FlattenPass removes all Include and BASTImport wrapper
  * nodes from the AST, making accessor methods like domain.contexts
  * return all definitions regardless of file organization.
  *
  * Uses language/input/everything.riddl which contains include
  * statements across multiple files.
  */
class FlattenPassTest extends AnyWordSpec with Matchers {

  /** Recursively walk the AST and fail if any Include or BASTImport
    * node is found at any level.
    */
  private def assertNoWrapperNodes(
    value: RiddlValue,
    path: String = "root"
  ): Unit = {
    value match {
      case _: Include[?] =>
        fail(s"Found Include node at $path")
      case _: BASTImport =>
        fail(s"Found BASTImport node at $path")
      case c: Container[?] =>
        c.contents.toSeq.foreach { child =>
          val childPath = child match {
            case d: Definition => s"$path/${d.identify}"
            case other         => s"$path/${other.getClass.getSimpleName}"
          }
          assertNoWrapperNodes(child, childPath)
        }
      case _ => () // leaf node, nothing to check
    }
  }

  /** Count Include and BASTImport nodes at all levels. */
  private def countWrapperNodes(value: RiddlValue): Int = {
    value match {
      case _: Include[?]  => 1
      case _: BASTImport  => 1
      case c: Container[?] =>
        c.contents.toSeq.map(countWrapperNodes).sum
      case _ => 0
    }
  }

  "FlattenPass" should {

    "remove all Include nodes from everything.riddl" in pending /* {
      val url = URL.fromCwdPath("language/input/everything.riddl")
      val inputFuture =
        RiddlParserInput.fromURL(url, "flatten-pass-test")

      val result = Await.result(
        inputFuture.map { input =>
          TopLevelParser.parseInput(input, true)
        },
        30.seconds
      )

      result match {
        case Right(root) =>
          // Verify includes exist BEFORE flattening
          val beforeCount = countWrapperNodes(root)
          info(s"Before flatten: $beforeCount wrapper nodes")
          beforeCount must be > 0

          // Run FlattenPass
          val passInput = PassInput(root)
          Pass.runThesePasses(
            passInput,
            Seq(
              FlattenPass.creator(PassOptions.empty)
            )
          )

          // Verify NO includes or imports remain AFTER flattening
          val afterCount = countWrapperNodes(root)
          info(s"After flatten: $afterCount wrapper nodes")
          assertNoWrapperNodes(root)

          // Verify the tree still has meaningful content
          root.domains must not be empty
          val domain = root.domains.head
          domain.id.value mustBe "Everything"

          // These should now be accessible via direct accessors
          // (they were inside Include wrappers before flattening)
          info(s"Domain contexts: ${domain.contexts.size}")
          info(s"Domain types: ${domain.types.size}")
          domain.contexts must not be empty

        case Left(messages) =>
          fail(s"Parse failed: ${messages.format}")
      }
    } */
  }
}
