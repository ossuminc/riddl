/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.bast

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{At, Messages}
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.utils.{PlatformContext, URL}

import scala.collection.mutable
import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration.*
import scala.util.{Failure, Success, Try}

/** Utility for loading BAST imports.
  *
  * This loads BAST files referenced by BASTImport nodes and populates their
  * contents field with the loaded Nebula contents.
  */
object BASTLoader {

  /** Result of loading BAST imports */
  case class LoadResult(
    loadedCount: Int,
    failedCount: Int,
    messages: Messages
  )

  /** Load all BAST imports in a Root.
    *
    * Finds all BASTImport nodes in the Root and loads the referenced BAST files,
    * populating each BASTImport's contents field with the loaded Nebula contents.
    *
    * @param root The Root containing BASTImport nodes to load
    * @param baseURL The base URL for resolving relative BAST file paths
    * @param pc The platform context for file loading
    * @return LoadResult with counts and any error messages
    */
  def loadImports(root: Root, baseURL: URL)(using pc: PlatformContext): LoadResult = {
    val msgs = mutable.ListBuffer.empty[Messages.Message]
    var loaded = 0
    var failed = 0

    root.contents.foreach {
      case bi: BASTImport =>
        loadSingleImport(bi, baseURL) match {
          case Right(nebula) =>
            // Copy Nebula contents into BASTImport contents
            nebula.contents.foreach { item =>
              bi.contents.append(item)
            }
            loaded += 1
          case Left(error) =>
            msgs += Messages.Message(
              bi.loc,
              s"Failed to load BAST import '${bi.path.s}': $error",
              Messages.Error
            )
            failed += 1
        }
      case _ => () // Not a BASTImport, skip
    }

    LoadResult(loaded, failed, msgs.toList)
  }

  /** Load a single BAST import.
    *
    * @param bi The BASTImport to load
    * @param baseURL The base URL for resolving relative paths
    * @param pc The platform context
    * @return Either an error message or the loaded Nebula
    */
  private def loadSingleImport(bi: BASTImport, baseURL: URL)(using pc: PlatformContext): Either[String, Nebula] = {
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

      // Wait for the result (with timeout)
      Await.result(future, 30.seconds)
    } match {
      case Success(Right(nebula)) => Right(nebula)
      case Success(Left(msgs)) => Left(msgs.map(_.format).mkString("; "))
      case Failure(ex) => Left(ex.getMessage)
    }
  }

  /** Check if a Root has any unloaded BASTImport nodes.
    *
    * @param root The Root to check
    * @return true if there are BASTImport nodes with empty contents
    */
  def hasUnloadedImports(root: Root): Boolean = {
    root.contents.toSeq.exists {
      case bi: BASTImport => bi.contents.isEmpty
      case _ => false
    }
  }

  /** Get all BASTImport nodes from a Root.
    *
    * @param root The Root to search
    * @return Sequence of BASTImport nodes
    */
  def getImports(root: Root): Seq[BASTImport] = {
    root.contents.toSeq.collect {
      case bi: BASTImport => bi
    }
  }
}
