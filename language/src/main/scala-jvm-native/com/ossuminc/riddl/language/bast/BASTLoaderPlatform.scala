/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.bast

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.utils.{PlatformContext, URL}

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration.*
import scala.util.{Failure, Success, Try}

/** JVM/Native implementation of BAST loading with blocking I/O support. */
private[bast] object BASTLoaderPlatform {

  /** Load a single BAST import using blocking I/O.
    *
    * @param bi
    *   The BASTImport to load
    * @param baseURL
    *   The base URL for resolving relative paths
    * @param pc
    *   The platform context
    * @return
    *   Either an error message or the loaded Module
    */
  def loadSingleImport(bi: BASTImport, baseURL: URL)(using
    pc: PlatformContext
  ): Either[String, Module] = {
    Try {
      // Resolve the path: full URL, absolute filesystem path, or relative to the base URL
      val bastURL = BASTLoader.resolveBastURL(bi.path.s, baseURL)
      BASTReader(readBytes(bastURL)).read() // Returns Either[Messages, Module]
    } match {
      case Success(Right(module)) => Right(module)
      case Success(Left(msgs))    => Left(msgs.map(_.format).mkString("; "))
      case Failure(ex)            => Left(ex.getMessage)
    }
  }

  /** Read the raw bytes of a BAST file.
    *
    * A `.bast` file is BINARY. `PlatformContext.load` decodes as UTF-8 and joins lines with "\n",
    * which either throws `MalformedInputException` or silently mangles the bytes — so a local file
    * is read directly instead. Only a remote (http) BAST file still goes through the string path,
    * which remains lossy; fetching those as bytes needs a platform-context capability that does not
    * exist yet.
    */
  private def readBytes(url: URL)(using pc: PlatformContext): Array[Byte] =
    if url.isFileScheme then
      import java.nio.file.{Files, Path}
      val path: Path =
        if url.basis.nonEmpty && url.path.nonEmpty then Path.of("/" + url.basis + "/" + url.path)
        else if url.basis.isEmpty && url.path.nonEmpty then Path.of(url.path)
        else Path.of("/" + url.basis)
      Files.readAllBytes(path)
    else
      implicit val ec: ExecutionContext = pc.ec
      Await.result(pc.load(url).map(_.getBytes("ISO-8859-1")), 30.seconds)
    end if
  end readBytes
}
