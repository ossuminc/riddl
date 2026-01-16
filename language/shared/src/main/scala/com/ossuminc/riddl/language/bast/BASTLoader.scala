/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.bast

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{At, Messages}
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.utils.{PlatformContext, URL}

import scala.collection.mutable

/** Utility for loading BAST imports.
  *
  * This loads BAST files referenced by BASTImport nodes and populates their
  * contents field with the loaded Nebula contents. Imports can appear at the
  * root level or inside domains.
  */
object BASTLoader {

  /** Result of loading BAST imports */
  case class LoadResult(
    loadedCount: Int,
    failedCount: Int,
    messages: Messages
  )

  /** Load all BAST imports in a Root, including those inside domains.
    *
    * Finds all BASTImport nodes in the Root and its domains, loads the referenced
    * BAST files, and populates each BASTImport's contents field with the loaded
    * Nebula contents.
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

    def loadImport(bi: BASTImport): Unit = {
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
    }

    def processContents[T <: RiddlValue](contents: Contents[T]): Unit = {
      contents.foreach {
        case bi: BASTImport => loadImport(bi)
        case d: Domain => processContents(d.contents)
        case _ => () // Not a BASTImport or Domain, skip
      }
    }

    processContents(root.contents)
    LoadResult(loaded, failed, msgs.toList)
  }

  /** Load a single BAST import.
    *
    * This delegates to the platform-specific implementation since JVM/Native
    * support blocking I/O while JavaScript does not.
    *
    * @param bi The BASTImport to load
    * @param baseURL The base URL for resolving relative paths
    * @param pc The platform context
    * @return Either an error message or the loaded Nebula
    */
  private def loadSingleImport(bi: BASTImport, baseURL: URL)(using pc: PlatformContext): Either[String, Nebula] = {
    BASTLoaderPlatform.loadSingleImport(bi, baseURL)
  }

  /** Check if a Root has any unloaded BASTImport nodes.
    *
    * @param root The Root to check
    * @return true if there are BASTImport nodes with empty contents
    */
  def hasUnloadedImports(root: Root): Boolean = {
    var found = false
    def checkContents[T <: RiddlValue](contents: Contents[T]): Unit = {
      if !found then
        contents.foreach {
          case bi: BASTImport => if bi.contents.isEmpty then found = true
          case d: Domain => checkContents(d.contents)
          case _ => ()
        }
      end if
    }
    checkContents(root.contents)
    found
  }

  /** Get all BASTImport nodes from a Root, including those inside domains.
    *
    * @param root The Root to search
    * @return Sequence of BASTImport nodes
    */
  def getImports(root: Root): Seq[BASTImport] = {
    val result = mutable.ListBuffer.empty[BASTImport]
    def collectFromContents[T <: RiddlValue](contents: Contents[T]): Unit = {
      contents.foreach {
        case bi: BASTImport => result += bi
        case d: Domain => collectFromContents(d.contents)
        case _ => ()
      }
    }
    collectFromContents(root.contents)
    result.toSeq
  }
}
