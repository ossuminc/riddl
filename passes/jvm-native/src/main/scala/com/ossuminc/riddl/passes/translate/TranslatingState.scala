/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.translate

import com.ossuminc.riddl.language.AST.{Definition, Root}
import com.ossuminc.riddl.utils.{OutputFile, PlatformContext, Timer}

import java.nio.file.Path
import scala.collection.mutable

/** Base class for the state of a [[TranslatingState]] so it can do its work. This class just takes care of managing the
  * output files.
  *
  * @tparam OF
  *   The type of the OutputFile that should be used to do the translation.
  */
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

  def writeFiles(timeEach: Boolean)(using PlatformContext): Unit = {
    files.foreach { (file: OF) =>
      Timer.time(s"Writing file: ${file.filePath}", timeEach) {
        file.write()
      }
    }
  }

  def addFile(file: OF): this.type = { files.append(file); this }

  def makeDefPath(
    definition: Definition,
    parents: Seq[Definition]
  ): Seq[String] = {
    parents.filterNot(x => x.isInstanceOf[Root]).map(_.id.value) :+
      definition.id.value
  }
}
