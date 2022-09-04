/*
 * Copyright 2019 Reactific Software LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.reactific.riddl.language.testkit

/** Unit Tests For RunCommandOnExamplesTest */
import com.reactific.riddl.commands.{CommandOptions, CommandPlugin}
import com.reactific.riddl.language.CommonOptions
import com.reactific.riddl.language.Messages.{Messages, errors}
import com.reactific.riddl.utils.{Logger, PathUtils, SysLogger, Zip}
import org.apache.commons.io.FileUtils
import org.scalatest.*
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.net.URL
import java.nio.file.{Files, Path}
import scala.annotation.unused
import scala.jdk.CollectionConverters.IteratorHasAsScala

/** Test Setup for running a command on the examples  */
abstract class RunCommandOnExamplesTest[
  OPT <: CommandOptions, CMD <: CommandPlugin[OPT]
] (
  val commandName: String,
  val outputDir: Path
) extends AnyWordSpec with Matchers with BeforeAndAfterAll {

  val examplesRepo: String = "https://github.com/reactific/riddl-examples"
  val examplesVersion: String = "0.10.0"
  val examplesURL: URL =
    new URL(s"$examplesRepo/archive/refs/tags/$examplesVersion.zip")
  val tmpDir: Path = Files.createTempDirectory("RiddlTest")
  val examplesPath: Path =
    Path.of(s"riddl-examples-$examplesVersion/src/riddl")
  val srcDir: Path = tmpDir.resolve(examplesPath)

  val commonOptions: CommonOptions = CommonOptions(
    showTimes = true,
    showWarnings = false,
    showMissingWarnings = false,
    showStyleWarnings = false
  )

  val logger: Logger = SysLogger()

  override def beforeAll(): Unit = {
    super.beforeAll()
    require(Files.isDirectory(tmpDir), "Temp directory failed to create")
    val fileName = PathUtils.copyURLToDir(examplesURL, tmpDir)
    val zip_path = tmpDir.resolve(fileName)
    Zip.unzip(zip_path, tmpDir)
    zip_path.toFile.delete()
    if (!Files.isDirectory(outputDir)) { Files.createDirectories(outputDir) }
  }

  override def afterAll(): Unit = {
    FileUtils.forceDeleteOnExit(tmpDir.toFile)
  }

  private final val suffix = "conf"

  def forEachConfigFile[T](f : (String, Path) => T): Seq[Either[Messages,T]] = {
    val configs = FileUtils
      .iterateFiles(srcDir.toFile, Array[String](suffix),true)
      .asScala.toSeq
    for {
      config <- configs
    } yield {
      val name = config.getName.dropRight(suffix.length+1)
      CommandPlugin.loadCandidateCommands(config.toPath).flatMap { cmds =>
        if (cmds.contains(commandName)) {
          Right(f(name, config.toPath))
        } else {
          Left(errors(s"Command $commandName not found in $config"))
        }
      }
    }
  }

  /**
   * Call this from your test suite subclass to run all the examples found.
   */
  def runTests(): Unit = {
    forEachConfigFile { case (name, path) =>
      val outDir = outputDir.resolve(name)
      Files.createDirectories(outDir)
      CommandPlugin
        .runCommandNamed(commandName, path, logger, commonOptions) match {
        case Right(()) =>
          onSuccess(commandName, name, path, outDir)
        case Left(messages) =>
          fail(messages.map(_.format).mkString(System.lineSeparator()))
      }
    }
  }

  /**
   * Override this to do additional checking after a successful command run
   * @param commandName The name of the command that succeeded
   * @param caseName The name of the test case
   * @param configFile The configuration file that was run
   * @param outDir The output directory for translator commands
   * @return
   */
  def onSuccess(
    @unused commandName: String,
    @unused caseName: String,
    @unused configFile: Path,
    @unused outDir: Path
  ): Assertion = {
    succeed
  }
}

