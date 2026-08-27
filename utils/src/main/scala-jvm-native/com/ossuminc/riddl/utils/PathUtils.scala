/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.utils

import java.io.{File, InputStream}
import java.net.URL as JNURL
import java.nio.file.{Files, Path, StandardCopyOption}
import scala.jdk.Accumulator
import java.nio.file.{Files, Path, StandardCopyOption}
import scala.jdk.StreamConverters.StreamHasToScala
import scala.concurrent.duration.DurationInt

object PathUtils {

  /** Copy a resource from the classpath of this program to a destination file
    * @param resourceName
    *   name/path of the resource relative to src/main/resources
    * @param destination
    *   path to the destination
    */
  def copyResource(resourceName: String, destination: Path): Unit = {
    val src = this.getClass.getClassLoader.getResourceAsStream(resourceName)
    require(src != null, s"Failed to open resource $resourceName")
    Files.copy(src, destination, StandardCopyOption.REPLACE_EXISTING)
  }

  /** Determine if a program is in the current system PATH environment var
    * @param program
    *   The name of the program to find
    * @return
    *   True if the program is in the path, false otherwise
    */
  def existsInPath(program: String): Boolean = {
    System
      .getenv("PATH")
      .split(java.util.regex.Pattern.quote(File.pathSeparator))
      .map(Path.of(_))
      .exists(p => Files.isExecutable(p.resolve(program)))
  }

  def copyURLToDir(from: JNURL, destDir: Path)(using pc: PlatformContext): String = {
    // [1.3]: `toExternalForm` + `PlatformContext.loadBytes`, NOT `getFile` + `openStream`. Both
    // of those are STUBS on Scala Native (`java_net_url_stubs`) -- they compile and throw
    // `scala.NotImplementedError` when called, which is how the `commands` example-corpus suites
    // aborted on Native while passing on the JVM. `loadBytes` is the same operation with a real
    // implementation on all THREE platforms: `openStream` on the JVM, sttp on Native, `dom.fetch`
    // on Scala.js -- each the same stack that platform already uses for text.
    //
    // Query and fragment are stripped before taking the last segment, so a URL ending `?ref=main`
    // does not become part of the filename.
    val externalForm = from.toExternalForm
    val nameParts = externalForm.takeWhile(c => c != '?' && c != '#').split('/')
    if nameParts.nonEmpty then {
      val fileName = scala.util.Random.self.nextLong.toString ++ nameParts.last
      Files.createDirectories(destDir)
      val dl_path = destDir.resolve(fileName)
      val bytes = Await.result(pc.loadBytes(URL(externalForm)), 5.minutes)
      Files.write(dl_path, bytes)
      if Files.isRegularFile(dl_path) then { fileName }
      else { "" }
    } else { "" }
  }

  def compareDirectories(
    a: Path,
    b: Path
  )(
    missing: Path => Boolean,
    differentSize: (Path, Path) => Boolean,
    differentContent: (Path, Path) => Boolean
  ): Unit = {
    var exit = false
    val sourceFiles = Files
      .list(a)
      .toScala(Accumulator)
      .toList
      .filterNot(_.getFileName.toString.startsWith("."))
    for fileA <- sourceFiles do {
      val fileNameA = fileA.getFileName
      val fileB = b.resolve(fileNameA)
      if !Files.exists(fileB) then { exit = missing(fileB) }
      else {
        val sizeA = Files.size(fileA)
        val sizeB = Files.size(fileB)
        if sizeA != sizeB then { exit = differentSize(fileA, fileB) }
        else {
          val bytesA = Files.readAllBytes(fileA)
          val bytesB = Files.readAllBytes(fileB)
          if bytesA != bytesB then { exit = differentContent(fileA, fileB) }
        }
      }
    }
  }

  private def fromPaths(basis: String, path: String, purpose: String = "")(using
    PlatformContext
  ): URL =
    URL("file", "", basis.dropWhile(_ == '/'), path.dropWhile(_ == '/'))

  /** Set up a parser input for parsing directly from a local file based on the current working
    * directory
    *
    * @param path
    *   The path that will be added to the current working directory
    */
  def urlFromCwdPath(path: Path, purpose: String = ""): URL =
    URL.fromCwdPath(path.toString)

  def urlFromFullPath(path: Path, purpose: String = "")(using PlatformContext): URL =
    require(path.toString.startsWith("/"))
    URL.fromFullPath(path.toString)

  def urlFromPath(path: Path, purpose: String = "")(using PlatformContext): URL =
    if path.toString.startsWith("/") then urlFromFullPath(path, purpose)
    else urlFromCwdPath(path, purpose)

}
