/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.utils.{OutputFile, Timer}

import java.nio.file.Path
import scala.collection.mutable
import scala.reflect.ClassTag

trait TranslatingOptions extends PassCommandOptions {
  def inputFile: Option[Path]

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

  def writeFiles(timeEach: Boolean): Unit = {
    files.foreach { (file: OF) =>
      Timer.time(s"Writing file: ${file.filePath}", timeEach) {
        file.write()
      }
    }
  }

  def addFile(file: OF): this.type = { files.append(file); this }

  def makeDefPath(
    definition: Definition[?],
    parents: Seq[Definition[?]]
  ): Seq[String] = {
    parents.filterNot(x => x.isInstanceOf[Root]).map(_.id.value) :+
      definition.id.value
  }
}

object TranslationCommand {
  trait Options extends TranslatingOptions {
    def inputFile: Option[Path]

    def outputDir: Option[Path]

    def projectName: Option[String]
  }
}

/** An abstract base class for translation style commands. That is, they translate an input file into an output
  * directory of files.
  *
  * @param name
  *   The name of the command to pass to [[CommandPlugin]]
  * @tparam OPT
  *   The option type for the command
  */
abstract class TranslationCommand[OPT <: TranslationCommand.Options: ClassTag](name: String)
    extends PassCommand[OPT](name) {}
