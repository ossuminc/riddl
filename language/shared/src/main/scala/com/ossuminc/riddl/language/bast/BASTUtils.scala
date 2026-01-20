/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.bast

import com.ossuminc.riddl.language.AST.Nebula
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.utils.{PlatformContext, URL}

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration.*
import scala.util.{Failure, Success, Try}

/** Utility functions for BAST file operations.
  *
  * These functions support automatic BAST loading during parsing,
  * similar to Python's .pyc file mechanism.
  */
object BASTUtils {

  /** Check if a BAST file exists for a given RIDDL URL and is newer.
    *
    * Looks for a .bast file in the same directory as the .riddl file.
    * Returns the BAST URL only if:
    *   1. The .bast file exists
    *   2. The .bast file is newer than the .riddl file (by modification time)
    *
    * Note: This currently only works for file:// URLs on JVM platform.
    * On JS/Native platforms, this will always return None.
    *
    * @param riddlUrl The URL of the RIDDL file
    * @param pc The platform context for file operations
    * @return Some(bastUrl) if BAST file exists and is newer, None otherwise
    */
  def checkForBastFile(riddlUrl: URL)(using pc: PlatformContext): Option[URL] = {
    // Only handle file:// URLs
    if !riddlUrl.isFileScheme then return None

    try {
      // Get the full path from the URL
      val fullPath = riddlUrl.toFullPathString
      val bastPath = fullPath.replaceAll("\\.(riddl|RIDDL)$", ".bast")

      // Use java.nio.file for file system operations (JVM only)
      val riddlNioPath = java.nio.file.Paths.get(fullPath)
      val bastNioPath = java.nio.file.Paths.get(bastPath)

      // Check if BAST file exists
      if !java.nio.file.Files.exists(bastNioPath) then return None

      // Check if BAST file is newer
      val riddlTime = java.nio.file.Files.getLastModifiedTime(riddlNioPath)
      val bastTime = java.nio.file.Files.getLastModifiedTime(bastNioPath)

      if bastTime.compareTo(riddlTime) >= 0 then
        Some(URL.fromFullPath(bastPath))
      else
        None
      end if
    } catch {
      case _: Exception => None
    }
  }

  /** Load a BAST file and return its contents as a Nebula.
    *
    * @param bastUrl The URL of the BAST file to load
    * @param pc The platform context for file operations
    * @return Either error messages or the loaded Nebula
    */
  def loadBAST(bastUrl: URL)(using pc: PlatformContext): Either[Messages, Nebula] = {
    Try {
      implicit val ec: ExecutionContext = pc.ec
      val future = pc.load(bastUrl).map { data =>
        // Parse as BAST - data is loaded as String, convert to bytes
        val bytes = data.getBytes("ISO-8859-1") // Binary data preserved
        val reader = BASTReader(bytes)
        reader.read() // Returns Either[Messages, Nebula]
      }

      // Wait for the result (with timeout)
      Await.result(future, 30.seconds)
    } match {
      case Success(result) => result
      case Failure(ex) =>
        Left(List(Messages.Message(
          com.ossuminc.riddl.language.At.empty,
          s"Failed to load BAST file '${bastUrl.toExternalForm}': ${ex.getMessage}",
          Messages.Error
        )))
    }
  }

  /** Check for and load a BAST file if available, with fallback warning.
    *
    * This is a convenience function that:
    *   1. Checks if a BAST file exists and is newer
    *   2. If so, attempts to load it
    *   3. Returns the result or appropriate warnings/errors
    *
    * @param riddlUrl The URL of the RIDDL file
    * @param pc The platform context
    * @return Some((nebula, messages)) if BAST loaded, None if should parse RIDDL
    */
  def tryLoadBastOrParseRiddl(riddlUrl: URL)(using pc: PlatformContext): Option[(Nebula, Messages)] = {
    checkForBastFile(riddlUrl) match {
      case None => None // No BAST file or out of date, parse RIDDL
      case Some(bastUrl) =>
        loadBAST(bastUrl) match {
          case Right(nebula) =>
            Some((nebula, Messages.empty))
          case Left(errors) =>
            // BAST load failed, log warning and fall back to parsing
            pc.log.warn(s"BAST file '${bastUrl.toExternalForm}' failed to load, falling back to parsing: ${errors.map(_.format).mkString(", ")}")
            None // Signal to parse RIDDL instead
        }
    }
  }

  /** Get the BAST file URL for a given RIDDL URL.
    *
    * Simply replaces the .riddl extension with .bast.
    * Does not check if the file exists.
    *
    * @param riddlUrl The URL of the RIDDL file
    * @return The URL where the BAST file would be located
    */
  def getBastUrlFor(riddlUrl: URL): URL = {
    val path = riddlUrl.path.replaceAll("\\.(riddl|RIDDL)$", ".bast")
    URL(riddlUrl.scheme, riddlUrl.authority, riddlUrl.basis, path)
  }
}
