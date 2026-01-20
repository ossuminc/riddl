/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.utils

import sttp.client4.*
import sttp.model.Uri
import sttp.model.Uri.*
import sttp.shared.Identity

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, StandardOpenOption}

/** This is the JVM version of the PlatformContext. It is used to load file content in UTF-8 via a
  * URL as a String and returning the Future that will obtain it. Further processing can be chained
  * onto the future. This handles the I/O part of parsing in a platform specific way.
  */
class NativePlatformContext extends PlatformContext:

  import scala.concurrent.{ExecutionContext, Future}
  import scala.io.Source

  given PlatformContext = this

  private val sttpBackend = DefaultSyncBackend()

  logger = SysLogger()

  override def load(url: URL): Future[String] =
    require(url.isValid, "Cannot load from an invalid URL")
    require(url.isValid, "Cannot load from an invalid URL")

    import scala.io.Codec
    implicit val ec: ExecutionContext = this.ec
    url.scheme match
      case file: String if file == URL.fileScheme =>
        import java.io.FileNotFoundException
        import java.nio.file.{Files, Path}
        val path: Path =
          if url.basis.nonEmpty && url.path.nonEmpty then Path.of("/" + url.basis + "/" + url.path)
          else if url.basis.isEmpty && url.path.nonEmpty then Path.of(url.path)
          else if url.basis.nonEmpty && url.path.isEmpty then Path.of("/" + url.basis)
          else throw new IllegalArgumentException("URL is invalid!")
          end if
        if Files.exists(path) then
          Future {
            val source = Source.fromFile(path.toFile)(Codec.UTF8)
            try {
              source.getLines().mkString("\n")
            } finally {
              source.close()
            }
          }
        else throw FileNotFoundException(s"While loading $path")
        end if
      case http: String if http == URL.httpsScheme | http == URL.httpScheme =>
        val jUri: java.net.URI = java.net.URI.create(url.toExternalForm)
        val resource: Uri = Uri(jUri)
        Future {
          val response = basicRequest.get(resource).send(sttpBackend)
          response.body match
            case Left(message) => throw new RuntimeException(message)
            case Right(body)   => body
          end match
        }
    end match
  end load

  override def read(file: URL): String =
    val source = Source.fromFile(file.toString)
    try {
      source.getLines.mkString("\n")
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

  override def newline: String = "\n"

  override def ec: ExecutionContext = scala.concurrent.ExecutionContext.global
end NativePlatformContext
