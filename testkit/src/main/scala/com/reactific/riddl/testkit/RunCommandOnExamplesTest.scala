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

package com.reactific.riddl.testkit

/** Unit Tests For RunCommandOnExamplesTest */
import com.reactific.riddl.commands.CommandOptions
import com.reactific.riddl.commands.CommandPlugin
import com.reactific.riddl.language.CommonOptions
import com.reactific.riddl.language.Messages.Messages
import com.reactific.riddl.language.Messages.errors
import com.reactific.riddl.language.Messages.warnings
import com.reactific.riddl.utils.Logger
import com.reactific.riddl.utils.PathUtils
import com.reactific.riddl.utils.SysLogger
import com.reactific.riddl.utils.Zip
import org.apache.commons.io.FileUtils
import org.scalatest.*
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import scala.annotation.unused
import scala.jdk.CollectionConverters.IteratorHasAsScala

/** Test Setup for running a command on the examples */
abstract class RunCommandOnExamplesTest[
  OPT <: CommandOptions,
  CMD <: CommandPlugin[OPT]
](val commandName: String)
    extends AnyWordSpec with Matchers with BeforeAndAfterAll {

  val examplesRepo: String =
    "https://github.com/reactific/riddl-examples/archive/refs/heads/main.zip"
  val examplesURL: URL = new URL(examplesRepo)
  val tmpDir: Path = Files.createTempDirectory("riddl-examples")
  val examplesPath: Path = Path.of(s"riddl-examples-main/src/riddl")
  val srcDir: Path = tmpDir.resolve(examplesPath)
  val outDir: Path = tmpDir.resolve("out")

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
  }

  override def afterAll(): Unit = { FileUtils.forceDeleteOnExit(tmpDir.toFile) }

  private final val suffix = "conf"

  def validate(@unused name: String): Boolean = true

  def forEachConfigFile[T](f: (String, Path) => T): Seq[Either[Messages, T]] = {
    val configs = FileUtils
      .iterateFiles(srcDir.toFile, Array[String](suffix), true).asScala.toSeq
    for {
      config <- configs
      name = config.getName.dropRight(suffix.length + 1)
    } yield {
      if (validate(name)) {
        val commands = CommandPlugin.loadCandidateCommands(config.toPath)
        if (commands.contains(commandName)) { Right(f(name, config.toPath)) }
        else { Left(errors(s"Command $commandName not found in $config")) }
      } else {
        info(s"Skipping $name")
        Left(warnings(s"Command $commandName skipped for $name"))
      }
    }
  }

  def outputDir = ""

  /** Call this from your test suite subclass to run all the examples found.
    */
  def runTests(): Unit = {
    forEachConfigFile { case (name, path) =>
      val outputDir = outDir.resolve(name)

      val result = CommandPlugin.runCommandNamed(
        commandName,
        path,
        logger,
        commonOptions,
        outputDirOverride = Some(outputDir)
      )
      result match {
        case Right(cmd) => onSuccess(commandName, name, path, cmd, outputDir)
        case Left(messages) => fail(messages.format)
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
    * @return
    */
  def onSuccess(
    @unused commandName: String,
    @unused caseName: String,
    @unused configFile: Path,
    @unused command: CommandPlugin[CommandOptions],
    @unused tempDir: Path
  ): Assertion = { succeed }
}
