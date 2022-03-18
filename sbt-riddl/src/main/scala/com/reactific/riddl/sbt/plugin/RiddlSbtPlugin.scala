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

package com.reactific.riddl.sbt.plugin

import sbt.*
import sbt.Keys.*
import sbt.internal.util.ManagedLogger
import sbt.plugins.JvmPlugin

import scala.language.postfixOps

/** A plugin that endows sbt with knowledge of code generation via riddl */
object RiddlSbtPlugin extends AutoPlugin {
  override def requires: AutoPlugin = JvmPlugin

  object autoImport {

    sealed trait RIDDLCOption
    case object Verbose extends RIDDLCOption
    case object Quiet extends RIDDLCOption
    case object SuppressWarnings extends RIDDLCOption
    case object SuppressMissingWarnings extends RIDDLCOption
    case object SuppressStyleWarnings extends RIDDLCOption
    case object ShowTimes extends RIDDLCOption

    lazy val riddlcPath = settingKey[File]("Path to `riddlc` compiler")
    lazy val riddlcOptions = settingKey[Seq[RIDDLCOption]]("Options for the riddlc compiler")
    lazy val fileToTranslate =
      settingKey[String]("Name of top level file to translate to documentation")
    lazy val outDir = settingKey[File]("Path to riddlc output directory")
    lazy val configFile = settingKey[File]("Path to riddlc configuration file")

  }

  import autoImport.*
  override lazy val globalSettings: Seq[Setting[?]] = {
    Seq(
      riddlcPath := file("riddlc"),
      riddlcOptions := Seq.empty[RIDDLCOption],
      fileToTranslate := "top",
      outDir := (target.value / "riddl" / "doc"),
      configFile := (Compile / sourceDirectory).value / "riddl" /
        (fileToTranslate.value + ".config")
    )
  }

  lazy val compileTask = taskKey[Seq[File]]("A task to invoke riddlc compiler")

  override lazy val projectSettings: Seq[Setting[?]] =
    Seq((Compile / compile) := ((Compile / compile) dependsOn compileTask).value)

  compileTask := {
    val srcFile = (Compile / sourceDirectory).value / "riddl" / (fileToTranslate.value + ".riddl")
    val execPath = riddlcPath.value
    val options = riddlcOptions.value
    val output = outDir.value
    val configF: File = configFile.value
    val log: ManagedLogger = streams.value.log
    translateToDoc(execPath, options, srcFile, output, configF, log)
  }

  def translateToDoc(
    riddlc: sbt.File,
    options: Seq[RIDDLCOption],
    src: File,
    outDir: sbt.File,
    config: sbt.File,
    log: ManagedLogger
  ): Seq[File] = {
    import sys.process.*
    val flags = options.map {
      case Verbose                 => "--verbose"
      case Quiet                   => "--quiet"
      case SuppressWarnings        => "--suppress-warnings"
      case SuppressMissingWarnings => "--suppress-missing-warnings"
      case SuppressStyleWarnings   => "--suppress-style-warnings"
      case ShowTimes               => "--show-times"
    }.mkString(" ")
    val command = riddlc.toString + " " + flags + " translate" + " -i " + src.toString + " -c " +
      config.toString + " -o " + outDir.toString
    log.info(s"Executing command: $command")
    val result: String = command !!

    log.info(s"Command Result:\n$result")
    Seq[File](outDir)
  }
}

/*
riddlc [parse|validate|translate] [options] <args>...

  -h, --help
  -v, --verbose
  -q, --quiet
  -w, --suppress-warnings
  -m, --suppress-missing-warnings
  -s, --suppress-style-warnings
  -t, --show-times
Command: parse [options]
Parse the input for syntactic compliance with riddl language
  -i, --input-file <value>
                           required riddl input file to compile
Command: validate [options]

  -i, --input-file <value>
                           required riddl input file to compile
Command: translate [options] kind
translate riddl as specified in configuration file
  kind                     The kind of output to generate during translation
  -i, --input-file <value>
                           required riddl input file to compile
  -c, --configuration-file <value>
                           configuration that specifies how to do the translation

Process finished with exit code 0

 */
