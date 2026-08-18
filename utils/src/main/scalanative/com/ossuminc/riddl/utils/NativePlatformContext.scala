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

  // [1.3]: wrapped in `FollowRedirectsBackend` explicitly. The Native curl backend surfaces ONLY
  // `Content-Length` among response headers — verified by listing them — so a 302's `Location` is
  // invisible and redirects CANNOT be followed by hand. GitHub archive URLs always redirect.
  private val sttpBackend =
    sttp.client4.wrappers.FollowRedirectsBackend(DefaultSyncBackend())

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
      // `asFile`, NOT `asByteArrayAlways`. Reid, 2026-08-18: for BINARY content sttp should stream
      // to a file rather than materialise a byte array. The in-memory path returned a SHORT body
      // here — a downloaded ZIP arrived too small to parse — while the same URL fetched 207,955
      // bytes with curl. Streaming to disk and reading the file back sidesteps whatever the
      // byte-array handler was doing to the content.
      val tmp = java.nio.file.Files.createTempFile("riddl-fetch", ".bin").toFile
      try
        val response = basicRequest.get(uri).response(asFile(tmp)).send(sttpBackend)
        val code = response.code.code
        if code >= 300 && code < 400 then
          // Case-INSENSITIVE header lookup, done by hand. `response.header("Location")` returned
          // None against GitHub, which answers HTTP/2 and therefore lower-cases its header names
          // ("location"). HTTP header names are case-insensitive by spec, so this is the backend
          // not honouring that — and the symptom was a 302's EMPTY body being handed back as the
          // content, which surfaced far away as "too short to be Zip".
          response.headers
            .find(_.name.equalsIgnoreCase("location"))
            .map(_.value) match
            case Some(next) => fetchFollowingRedirects(next, hopsLeft - 1)
            case None =>
              // VERIFIED UPSTREAM LIMITATION, not a bug here (2026-08-18). sttp's Scala Native
              // curl backend exposes ONLY `Content-Length` among response headers — confirmed by
              // listing them — so `Location` is invisible and a redirect cannot be followed at
              // ANY layer: not by hand here, and not by `FollowRedirectsBackend`, which needs the
              // same header. Both were tried.
              throw new RuntimeException(
                s"HTTP $code for $externalForm: this platform cannot follow HTTP redirects. " +
                  "sttp's Scala Native backend does not expose the Location header (only " +
                  s"${response.headers.map(_.name).mkString(", ")}). Use a direct, " +
                  "non-redirecting URL, or fetch on the JVM."
              )
        else if code >= 400 then throw new RuntimeException(s"HTTP $code fetching $externalForm")
        else java.nio.file.Files.readAllBytes(tmp.toPath)
        end if
      finally java.nio.file.Files.deleteIfExists(tmp.toPath)
      end try
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
