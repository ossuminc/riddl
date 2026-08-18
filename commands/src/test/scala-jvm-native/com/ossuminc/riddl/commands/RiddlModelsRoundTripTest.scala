/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands

import com.ossuminc.riddl.utils.StringHelpers.dropRightWhile
import com.ossuminc.riddl.utils.{PlatformContext, pc}
import org.ekrich.config.*
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.nio.file.{Files, Path}
import scala.jdk.StreamConverters.*

/** Comprehensive BAST round-trip test against all riddl-models.
  *
  * For each model in the riddl-models repository:
  *   1. Validate the original RIDDL source 2. Bastify (RIDDL -> BAST) 3. Unbastify (BAST -> RIDDL
  *      via PrettifyPass with flatten=true) 4. Prettify original with --single-file (same code
  *      path) 5. Compare unbastify output with prettified original
  *
  * Both outputs go through PrettifyPass, so any PrettifyPass formatting quirks cancel out. What
  * we're testing is that the BAST serialization/deserialization is lossless.
  *
  * Uses local ../riddl-models if available (faster for local dev), otherwise downloads from GitHub
  * (for CI).
  *
  * LEAVES THE MODELS REPO EXACTLY AS FOUND. Step 2 necessarily writes a .bast beside the source
  * .riddl (see `roundTripTest` for why it cannot be redirected), so each model's prior .bast --
  * content if it was checked in, absence if it was not -- is captured before and restored in a
  * `finally`. Without that, a single suite run rewrote all 189 checked-in .bast files in the
  * sibling checkout.
  */
class RiddlModelsRoundTripTest extends AnyWordSpec with Matchers with BeforeAndAfterAll {

  given io: PlatformContext = pc

  private val localDir = Path.of("../riddl-models")

  /** Which riddl-models branch to download when there is no local checkout.
    *
    * Defaults to `main`, which is right once a release has shipped. While a release BRANCH is in
    * flight, the corpus conforming to it lives on a matching branch of riddl-models and `main`
    * still holds the previous major — so CI downloaded 1.x models and failed against the 2.0
    * grammar, while local runs passed against the developer's checkout. Set `RIDDL_MODELS_BRANCH`
    * to point CI at the right one; drop it when the corpus merges to `main`.
    */
  /** Which riddl-models branch this corpus expects.
    *
    * Kept as documentation after the download was removed ([1.3]) and NAMED IN THE SKIP MESSAGE,
    * so a developer whose checkout is on the wrong branch is told which one to be on. `release/2`
    * rather than `main`, for the same reason CI pins `RIDDL_MODELS_BRANCH`: riddl-models `main`
    * still holds the 1.x corpus until 2.0.0 final merges.
    */
  private val modelsBranch: String =
    Option(System.getenv("RIDDL_MODELS_BRANCH")).filter(_.nonEmpty).getOrElse("release/2")

  /** [1.3], RULED 2026-08-18 by Reid: **read the sibling checkout; SKIP when it is absent.**
    *
    * The download fallback is GONE. It was the last thing keeping this suite — 189 cases, the
    * single largest block of the JVM/Native test gap — off Scala Native, because
    * `java.net.URL.openStream` is a stub there. A developer without the checkout now gets a SKIP
    * rather than a silent 40-second fetch, and CI keeps coverage by checking the repo out beside
    * this one.
    */
  private val riddlModelsDir: Path = localDir

  override def afterAll(): Unit = super.afterAll()

  private val commonArgs = Array(
    "--quiet",
    "--show-missing-warnings=false",
    "--show-style-warnings=false",
    "--show-usage-warnings=false"
  )

  // Models that redefine built-in type names (UserId, Location,
  // Currency). Remove after fixing in riddl-models.
  private val pendingModels: Set[String] = Set.empty

  "BAST round-trip" should {
    val confFiles = discoverModels(riddlModelsDir)
    if confFiles.isEmpty then
      // Loud on purpose: CLAUDE.md records that a cancelled corpus suite "reads as green in a
      // summary scan", and that has bitten this repo. Name the absolute path that was searched
      // so an absent checkout is distinguishable from an empty one.
      "be skipped — riddl-models checkout not found" in {
        cancel(
          s"riddl-models not found at ${localDir.toAbsolutePath} (branch '$modelsBranch') — " +
            "check it out beside riddl to run the corpus round trip; skipping rather than failing."
        )
      }
    else
      confFiles.foreach { case (confFile, riddlFile) =>
        val relPath =
          riddlModelsDir.relativize(confFile.getParent)
        val relPathStr = relPath.toString
        if pendingModels.exists(p => relPathStr.endsWith(p))
        then
          s"round-trip $relPath" in {
            pending // pre-existing validation errors in riddl-models
          }
        else
          s"round-trip $relPath" in {
            roundTripTest(confFile, riddlFile)
          }
        end if
      }
    end if
  }

  /** Discover models: find .conf files at depth 3, parse input-file
    */
  private def discoverModels(
    base: Path
  ): Seq[(Path, Path)] = {
    if !Files.isDirectory(base) then return Seq.empty
    val allConf = Files
      .walk(base, 5)
      .filter(p => p.toString.endsWith(".conf") && Files.isRegularFile(p))
      .toScala(Seq)

    allConf.flatMap { confFile =>
      val depth = base.relativize(confFile).getNameCount - 1
      if depth == 3 then parseInputFile(confFile).map(riddlFile => (confFile, riddlFile))
      else None
    }
  }

  /** Parse a .conf file to extract the input-file path */
  private def parseInputFile(confFile: Path): Option[Path] = {
    try {
      val config = ConfigFactory.parseFile(confFile.toFile)
      if config.hasPath("validate.input-file") then
        val inputFile =
          config.getString("validate.input-file")
        Some(confFile.getParent.resolve(inputFile))
      else None
    } catch {
      case _: ConfigException => None
    }
  }

  /** Run the round-trip for a single model:
    *   1. Validate original 2. Bastify 3. Unbastify (produces flattened .riddl) 4. Prettify
    *      original with --single-file 5. Compare unbastify output with prettified original
    */
  private def roundTripTest(
    confFile: Path,
    riddlFile: Path
  ): Unit = {
    val tempDir = Files.createTempDirectory("bast-roundtrip")
    val prettyDir = tempDir.resolve("pretty-original")

    val riddlPath = riddlFile.toAbsolutePath.toString
    val bastPath = riddlPath.replaceAll("\\.riddl$", ".bast")

    val i = riddlPath.indexOfSlice("riddl-models/")
    val partialRiddlPath = riddlPath.substring(i + "riddl-models/".length)
    val partialRiddlPathDir = partialRiddlPath.dropRightWhile(_ != '/').dropRight(1)
    val unbastDir: Path = tempDir.resolve("unbast").resolve(partialRiddlPathDir)

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

    // This test WRITES INTO THE MODELS REPO and must put it back exactly as found.
    //
    // `bastify` has no output-path option: `BastifyCommand.Options.outputDir` is derived from
    // `inputFile.getParent`, and `overrideOptions` deliberately ignores an override, so the .bast
    // lands beside the source .riddl -- inside ../riddl-models when a local checkout is used.
    // Left alone, one suite run rewrote all 189 checked-in .bast files in that sibling repo,
    // which is a surprising side effect of running riddl's own tests and defeats BACKLOG § 0's
    // decision to defer .bast regeneration to release.
    //
    // Redirecting the output is NOT an option here. Bastifying somewhere else would mean copying
    // the model first, and a model's `include` paths are relative -- the copy would not resolve
    // them, so the test would stop exercising the real shape. /dev/null does not work either:
    // step 3 reads the file back. So bastify writes where it must, and we restore afterwards.
    val bastFile = Path.of(bastPath)
    val preExistingBast: Option[Array[Byte]] =
      if Files.exists(bastFile) then Some(Files.readAllBytes(bastFile)) else None

    try {
      // Step 2: Bastify
      val bastifyArgs =
        commonArgs ++ Array("bastify", riddlPath)
      Commands.runMainForTest(bastifyArgs) match {
        case Left(messages) =>
          fail(
            s"Step 2 (bastify) failed:\n" +
              s"${messages.format}"
          )
        case Right(_) =>
          assert(
            Files.exists(bastFile),
            s"BAST file not created: $bastPath"
          )
      }

      // Step 3: Unbastify
      val unbastifyArgs = commonArgs ++ Array(
        "unbastify",
        "-o",
        unbastDir.toAbsolutePath.toString,
        "-s",
        "true",
        bastPath
      )
      Commands.runMainForTest(unbastifyArgs) match {
        case Left(messages) =>
          fail(s"Step 3 (unbastify) failed:\n${messages.format}")
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
              s"Round-trip differs at line " +
                s"${idx + 1}:\n" +
                s"  original:   $line1\n" +
                s"  round-trip: $line2"
            )
          case None =>
            if lines1.length != lines2.length then
              fail(
                s"Round-trip differs in length: " +
                  s"${lines1.length} vs " +
                  s"${lines2.length}"
              )
            end if
        }
      end if
    } finally {
      deleteRecursively(tempDir)
      // Restore the models repo. In a `finally` so a FAILING model does not leave the file
      // rewritten either -- a red run is exactly when someone is least likely to notice a dirty
      // sibling working tree, and most likely to re-run and compound it.
      preExistingBast match {
        case Some(bytes) => Files.write(bastFile, bytes) // was checked in: put the bytes back
        case None        => Files.deleteIfExists(bastFile) // was not there: leave no trace
      }
    }
  }

  private def deleteRecursively(path: Path): Unit = {
    if Files.isDirectory(path) then
      Files
        .list(path)
        .forEach(p => deleteRecursively(p.asInstanceOf[Path]))
    end if
    Files.deleteIfExists(path)
  }
}
