/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.prettify

import com.ossuminc.riddl.language.CommonOptions
import com.ossuminc.riddl.commands.TranslatingState

import java.net.URL
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import scala.collection.mutable

case class PrettifyState(
  commonOptions: CommonOptions = CommonOptions.empty,
  options: PrettifyCommand.Options = PrettifyCommand.Options()
) extends TranslatingState[RiddlFileEmitter] {

  require(options.inputFile.nonEmpty, "No input file specified")
  require(options.outputDir.nonEmpty, "No output directory specified")

  private val inPath: Path = options.inputFile.getOrElse(Path.of(""))
  private val outPath: Path = options.outputDir.getOrElse(Path.of(""))

  def relativeToInPath(path: Path): Path = inPath.relativize(path)

  def outPathFor(path: Path): Path = {
    val suffixPath = if path.isAbsolute then relativeToInPath(path) else path
    outPath.resolve(suffixPath)
  }

  def outPathFor(url: URL): Path = {
    val suffixPath = Path.of(url.toURI)
    outPath.resolve(suffixPath)
  }

  def fileList: Seq[Path] = {
    closeStack()
    if options.singleFile then {
      val content = filesAsString
      Files.writeString(firstFile.filePath, content, StandardCharsets.UTF_8)
      Seq(firstFile.filePath)
    } else { for emitter <- files yield { emitter.emit() } }.toSeq
  }

  def filesAsString: String = {
    closeStack()
    files.map(fe => s"\n// From '${fe.filePath.toString}'\n${fe.toString}").mkString
  }

  private val fileStack: mutable.Stack[RiddlFileEmitter] = mutable.Stack
    .empty[RiddlFileEmitter]

  private def closeStack(): Unit = { while fileStack.nonEmpty do popFile() }

  def current: RiddlFileEmitter = fileStack.headOption.getOrElse(RiddlFileEmitter(Path.of("")))

  private val firstFile: RiddlFileEmitter = {
    val file = RiddlFileEmitter(outPathFor(inPath))
    pushFile(file)
    file
  }

  def pushFile(file: RiddlFileEmitter): this.type = {
    fileStack.push(file)
    addFile(file)
  }

  def popFile(): PrettifyState = { fileStack.pop(); this }

  def withCurrent(f: RiddlFileEmitter => Unit): this.type = {
    f(current); this
  }
}
