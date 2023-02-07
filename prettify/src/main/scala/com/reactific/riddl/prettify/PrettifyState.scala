/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.prettify
import com.reactific.riddl.language.CommonOptions
import com.reactific.riddl.language.Folding
import com.reactific.riddl.language.TranslatingState
import com.reactific.riddl.language.TranslationResult

import java.net.URL
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import scala.collection.mutable

case class PrettifyState(
  commonOptions: CommonOptions,
  options: PrettifyCommand.Options)
    extends Folding.State[PrettifyState]
    with TranslatingState[RiddlFileEmitter]
    with TranslationResult {

  require(options.inputFile.nonEmpty, "No input file specified")
  require(options.outputDir.nonEmpty, "No output directory specified")

  private val inPath: Path = options.inputFile.get
  private val outPath: Path = options.outputDir.get

  def relativeToInPath(path: Path): Path = inPath.relativize(path)

  def outPathFor(path: Path): Path = {
    val suffixPath = if (path.isAbsolute) relativeToInPath(path) else path
    outPath.resolve(suffixPath)
  }

  def outPathFor(url: URL): Path = {
    val suffixPath = Path.of(url.toURI)
    outPath.resolve(suffixPath)
  }

  def fileList: Seq[Path] = {
    closeStack()
    if (options.singleFile) {
      val content = filesAsString
      Files.writeString(firstFile.filePath, content, StandardCharsets.UTF_8)
      Seq(firstFile.filePath)
    } else { for { emitter <- files } yield { emitter.emit() } }.toSeq
  }

  def filesAsString: String = {
    closeStack()
    files.map(fe => s"\n// From '${fe.filePath.toString}'\n${fe.asString}")
      .mkString
  }

  private val fileStack: mutable.Stack[RiddlFileEmitter] = mutable.Stack
    .empty[RiddlFileEmitter]

  private def closeStack(): Unit = { while (fileStack.nonEmpty) popFile() }

  def current: RiddlFileEmitter = fileStack.head

  private val firstFile: RiddlFileEmitter = {
    val file = RiddlFileEmitter(outPathFor(inPath))
    pushFile(file)
    file
  }

  def pushFile(file: RiddlFileEmitter): PrettifyState = {
    fileStack.push(file)
    addFile(file)
  }

  def popFile(): PrettifyState = { fileStack.pop(); this }

  def withCurrent(f: RiddlFileEmitter => Unit): PrettifyState = {
    f(current); this
  }
}
