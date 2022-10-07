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

import com.reactific.riddl.language.Messages.Messages
import com.reactific.riddl.language.parsing.RiddlParserInput
import com.reactific.riddl.language.AST.Definition
import com.reactific.riddl.language.AST.RootContainer
import com.reactific.riddl.utils.Logger
import com.reactific.riddl.utils.OutputFile

import java.nio.file.Path
import scala.collection.mutable

trait TranslatingOptions {
  def inputFile: Option[Path]
  def outputDir: Option[Path]
  def projectName: Option[String]
}

trait TranslatingState[OF <: OutputFile] {
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

  def addFile(file: OF): this.type = { files.append(file); this }

  def makeDefPath(
    definition: Definition,
    parents: Seq[Definition]
  ): Seq[String] = {
    parents.filterNot(x => x.isInstanceOf[RootContainer]).map(_.id.value) :+
      definition.id.value
  }
}

trait TranslationResult

/** Base class of all Translators
  * @tparam OPT
  *   The options class used by the translator
  */
trait Translator[OPT <: TranslatingOptions] {

  def translate(
    result: Validation.Result,
    log: Logger,
    commonOptions: CommonOptions,
    options: OPT
  ): Either[Messages, TranslationResult]

  final def parseValidateTranslate(
    log: Logger,
    commonOptions: CommonOptions,
    options: OPT
  ): Either[Messages, TranslationResult] = {
    require(options.inputFile.nonEmpty, "Input path option must not be empty")
    Riddl.parseAndValidate(options.inputFile.get, commonOptions).flatMap {
      results => translate(results, log, commonOptions, options)
    }
  }

  final def parseValidateTranslate(
    input: RiddlParserInput,
    log: Logger,
    commonOptions: CommonOptions,
    options: OPT
  ): Either[Messages, TranslationResult] = {
    Riddl.parseAndValidate(input, commonOptions).flatMap { results =>
      translate(results, log, commonOptions, options)
    }
  }
}
