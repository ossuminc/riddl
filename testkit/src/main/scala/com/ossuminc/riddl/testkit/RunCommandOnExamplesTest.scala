/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.testkit

/** Unit Tests For RunCommandOnExamplesTest */
import com.ossuminc.riddl.commands.CommandOptions
import com.ossuminc.riddl.commands.CommandPlugin
import com.ossuminc.riddl.language.CommonOptions
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.language.Messages.errors
import com.ossuminc.riddl.language.Messages.warnings
import com.ossuminc.riddl.utils.{PathUtils, Logger, SysLogger, Zip}
import org.apache.commons.io.FileUtils
import org.apache.commons.io.filefilter.DirectoryFileFilter
import org.apache.commons.io.filefilter.NotFileFilter
import org.apache.commons.io.filefilter.TrueFileFilter
import org.scalatest.*
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import scala.annotation.unused
import scala.jdk.CollectionConverters.IteratorHasAsScala

/** Test Setup for running a command on the riddl-examples repos.
  *
  * This testkit helper allows you to create a test that runs a command on all the examples in the riddl-examples repo.
  * It will download the riddl-examples repo, unzip it, and run the command on each example. The command is run in a
  * temporary directory, and the output is compared to the expected output in the example.
  *
  * @tparam OPT
  *   The class for the RiddlOptions of the command
  * @tparam CMD
  *   The class for the Command
  * @param commandName
  *   The name of the command to run.
  */
abstract class RunCommandOnExamplesTest[OPT <: CommandOptions, CMD <: CommandPlugin[OPT]](
  val commandName: String
) extends AnyWordSpec
    with Matchers
    with BeforeAndAfterAll {

  val examplesRepo: String =
    "https://github.com/ossuminc/riddl-examples/archive/refs/heads/main.zip"
  val examplesURL: URL = java.net.URI.create(examplesRepo).toURL
  val tmpDir: Path = Files.createTempDirectory("riddl-examples")
  val examplesPath: Path = Path.of(s"riddl-examples-main/src/riddl")
  val srcDir: Path = tmpDir.resolve(examplesPath)
  val outDir: Path = tmpDir.resolve("out")

  val commonOptions: CommonOptions = CommonOptions(
    showTimes = true,
    showWarnings = false,
    showMissingWarnings = false,
    showStyleWarnings = false,
    verbose = true
  )

  val logger: Logger = SysLogger()

  override def beforeAll(): Unit = {
    super.beforeAll()
    require(Files.isDirectory(tmpDir), "Temp directory failed to create")
    val fileName = PathUtils.copyURLToDir(examplesURL, tmpDir)
    val zip_path = tmpDir.resolve(fileName)
    Zip.unzip(zip_path, tmpDir)
    zip_path.toFile.delete()
  }

  override def afterAll(): Unit = {
    FileUtils.forceDeleteOnExit(tmpDir.toFile)
  }

  private final val suffix = "conf"

  def validateTestName(@unused name: String): Boolean = true

  private def forEachConfigFile[T](
    f: (String, Path) => T
  ): Seq[Either[(String, Messages), T]] = {
    val configs = FileUtils
      .iterateFiles(srcDir.toFile, Array[String](suffix), true)
      .asScala
      .toSeq
    for
      config <- configs
      name = config.getAbsolutePath.dropRight(suffix.length + 1)
    yield {
      if validateTestName(name) then {
        CommandPlugin.loadCandidateCommands(config.toPath) match {
          case Right(commands) =>
            if commands.contains(commandName) then {
              Right(f(name, config.toPath))
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

  def forAFolder[T](
    folderName: String
  )(f: (String, Path) => T): Either[Messages, T] = FileUtils
    .iterateFilesAndDirs(
      srcDir.toFile,
      DirectoryFileFilter.DIRECTORY,
      new NotFileFilter(TrueFileFilter.INSTANCE)
    )
    .asScala
    .toSeq
    .find(file => file.isDirectory && file.getName == "riddl") match {
    case Some(riddlDir) =>
      riddlDir.listFiles.toSeq
        .filter(file => file.isDirectory)
        .find(_.getName.endsWith(folderName)) match {
        case Some(folder) =>
          println(folder.listFiles.toSeq)
          folder.listFiles.toSeq.find(_.getName.endsWith(".conf")) match {
            case Some(config) =>
              CommandPlugin
                .loadCandidateCommands(config.toPath)
                .flatMap { cmds =>
                  if cmds.contains(commandName) then {
                    Right(f(folderName, config.toPath))
                  } else {
                    Left(errors(s"Command $commandName not found in $config"))
                  }
                }
            case None =>
              Left(
                errors(
                  s"No config file found in RIDDL-examples folder $folderName"
                )
              )
          }
        case None =>
          Left(errors(s"RIDDL-examples folder $folderName not found"))
      }
    case None =>
      Left(errors(s"riddl-examples/riddl top level folder not found"))
  }

  def outputDir = ""

  /** Call this from your test suite subclass to run all the examples found.
    */
  def runTests(): Unit = {
    val results = forEachConfigFile { case (name, path) =>
      val outputDir = outDir.resolve(name)

      val result = CommandPlugin.runCommandNamed(
        commandName,
        path,
        logger,
        commonOptions,
        outputDirOverride = Some(outputDir)
      )
      result match {
        case Right(command) =>
          onSuccess(commandName, name, path, command, outputDir) -> name
        case Left(messages) =>
          onFailure(commandName, name, path, messages, outputDir) -> name
      }
    }
    for result <- results do {
      result match {
        case Right(_) => // do nothing
        case Left((name, messages)) =>
          val errors = messages.justErrors
          if errors.nonEmpty then {
            fail(s"Test case $name failed:\n${errors.format}")
          } else { info(messages.format) }
      }
    }
  }

  /** Call this from your test suite subclass to run all the examples found.
    */
  def runTest(folderName: String): Unit = {
    forAFolder(folderName) { case (name, path) =>
      val outputDir = outDir.resolve(name)

      val result = CommandPlugin.runCommandNamed(
        commandName,
        path,
        logger,
        commonOptions,
        outputDirOverride = Some(outputDir)
      )
      result match {
        case Right(command) =>
          onSuccess(commandName, name, path, command, outputDir)
        case Left(messages) =>
          onFailure(commandName, name, path, messages, outputDir)

      }
    }
  }

  /** Override this to do additional checking after a successful command run
    * @param commandName
    *   The name of the command that succeeded
    * @param caseName
    *   The name of the test case
    * @param configFile
    *   The configuration file that was run
    * @param tempDir
    *   THe Path to the temporary directory containing the source
    * @return
    */
  def onSuccess(
    @unused commandName: String,
    @unused caseName: String,
    @unused configFile: Path,
    @unused command: CommandPlugin[CommandOptions],
    @unused tempDir: Path
  ): Assertion = { succeed }

  /** Override this to capture the failure condition
    *
    * @param commandName
    *   The name of the command that failed
    * @param caseName
    *   The name of the test case that was running
    * @param configFile
    *   The path to the config file that was used
    * @param messages
    *   The messages generated from the failure
    * @param tempDir
    *   THe Path to the temporary directory containing the source
    * @return
    */
  def onFailure(
    @unused commandName: String,
    @unused caseName: String,
    @unused configFile: Path,
    @unused messages: Messages,
    @unused tempDir: Path
  ): Assertion = { fail(messages.format) }

}
