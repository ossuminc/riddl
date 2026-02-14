/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands

import com.ossuminc.riddl.utils.{pc, PlatformContext}
import org.ekrich.config.*
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.nio.file.{Files, Path}
import scala.jdk.StreamConverters.*

/** Comprehensive BAST round-trip test against all riddl-models.
  *
  * For each model in the riddl-models repository:
  *   1. Validate the original RIDDL source
  *   2. Bastify (RIDDL -> BAST)
  *   3. Unbastify (BAST -> RIDDL via PrettifyPass with flatten=true)
  *   4. Prettify original with --single-file (same code path)
  *   5. Compare unbastify output with prettified original
  *
  * Both outputs go through PrettifyPass, so any PrettifyPass
  * formatting quirks cancel out. What we're testing is that
  * the BAST serialization/deserialization is lossless.
  */
class RiddlModelsRoundTripTest extends AnyWordSpec with Matchers {

  given io: PlatformContext = pc

  private val riddlModelsDir = Path.of("../riddl-models")

  private val commonArgs = Array(
    "--quiet",
    "--show-missing-warnings=false",
    "--show-style-warnings=false",
    "--show-usage-warnings=false"
  )

  "BAST round-trip" should {
    assume(
      Files.isDirectory(riddlModelsDir),
      "riddl-models not found at ../riddl-models — skipping"
    )

    val confFiles = discoverModels(riddlModelsDir)
    assume(confFiles.nonEmpty, "No .conf files found in riddl-models")

    confFiles.foreach { case (confFile, riddlFile) =>
      val relPath = riddlModelsDir.relativize(confFile.getParent)

      s"round-trip $relPath" in {
        roundTripTest(confFile, riddlFile)
      }
    }
  }

  /** Discover models: find .conf files at depth 3, parse input-file */
  private def discoverModels(base: Path): Seq[(Path, Path)] = {
    val allConf = Files
      .walk(base, 5)
      .filter(p =>
        p.toString.endsWith(".conf") && Files.isRegularFile(p)
      )
      .toScala(Seq)

    allConf.flatMap { confFile =>
      val depth = base.relativize(confFile).getNameCount - 1
      if depth == 3 then
        parseInputFile(confFile).map(riddlFile =>
          (confFile, riddlFile)
        )
      else None
    }
  }

  /** Parse a .conf file to extract the input-file path */
  private def parseInputFile(confFile: Path): Option[Path] = {
    try {
      val config = ConfigFactory.parseFile(confFile.toFile)
      if config.hasPath("validate.input-file") then
        val inputFile = config.getString("validate.input-file")
        Some(confFile.getParent.resolve(inputFile))
      else None
    } catch {
      case _: ConfigException => None
    }
  }

  /** Run the round-trip for a single model:
    *   1. Validate original
    *   2. Bastify
    *   3. Unbastify (produces flattened .riddl)
    *   4. Prettify original with --single-file
    *   5. Compare unbastify output with prettified original
    */
  private def roundTripTest(
    confFile: Path,
    riddlFile: Path
  ): Unit = {
    val tempDir = Files.createTempDirectory("bast-roundtrip")
    val unbastDir = tempDir.resolve("unbast")
    val prettyDir = tempDir.resolve("pretty-original")

    try {
      val riddlPath = riddlFile.toAbsolutePath.toString
      val bastPath = riddlPath.replaceAll("\\.riddl$", ".bast")

      // Step 1: Validate original
      val validateArgs =
        commonArgs ++ Array("validate", riddlPath)
      Commands.runMainForTest(validateArgs) match {
        case Left(messages) =>
          fail(
            s"Step 1 (validate original) failed:\n" +
              s"${messages.format}"
          )
        case Right(_) => // ok
      }

      // Step 2: Bastify
      val bastifyArgs =
        commonArgs ++ Array("bastify", riddlPath)
      Commands.runMainForTest(bastifyArgs) match {
        case Left(messages) =>
          fail(s"Step 2 (bastify) failed:\n${messages.format}")
        case Right(_) =>
          assert(
            Files.exists(Path.of(bastPath)),
            s"BAST file not created: $bastPath"
          )
      }

      try {
        // Step 3: Unbastify (uses PrettifyPass with flatten=true)
        val unbastifyArgs = commonArgs ++ Array(
          "unbastify",
          bastPath,
          "-o",
          unbastDir.toAbsolutePath.toString
        )
        Commands.runMainForTest(unbastifyArgs) match {
          case Left(messages) =>
            fail(
              s"Step 3 (unbastify) failed:\n${messages.format}"
            )
          case Right(_) =>
            assert(
              Files.exists(unbastDir),
              s"Unbastify output dir not created"
            )
        }

        // Find the unbastify output file
        val outputRiddlFiles = Files
          .list(unbastDir)
          .filter(p => p.toString.endsWith(".riddl"))
          .toScala(Seq)
        assert(
          outputRiddlFiles.nonEmpty,
          "No .riddl files in unbastify output"
        )
        val unbastContent =
          Files.readString(outputRiddlFiles.head)

        // Step 4: Prettify original with --single-file
        val prettyArgs = commonArgs ++ Array(
          "prettify",
          riddlPath,
          "-o",
          prettyDir.toAbsolutePath.toString,
          "-s",
          "true"
        )
        Commands.runMainForTest(prettyArgs) match {
          case Left(messages) =>
            fail(
              s"Step 4 (prettify original) failed:\n" +
                s"${messages.format}"
            )
          case Right(_) => // ok
        }

        val prettyFile =
          prettyDir.resolve("prettify-output.riddl")
        assert(
          Files.exists(prettyFile),
          "Prettified original not found"
        )
        val prettyContent = Files.readString(prettyFile)

        // Step 5: Compare unbastify output with prettified
        // original. Both go through PrettifyPass, so format
        // quirks cancel out. Differences = BAST data loss.
        if unbastContent != prettyContent then
          val lines1 =
            prettyContent.linesIterator.toIndexedSeq
          val lines2 =
            unbastContent.linesIterator.toIndexedSeq
          val firstDiff = lines1
            .zipAll(lines2, "<missing>", "<missing>")
            .zipWithIndex
            .find { case ((a, b), _) => a != b }

          firstDiff match {
            case Some(((line1, line2), idx)) =>
              fail(
                s"Round-trip differs at line ${idx + 1}:\n" +
                  s"  original:   $line1\n" +
                  s"  round-trip: $line2"
              )
            case None =>
              if lines1.length != lines2.length then
                fail(
                  s"Round-trip differs in length: " +
                    s"${lines1.length} vs ${lines2.length}"
                )
              end if
          }
        end if
      } finally {
        // Clean up .bast file created next to source
        Files.deleteIfExists(Path.of(bastPath))
      }
    } finally {
      deleteRecursively(tempDir)
    }
  }

  private def deleteRecursively(path: Path): Unit = {
    if Files.isDirectory(path) then
      Files
        .list(path)
        .forEach(p =>
          deleteRecursively(p.asInstanceOf[Path])
        )
    end if
    Files.deleteIfExists(path)
  }
}
