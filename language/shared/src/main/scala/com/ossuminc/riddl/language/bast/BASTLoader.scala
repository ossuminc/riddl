/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.bast

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{Contents, *}
import com.ossuminc.riddl.language.{At, Messages}
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.utils.{PlatformContext, URL}

import scala.collection.mutable

/** Utility for loading BAST imports.
  *
  * This loads BAST files referenced by BASTImport nodes and populates their
  * contents field with the loaded Nebula contents. Imports can appear at the
  * root level, inside domains, or inside contexts.
  *
  * Supports selective imports that import a specific definition by kind and name,
  * with optional aliasing to rename the imported definition.
  */
object BASTLoader {

  /** Result of loading BAST imports */
  case class LoadResult(
    loadedCount: Int,
    failedCount: Int,
    messages: Messages
  )

  /** Load all BAST imports in a Root, including those inside domains and contexts.
    *
    * Finds all BASTImport nodes in the Root, its domains, and contexts, loads the
    * referenced BAST files, and populates each BASTImport's contents field with
    * the loaded Nebula contents. Handles selective imports by filtering to the
    * specified definition.
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
      loadAndProcessImport(bi, baseURL) match {
        case Right(items) =>
          // Copy filtered items into BASTImport contents
          items.foreach { item =>
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
        case c: Context => processContents(c.contents)
        case _ => () // Not a BASTImport, Domain, or Context, skip
      }
    }

    processContents(root.contents)
    LoadResult(loaded, failed, msgs.toList)
  }

  /** Load and process a single BAST import, applying selective filtering and aliasing.
    *
    * @param bi The BASTImport to load
    * @param baseURL The base URL for resolving relative paths
    * @param pc The platform context
    * @return Either an error message or the sequence of items to import
    */
  private def loadAndProcessImport(
    bi: BASTImport,
    baseURL: URL
  )(using pc: PlatformContext): Either[String, Seq[NebulaContents]] = {
    BASTLoaderPlatform.loadSingleImport(bi, baseURL).flatMap { nebula =>
      if bi.isSelective then
        // Selective import: find the specific definition
        val kind = bi.kindOpt.get
        val selectorName = bi.selector.get.value
        findDefinition(nebula, kind, selectorName) match {
          case Some(defn) =>
            // Apply alias if present
            val finalDefn = bi.alias match {
              case Some(newId) => renameDefinition(defn, newId)
              case None => defn
            }
            Right(Seq(finalDefn))
          case None =>
            Left(s"Definition '$kind $selectorName' not found in BAST file '${bi.path.s}'")
        }
      else
        // Full import: load all contents
        Right(nebula.contents.toSeq)
    }
  }

  /** Find a definition by kind and name in a Nebula, searching recursively.
    *
    * @param nebula The Nebula to search
    * @param kind The kind of definition ("domain", "context", "type", etc.)
    * @param name The name of the definition to find
    * @return The found definition, or None if not found
    */
  private def findDefinition(nebula: Nebula, kind: String, name: String): Option[NebulaContents] = {
    def matchesKindAndName(defn: RiddlValue): Boolean = {
      defn match {
        case d: Domain if kind == "domain" => d.id.value == name
        case c: Context if kind == "context" => c.id.value == name
        case e: Entity if kind == "entity" => e.id.value == name
        case t: Type if kind == "type" => t.id.value == name
        case ep: Epic if kind == "epic" => ep.id.value == name
        case s: Saga if kind == "saga" => s.id.value == name
        case a: Adaptor if kind == "adaptor" => a.id.value == name
        case f: Function if kind == "function" => f.id.value == name
        case p: Projector if kind == "projector" => p.id.value == name
        case r: Repository if kind == "repository" => r.id.value == name
        case s: Streamlet if kind == "streamlet" => s.id.value == name
        case a: Author if kind == "author" => a.id.value == name
        case m: Module if kind == "module" => m.id.value == name
        case u: User if kind == "user" => u.id.value == name
        case c: Connector if kind == "connector" => c.id.value == name
        case c: Constant if kind == "constant" => c.id.value == name
        case i: Invariant if kind == "invariant" => i.id.value == name
        case _ => false
      }
    }

    def searchContents[T <: RiddlValue](contents: Contents[T]): Option[NebulaContents] = {
      // First check top-level items
      contents.toSeq.collectFirst {
        case defn: NebulaContents if matchesKindAndName(defn) => defn
      }.orElse {
        // Then search recursively in containers
        contents.toSeq.collectFirst {
          case d: Domain =>
            searchContents(d.contents).orElse(if matchesKindAndName(d) then Some(d) else None)
          case c: Context =>
            searchContents(c.contents).orElse(if matchesKindAndName(c) then Some(c) else None)
          case m: Module =>
            searchContents(m.contents).orElse(if matchesKindAndName(m) then Some(m) else None)
        }.flatten
      }
    }

    searchContents(nebula.contents)
  }

  /** Rename a definition by replacing its identifier with a new one.
    *
    * @param defn The definition to rename
    * @param newId The new identifier
    * @return A copy of the definition with the new identifier
    */
  private def renameDefinition(defn: NebulaContents, newId: Identifier): NebulaContents = {
    defn match {
      case d: Domain => d.copy(id = newId)
      case c: Context => c.copy(id = newId)
      case e: Entity => e.copy(id = newId)
      case t: Type => t.copy(id = newId)
      case ep: Epic => ep.copy(id = newId)
      case s: Saga => s.copy(id = newId)
      case a: Adaptor => a.copy(id = newId)
      case f: Function => f.copy(id = newId)
      case p: Projector => p.copy(id = newId)
      case r: Repository => r.copy(id = newId)
      case s: Streamlet => s.copy(id = newId)
      case a: Author => a.copy(id = newId)
      case m: Module => m.copy(id = newId)
      case u: User => u.copy(id = newId)
      case c: Connector => c.copy(id = newId)
      case c: Constant => c.copy(id = newId)
      case i: Invariant => i.copy(id = newId)
      case other => other // Can't rename, return as-is
    }
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
