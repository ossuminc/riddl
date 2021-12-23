package com.yoppworks.ossum.riddl.generation.hugo.rendering

import cats.instances.list._
import cats.instances.try_._
import cats.syntax.all._

import java.io.File
import java.io.PrintWriter
import java.io.FileWriter
import java.nio.file.Path

import scala.util.Try

object ContentWriter {
  final val contentOutputFolder = "content"

  def writeContent(toPath: String, content: List[HugoContent]): Try[List[String]] = for {
    output <- resolvePath(toPath)
    _ <- content.traverse(cnt => mkDirs(output, cnt.outputFilePath))
    files <- content.traverse(cnt => writeContentFile(output, cnt))
  } yield files

  private def resolvePath(toPath: String): Try[Path] = Try {
    val dirPath = new File(toPath)
    if (!dirPath.isDirectory) {
      throw new RuntimeException(s"$toPath must be a directory (is a file)")
    }
    if (!dirPath.exists) { dirPath.mkdirs() }
    val outputPath = dirPath.toPath.resolve(contentOutputFolder).toFile
    if (!outputPath.exists) { outputPath.mkdir() }

    outputPath.toPath.toAbsolutePath
  }

  private def writeContentFile(root: Path, content: HugoContent): Try[String] = Try {
    val file = root.resolve(content.outputFilePath.toString).toFile.getCanonicalFile
    val printer = new PrintWriter(new FileWriter(file))
    val lines = content.fileContents
    try {
      printer.print(lines)
      printer.flush()
      file.getAbsolutePath
    } finally { printer.close() }
  }

  private def mkDirs(root: Path, relative: RelativePath): Try[Unit] = Try {
    val relativeDir = if (relative.isDirectory) relative else relative.parent
    val dirFile = root.resolve(relativeDir.toString).toFile
    if (!dirFile.exists) { dirFile.mkdirs() }
  }

}
