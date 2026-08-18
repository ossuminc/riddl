/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands

import com.ossuminc.riddl.language.Messages.{Messages, errors, warnings}
import com.ossuminc.riddl.passes.PassesResult
import com.ossuminc.riddl.utils.*
// [1.3]: `java.nio.file`, NOT `org.apache.commons.io`. commons-io is a JVM-only Java library and
// was the COMPILE-time blocker keeping this base class — and the ~193 cases that extend it — off
// Scala Native. Native's javalib has `java.nio.file`, which does the same three jobs.
import org.scalatest.*

import java.net.URL
import java.nio.file.{Files, Path}
import scala.annotation.unused
import scala.jdk.CollectionConverters.IteratorHasAsScala

/** Test Setup for running a command on the riddl-examples repos.
  *
  * This testkit helper allows you to create a test that runs a command on all the examples in the
  * riddl-examples repo. It will download the riddl-examples repo, unzip it, and run the command on
  * each example. The command is run in a temporary directory, and the output is compared to the
  * expected output in the example.
  *
  * @param shouldDelete
  *   Whether to delete the temporary files needed to run the test
  */
trait RunCommandOnExamplesTest(
  shouldDelete: Boolean = true
)(using io: PlatformContext)
    extends AbstractTestingBasis
    with BeforeAndAfterAll {

  val examplesRepo: String =
    "https://github.com/ossuminc/riddl-examples/archive/refs/heads/main.zip"
  val examplesURL: URL = java.net.URI.create(examplesRepo).toURL
  val tmpDir: Path = Files.createTempDirectory("riddl-examples")
  val examplesPath: Path = Path.of(s"riddl-examples-main/src/riddl")
  val srcDir: Path = tmpDir.resolve(examplesPath)
  val outDir: Path = tmpDir.resolve("out")

  override def beforeAll(): Unit = {
    super.beforeAll()
    require(Files.isDirectory(tmpDir), "Temp directory failed to create")
    val fileName = PathUtils.copyURLToDir(examplesURL, tmpDir)
    val zip_path = tmpDir.resolve(fileName)
    Zip.unzip(zip_path, tmpDir)
    zip_path.toFile.delete()
  }

  override def afterAll(): Unit = {
    if shouldDelete then deleteRecursively(tmpDir)
    else info(s"Leaving $tmpDir undeleted")
  }

  /** [1.3]: the three filesystem operations this base needs, on `java.nio.file` so Scala Native can
    * run it. Each realises its `Seq` eagerly, because a `java.util.stream.Stream` holds an open
    * directory handle and these results outlive the call.
    */
  private def walk(start: Path): Seq[Path] =
    val stream = Files.walk(start)
    try stream.iterator().asScala.toSeq
    finally stream.close()

  private def list(dir: Path): Seq[Path] =
    val stream = Files.list(dir)
    try stream.iterator().asScala.toSeq
    finally stream.close()

  /** Deepest paths FIRST, so a directory is removed only after its contents — the ordering
    * `FileUtils.forceDelete` supplied and the reason a plain walk cannot replace it directly.
    */
  private def deleteRecursively(dir: Path): Unit =
    if Files.exists(dir) then
      walk(dir).sortBy(p => -p.toString.length).foreach { p =>
        try Files.deleteIfExists(p)
        catch { case _: java.io.IOException => () } // best effort: temp-dir cleanup
      }

  private final val suffix = "conf"

  def validateTestName(@unused name: String): Boolean = true

  private def forEachConfigFile[T](commandName: String)(
    f: (String, Path) => T
  ): Seq[Either[(String, Messages), T]] = {
    val configs = walk(srcDir).filter { p =>
      Files.isRegularFile(p) && p.getFileName.toString.endsWith("." + suffix)
    }
    for
      config <- configs
      name = config.toAbsolutePath.toString.dropRight(suffix.length + 1)
    yield {
      if validateTestName(name) then {
        Commands.loadCandidateCommands(config) match {
          case Right(commands) =>
            if commands.contains(commandName) then {
              Right(f(name, config))
            } else {
              Left(name -> errors(s"Command $commandName not found in $config"))
            }
          case Left(messages) => Left(name -> messages)
        }
      } else {
        Left(name -> warnings(s"Command $commandName skipped for $name"))
      }
    }
  }

  def forAFolder(folderName: String, commandName: String)(
    validate: (String, Path) => Either[Messages, PassesResult]
  ): Either[Messages, PassesResult] = {
    walk(srcDir)
      .find(p => Files.isDirectory(p) && p.getFileName.toString == "riddl") match {
      case Some(riddlDir) =>
        list(riddlDir)
          .filter(Files.isDirectory(_))
          .find(_.getFileName.toString.endsWith(folderName)) match {
          case Some(folder) =>
            list(folder).find(_.getFileName.toString == folderName + ".conf") match {
              case Some(config) =>
                Commands.loadCandidateCommands(config).flatMap { commands =>
                  if commands.contains(commandName) then validate(folderName, config)
                  else Left(errors(s"Config file $commandName not found in $config"))

                }
              case None =>
                Left(errors(s"No config file found in RIDDL-examples folder $folderName"))
            }
          case None =>
            Left(errors(s"RIDDL-examples folder $folderName not found"))
        }
      case None =>
        Left(errors(s"riddl-examples/riddl top level folder not found"))
    }
  }

  def outputDir = ""

  /** Call this from your test suite subclass to run all the examples found.
    */
  def runTests(commandName: String): Unit = {
    val results = forEachConfigFile(commandName) { case (name, path) =>
      val outputDir = outDir.resolve(name)

      val result = Commands.runCommandNamed(
        commandName,
        path,
        outputDirOverride = Some(outputDir)
      )
      result match {
        case Right(passesResult) =>
          onSuccess(commandName, name, passesResult, outputDir) -> name
        case Left(messages) =>
          onFailure(commandName, name, messages, outputDir) -> name
      }
    }
    for result <- results do {
      result match {
        case Right(_) => // do nothing
        case Left((name, messages)) =>
          val errors = messages.justErrors
          if errors.nonEmpty then {
            fail(s"Test case $name failed:\n${errors.format}")
          } else { info(errors.format) }
      }
    }
  }

  def runTestWithArgs(
    folderName: String,
    args: Array[String]
  ): Unit = {
    val commandName = args.head
    forAFolder(folderName, commandName) { case (name, _) =>
      val outputDir = outDir.resolve(name)
      val result = Commands.runCommandWithArgs(args)
      result match {
        case Right(passesResult) =>
          onSuccess(commandName, folderName, passesResult, outputDir)
        case Left(messages) =>
          onFailure(commandName, folderName, messages, outputDir)
      }
      result
    }
  }

  /** Call this from your test suite subclass to run all the examples found.
    */
  def runTest(folderName: String, commandName: String): Unit = {
    forAFolder(folderName, commandName) { case (name, config) =>
      val outputDir = outDir.resolve(name)
      val result = Commands.runCommandNamed(
        commandName,
        config,
        outputDirOverride = Some(outputDir)
      )
      result match {
        case Right(passesResult) =>
          onSuccess(commandName, folderName, passesResult, outputDir)
        case Left(messages) =>
          onFailure(commandName, folderName, messages, outputDir)
      }
      result
    }
  }

  /** Override this to do additional checking after a successful command run
    * @param commandName
    *   The name of the command that succeeded
    * @param caseName
    *   The name of the test case
    * @param result
    *   The PassesResult from running the passes
    * @param tempDir
    *   THe Path to the temporary directory containing the source
    * @return
    */
  def onSuccess(
    @unused commandName: String,
    @unused caseName: String,
    @unused result: PassesResult,
    @unused tempDir: Path
  ): Assertion = { succeed }

  /** Override this to capture the failure condition
    *
    * @param commandName
    *   The name of the command that failed
    * @param caseName
    *   The name of the test case that was running
    * @param messages
    *   The messages generated from the failure
    * @param tempDir
    *   THe Path to the temporary directory containing the source
    * @return
    */
  def onFailure(
    @unused commandName: String,
    @unused caseName: String,
    @unused messages: Messages,
    @unused tempDir: Path
  ): Assertion = { fail(messages.format) }

}
