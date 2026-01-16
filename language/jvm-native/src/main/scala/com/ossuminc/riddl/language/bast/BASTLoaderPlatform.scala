/*
 * Copyright 2019-2026 Ossum, Inc.
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
    * @param bi The BASTImport to load
    * @param baseURL The base URL for resolving relative paths
    * @param pc The platform context
    * @return Either an error message or the loaded Nebula
    */
  def loadSingleImport(bi: BASTImport, baseURL: URL)(using pc: PlatformContext): Either[String, Nebula] = {
    Try {
      // Resolve the path relative to the base URL
      val bastURL = if URL.isValid(bi.path.s) then {
        URL(bi.path.s)
      } else {
        baseURL.parent.resolve(bi.path.s)
      }

      // Load the BAST file bytes
      implicit val ec: ExecutionContext = pc.ec
      val future = pc.load(bastURL).map { data =>
        // Parse as BAST - note: data is loaded as String, convert to bytes
        val bytes = data.getBytes("ISO-8859-1") // Binary data preserved
        val reader = BASTReader(bytes)
        reader.read() // Returns Either[Messages, Nebula]
      }

      // Wait for the result (with timeout) - blocking I/O
      Await.result(future, 30.seconds)
    } match {
      case Success(Right(nebula)) => Right(nebula)
      case Success(Left(msgs)) => Left(msgs.map(_.format).mkString("; "))
      case Failure(ex) => Left(ex.getMessage)
    }
  }
}
