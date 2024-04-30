package com.ossuminc.riddl.passes.translate

import com.ossuminc.riddl.language.AST.{Definition, Root}
import com.ossuminc.riddl.utils.{OutputFile, Timer}

import java.nio.file.Path
import scala.collection.mutable

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
    definition: Definition,
    parents: Seq[Definition]
  ): Seq[String] = {
    parents.filterNot(x => x.isInstanceOf[Root]).map(_.id.value) :+
      definition.id.value
  }
}
