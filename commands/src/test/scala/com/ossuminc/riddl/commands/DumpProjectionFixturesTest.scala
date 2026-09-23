/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands

import com.ossuminc.riddl.commands.project.{ProjectionOutput, ProjectionPass}
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.passes.{Pass, PassInput, PassesOutput, Riddl}
import com.ossuminc.riddl.utils.{CommonOptions, pc}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.File
import scala.io.Source
import scala.util.control.NonFatal

/** **`ProjectionPass.addStatementFacts` is total BY HAND and `commands` compiles with
  * `--no-warnings`, so nothing in the build tells you when a new statement kind is missing an
  * arm.** On 2026-09-23 riddl-models found `dump --json` throwing a `MatchError` on B2's `store`
  * and emitting a zero-byte document while `riddlc validate` on the same model reported nothing
  * at all; B7's `log` and B2's other three were missing too. The statements' own JSON test
  * passed the whole time, because `JsonifierPass` is a SECOND writer and it did get its arms --
  * the "a dispatch written twice tells you nothing about its other copy" trap.
  *
  * This is the structural guard rather than five more arms' worth of assertions: the reflectivity
  * rule already requires a `.riddl` fixture for every new construct, so sweeping every fixture
  * through the projection means the NEXT missing arm reddens here instead of reaching a consumer.
  * A fixture that does not parse is skipped (many are include fragments or deliberately broken);
  * a fixture that parses and then throws is a failure.
  */
class DumpProjectionFixturesTest extends AnyWordSpec with Matchers {

  private val fixtureDirs: Seq[String] =
    Seq("language/input", "passes/input", "riddlc/input", "commands/input")

  private def riddlFiles(dir: String): Seq[File] =
    def walk(f: File): Seq[File] =
      if f.isDirectory then Option(f.listFiles).map(_.toSeq).getOrElse(Nil).flatMap(walk)
      else if f.getName.endsWith(".riddl") then Seq(f)
      else Nil
    val root = new File(dir)
    if root.isDirectory then walk(root).sortBy(_.getPath) else Nil

  private def read(f: File): String =
    val s = Source.fromFile(f)
    try s.mkString
    finally s.close()

  /** Well under what projects today, far above zero: a vacuous green is the failure this guards. */
  private val ProjectedFloor: Int = 40

  "the dump projection" should {

    "project every fixture that parses, throwing on none of them" in {
      val files = fixtureDirs.flatMap(riddlFiles)
      files.size must be > ProjectedFloor
      var projected = 0
      var records = 0
      val broken = scala.collection.mutable.ListBuffer.empty[String]
      pc.withOptions(CommonOptions(showWarnings = false)) { _ =>
        files.foreach { f =>
          Riddl.parseAndValidate(RiddlParserInput(read(f), f.getPath), shouldFailOnError = false) match
            case Left(_) => () // an include fragment or a deliberately-invalid fixture
            case Right(result) =>
              try
                val out = Pass.runPass[ProjectionOutput](
                  PassInput(result.root),
                  PassesOutput(),
                  ProjectionPass(PassInput(result.root), result.outputs)
                )
                projected += 1
                records += out.records.size
              catch
                case NonFatal(x) => broken += s"${f.getPath}: ${x.getClass.getSimpleName}: ${x.getMessage.take(160)}"
        }
      }
      info(s"projected=$projected records=$records of ${files.size} fixtures")
      withClue(s"${broken.size} fixtures threw in the projection:\n  ${broken.mkString("\n  ")}\n") {
        broken mustBe empty
      }
      projected must be > ProjectedFloor
    }
  }
}
