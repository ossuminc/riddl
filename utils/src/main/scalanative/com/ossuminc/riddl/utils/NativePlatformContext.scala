/*
 * Copyright 2019-2026 Ossum Inc.
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
            val source = Source.fromFile(path.toFile)(using Codec.UTF8)
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

  /** [1.3]: bytes, not text. `java.net.URL.openStream` is a STUB here — it compiles and throws
    * `scala.NotImplementedError` — so this goes through sttp, exactly as [[load]] does above, and
    * asks for `asByteArrayAlways` instead of a decoded String.
    */
  override def loadBytes(url: URL): Future[Array[Byte]] =
    Future {
      if url.scheme == URL.fileScheme then
        java.nio.file.Files.readAllBytes(
          java.nio.file.Path.of(url.toExternalForm.stripPrefix("file://"))
        )
      else fetchFollowingRedirects(url.toExternalForm, hopsLeft = 5)
      end if
    }(using ec)

  /** Follow HTTP redirects EXPLICITLY rather than trusting the backend to do it.
    *
    * [1.3]: the riddl-examples archive URL
    * (`github.com/…/archive/refs/heads/main.zip`) answers **302** with an EMPTY body, and the
    * Native backend was returning that empty body rather than following the `Location`. The
    * download then produced a 0-byte file and the failure surfaced two steps later as
    * `java.util.zip.ZipException: too short to be Zip` — a corrupt-archive message for what was
    * really an unfollowed redirect. Any redirecting URL would have hit this, and GitHub archive
    * links always redirect.
    *
    * Bounded at five hops so a redirect loop fails with a clear message instead of hanging.
    */
  private def fetchFollowingRedirects(externalForm: String, hopsLeft: Int): Array[Byte] =
    if hopsLeft <= 0 then
      throw new RuntimeException(s"Too many HTTP redirects while fetching $externalForm")
    else
      val uri: Uri = Uri(java.net.URI.create(externalForm))
      val response = basicRequest.get(uri).response(asByteArrayAlways).send(sttpBackend)
      val code = response.code.code
      if code >= 300 && code < 400 then
        response.header("Location") match
          case Some(next) => fetchFollowingRedirects(next, hopsLeft - 1)
          case None =>
            throw new RuntimeException(s"HTTP $code for $externalForm with no Location header")
      else if code >= 400 then throw new RuntimeException(s"HTTP $code fetching $externalForm")
      else response.body
      end if
    end if
  end fetchFollowingRedirects

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
end NativePlatformContext
