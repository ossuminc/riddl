package com.reactific.riddl.language

import org.scalatest.Assertion

import java.io.File
import java.nio.file.Path

abstract class RiddlFilesTestBase extends ValidatingTest {

  def checkAFile(rootDir: Path, file: File): Assertion

  def findRiddlFiles(dirFile: File, recurse: Boolean = false): Seq[File] = {
    val dirKids = dirFile.listFiles().toSeq
    val (files, dir) = dirKids.partition(_.isFile())
    if (!recurse) { files.filter(_.toString.endsWith(".riddl")) }
    else { files.filter(_.toString.endsWith(".riddl")) ++ dir.flatMap(f => findRiddlFiles(f)) }
  }

  def doOneFile(rootDir: Path, file: File): Unit = {
    s"check ${file.getPath}" in { checkAFile(rootDir, file) }
  }

  def checkItems(items: Seq[(String, Boolean)]): Unit = {
    for { item <- items } {
      val dirFile = new File(item._1)
      require(dirFile.exists(), s"Test item '${item._1}' doesn't exist!")
      if (dirFile.isDirectory) {
        val dirPath: Path = dirFile.toPath
        val files = findRiddlFiles(dirFile, item._2)
        files.foreach(file => doOneFile(dirPath, file))
      } else { doOneFile(dirFile.getParentFile.toPath, dirFile) }
    }
  }
}
