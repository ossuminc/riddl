/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.utils

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, StandardOpenOption}
import scala.scalajs.js.annotation.JSExportTopLevel

/** This is the JVM version of the Loader utility. It is used to load file content in UTF-8 via a
  * URL as a String and returning the Future that will obtain it. Further processing can be chained
  * onto the future. This handles the I/O part of parsing in a platform specific way.
  */
@JSExportTopLevel("JVMPlatformContext")
class JVMPlatformContext extends PlatformContext {

  import scala.concurrent.{ExecutionContext, Future}
  import scala.io.Source
  import scala.scalajs.js.annotation.JSExport

  given PlatformContext = this

  logger = SysLogger()

  @JSExport
  override def load(url: URL): Future[String] = {
    require(url.isValid, "Cannot load from an invalid URL")
    val source: Source = {
      import scala.io.Codec
      url.scheme match {
        case file: String if file == URL.fileScheme =>
          import java.io.FileNotFoundException
          import java.nio.file.{Files, Path}
          val path: Path =
            if url.basis.nonEmpty && url.path.nonEmpty then
              Path.of("/" + url.basis + "/" + url.path)
            else if url.basis.isEmpty && url.path.nonEmpty then Path.of(url.path)
            else if url.basis.nonEmpty && url.path.isEmpty then Path.of("/" + url.basis)
            else throw new IllegalStateException("URL is invalid!")
            end if
          if Files.isDirectory(path) then
            // Distinguish this from "not found": Source.fromFile on a directory raises an
            // IOException whose only clue is its message text, and on some paths the directory
            // never gets that far, so it was being reported as a missing file.
            throw new java.io.IOException(s"Operation on file $path failed: Is a directory")
          else if Files.exists(path) then Source.fromFile(path.toFile)(using Codec.UTF8)
          else throw FileNotFoundException(s"While loading $path")
        case _ =>
          val jurl = java.net.URI(url.toExternalForm).toURL
          Source.fromURL(jurl)(using Codec.UTF8)
      }
    }
    implicit val ec: ExecutionContext = this.ec
    Future {
      try {
        source.getLines().mkString("\n")
      } finally {
        source.close()
      }
    }
  }

  /** [1.3]: bytes, not text. `openStream` is real on the JVM, so this is the direct answer; the
    * `file` scheme is served by `Files.readAllBytes` rather than opening a stream on a URL, which
    * keeps a missing file reporting as a plain `NoSuchFileException`.
    */
  override def loadBytes(url: URL): Future[Array[Byte]] =
    Future {
      if url.scheme == URL.fileScheme then
        java.nio.file.Files
          .readAllBytes(java.nio.file.Path.of(url.toExternalForm.stripPrefix("file://")))
      else
        val in = java.net.URI.create(url.toExternalForm).toURL.openStream()
        try in.readAllBytes()
        finally in.close()
      end if
    }(using ec)

  override def read(file: URL): String =
    val source = Source.fromFile(file.toString)
    try {
      source.getLines().mkString("\n")
    } finally {
      source.close()
    }

  override def write(file: URL, content: String): Unit =
    Files.writeString(
      Path.of(file.toFullPathString),
      content,
      StandardCharsets.UTF_8,
      StandardOpenOption.CREATE,
      StandardOpenOption.TRUNCATE_EXISTING
    )

  override def stdout(message: String): Unit =
    System.err.print(message)

  override def stdoutln(message: String): Unit =
    System.out.println(message)

  override def newline: String = System.lineSeparator()

  override def ec: ExecutionContext = scala.concurrent.ExecutionContext.global
}
