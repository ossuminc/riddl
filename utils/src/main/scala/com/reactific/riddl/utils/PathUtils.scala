/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.utils

import java.io.File
import java.io.InputStream
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import scala.jdk.Accumulator
import scala.jdk.StreamConverters.StreamHasToScala

object PathUtils {

  /** Copy a resource from the classpath of this program to a destination file
    * @param resourceName
    *   name/path of the resource relative to src/main/resources
    * @param destination
    *   path to the destination
    */
  def copyResource(resourceName: String, destination: Path): Unit = {
    val src = this.getClass.getClassLoader.getResourceAsStream(resourceName)
    require(src != null, s"Failed to open resource ${resourceName}")
    Files.copy(src, destination, StandardCopyOption.REPLACE_EXISTING)
  }

  /** Determine if a program is in the current system PATH environment var
    * @param program
    *   The name of the program to find
    * @return
    *   True if the program is in the path, false otherwise
    */
  def existsInPath(program: String): Boolean = {
    System.getenv("PATH")
      .split(java.util.regex.Pattern.quote(File.pathSeparator)).map(Path.of(_))
      .exists(p => Files.isExecutable(p.resolve(program)))
  }

  def copyURLToDir(from: URL, destDir: Path): String = {
    val nameParts = from.getFile.split('/')
    if (nameParts.nonEmpty) {
      val fileName = scala.util.Random.self.nextLong.toString ++ nameParts.last
      Files.createDirectories(destDir)
      val dl_path = destDir.resolve(fileName)
      val in: InputStream = from.openStream
      if (in == null) { "" }
      else {
        try { Files.copy(in, dl_path, StandardCopyOption.REPLACE_EXISTING) }
        finally if (in != null) in.close()
      }
      if (Files.isRegularFile(dl_path)) { fileName }
      else { "" }
    } else { "" }
  }

  def compareDirectories(
    a: Path,
    b: Path
  )(missing: Path => Boolean,
    differentSize: (Path, Path) => Boolean,
    differentContent: (Path, Path) => Boolean
  ): Unit = {
    var exit = false
    val sourceFiles = Files.list(a).toScala(Accumulator).toList
      .filterNot(_.getFileName.toString.startsWith("."))
    for { fileA <- sourceFiles } {
      val fileNameA = fileA.getFileName
      val fileB = b.resolve(fileNameA)
      if (!Files.exists(fileB)) { exit = missing(fileB) }
      else {
        val sizeA = Files.size(fileA)
        val sizeB = Files.size(fileB)
        if (sizeA != sizeB) { exit = differentSize(fileA, fileB) }
        else {
          val bytesA = Files.readAllBytes(fileA)
          val bytesB = Files.readAllBytes(fileB)
          if (bytesA != bytesB) { exit = differentContent(fileA, fileB) }
        }
      }
    }
  }
}
