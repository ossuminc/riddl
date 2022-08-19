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

package com.reactific.riddl.language

import com.reactific.riddl.language.AST.RootContainer
import com.reactific.riddl.language.parsing.RiddlParserInput
import com.reactific.riddl.utils.{Logger, OutputFile}

import java.nio.file.Path
import scala.collection.mutable

trait TranslatingOptions {
  def inputFile: Option[Path]
  def outputDir: Option[Path]
  def projectName: Option[String]
}

trait TranslatorState[OF <: OutputFile] {
  def options: TranslatingOptions

  val files: mutable.ListBuffer[OF] = mutable.ListBuffer.empty[OF]

  def generatedFiles: Seq[Path] = files.map(_.filePath).toSeq

  val dirs: mutable.Stack[Path] = mutable.Stack[Path]()

  def addDir(name: String): Path = {
    dirs.push(Path.of(name))
    parentDirs
  }

  def parentDirs: Path = dirs.foldRight(Path.of("")) { case (nm, path) =>
    path.resolve(nm)
  }

  def close: Seq[Path] = {
    files.foreach(_.write())
    files.map(_.filePath).toSeq
  }

  def addFile(file: OF): Unit = {
    files.append(file)
  }
}

/** Base class of all Translators
 * @tparam OPT The options class used by the translator */
trait Translator[OPT <: TranslatingOptions] {

  protected def translateImpl(
    root: RootContainer,
    log: Logger,
    commonOptions: CommonOptions,
    options: OPT
  ): Seq[Path] = {
    require(options.inputFile.nonEmpty, "An input path was not provided.")
    require(options.outputDir.nonEmpty, "An output path was not provided.")
    if (commonOptions.verbose)
      log.info(s"Starting translation of `${root.id.format}")
    Seq.empty[Path]
  }

  final def translate(
    root: RootContainer,
    log: Logger,
    commonOptions: CommonOptions,
    options: OPT
  ): Seq[Path] = {
    val showTimes = commonOptions.showTimes
    Riddl.timer(stage = "translate", showTimes) {
      translateImpl(root, log, commonOptions, options)
    }
  }

  final def parseValidateTranslate(
    log: Logger,
    commonOptions: CommonOptions,
    options: OPT
  ): Seq[Path] = {
    require(options.inputFile.nonEmpty, "Input path option must not be empty")
    Riddl.parseAndValidate(options.inputFile.get, log, commonOptions) match {
      case Some(root) => translate(root, log, commonOptions, options)
      case None       => Seq.empty[Path]
    }
  }

  final def parseValidateTranslate(
    input: RiddlParserInput,
    log: Logger,
    commonOptions: CommonOptions,
    options: OPT
  ): Seq[Path] = {
    Riddl.parseAndValidate(input, log, commonOptions) match {
      case Some(root) => translate(root, log, commonOptions, options)
      case None       => Seq.empty[Path]
    }
  }
}
