/*
 * Copyright 2019-2026 Ossum Inc.
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
  * This loads BAST files referenced by BASTImport nodes and populates their contents field with the
  * contents of the loaded Module (the BAST serialization root). Imports can appear at the root
  * level, inside modules, domains and contexts; the walk below is generic over [[AST.Container]] so
  * an import nested at any depth (including inside `include` wrappers) is found.
  *
  * Supports selective imports that import a specific definition by kind and name, with optional
  * aliasing to rename the imported definition.
  *
  * '''Loading populates wrappers only.''' The loaded definitions land inside the [[AST.BASTImport]]
  * node itself; they are NOT spliced into the enclosing container. A `HierarchyPass` (prettify,
  * jsonify) descends into the wrapper without pushing it onto the parent stack, so those surfaces
  * already see the loaded definitions as members of the enclosing container. The plain `Pass`
  * traversal that Symbols/Resolution/Validation use does NOT descend, so an imported definition is
  * not in the symbol table and cannot be referenced yet.
  *
  * '''A self-contained model therefore requires an explicit flatten''' — `FlattenPass`,
  * `RiddlLib.flattenAST`, or `Container.flatten()` — which promotes the loaded definitions into the
  * container that holds the directive. Flattening is deliberately opt-in and is NOT part of
  * `Pass.standardPasses`: the unflattened tree is what re-emits and re-serializes the directive
  * itself instead of its expansion.
  */
object BASTLoader {

  /** Turn the path written in a load directive into the URL to read.
    *
    * Three shapes are accepted: an already-valid `file:`/`http(s):` URL, an absolute filesystem
    * path (`/a/b/lib.bast` — `URL.resolve` rejects a leading `/`, so it must go through
    * `fromFullPath`), and a path relative to the file that holds the directive.
    */
  private[bast] def resolveBastURL(path: String, baseURL: URL): URL =
    if URL.isValid(path) then URL(path)
    else if path.startsWith("/") then URL.fromFullPath(path)
    else baseURL.parent.resolve(path)
  end resolveBastURL

  /** Apply `f` to every [[AST.BASTImport]] reachable from `container`, at any depth.
    *
    * The walk descends into every nested [[AST.Container]] — definitions, `include` wrappers and
    * modules alike — so an import is found wherever the grammar allows one. It deliberately does
    * NOT descend into a BASTImport's own contents: those are loaded content, not source, and an
    * import cannot nest inside another import.
    */
  private def foreachImport(container: Container[?])(f: BASTImport => Unit): Unit = {
    def walk[T <: RiddlValue](contents: Contents[T]): Unit = contents.foreach {
      case bi: BASTImport  => f(bi)
      case c: Container[?] => walk(c.contents)
      case _               => () // a leaf; nothing to descend into
    }
    walk(container.contents)
  }

  /** Result of loading BAST imports */
  case class LoadResult(
    loadedCount: Int,
    failedCount: Int,
    messages: Messages
  )

  /** Load all BAST imports reachable from `container`, at any depth.
    *
    * Finds every BASTImport node, loads the referenced BAST file, and populates that import's
    * contents field with the loaded Module contents. Handles selective imports by filtering to the
    * specified definition.
    *
    * @param container
    *   The container (typically a [[AST.Root]] or [[AST.Module]]) holding BASTImport nodes
    * @param baseURL
    *   The base URL for resolving relative BAST file paths
    * @param pc
    *   The platform context for file loading
    * @return
    *   LoadResult with counts and any error messages
    */
  def loadImports(container: Container[?], baseURL: URL)(using pc: PlatformContext): LoadResult = {
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
          // NOTE: avoid "import '" in string literals — ESM shim
          // plugins misinterpret it as an ES module import statement.
          msgs += Messages.Message(
            bi.loc,
            s"Failed to load BAST file '${bi.path.s}': $error",
            Messages.Error
          )
          failed += 1
      }
    }

    foreachImport(container)(loadImport)
    LoadResult(loaded, failed, msgs.toList)
  }

  /** Load and process a single BAST import, applying selective filtering and aliasing.
    *
    * @param bi
    *   The BASTImport to load
    * @param baseURL
    *   The base URL for resolving relative paths
    * @param pc
    *   The platform context
    * @return
    *   Either an error message or the sequence of items to import
    */
  private def loadAndProcessImport(
    bi: BASTImport,
    baseURL: URL
  )(using pc: PlatformContext): Either[String, Seq[NebulaContents]] = {
    BASTLoaderPlatform.loadSingleImport(bi, baseURL).flatMap { module =>
      if bi.isSelective then
        // Selective import: find the specific definition
        val kind = bi.kindOpt.get
        val selectorName = bi.selector.get.value
        findDefinition(module, kind, selectorName) match {
          case Some(defn) =>
            // Apply alias if present
            val finalDefn = bi.alias match {
              case Some(newId) => renameDefinition(defn, newId)
              case None        => defn
            }
            Right(Seq(finalDefn))
          case None =>
            Left(s"Definition '$kind $selectorName' not found in BAST file '${bi.path.s}'")
        }
      else
        // Full import: load all contents
        Right(module.contents.toSeq.collect { case nc: NebulaContents => nc })
    }
  }

  /** Find a definition by kind and name in a Module, searching recursively.
    *
    * @param module
    *   The Module to search
    * @param kind
    *   The kind of definition ("domain", "context", "type", etc.)
    * @param name
    *   The name of the definition to find
    * @return
    *   The found definition, or None if not found
    */
  private def findDefinition(module: Module, kind: String, name: String): Option[NebulaContents] = {
    def matchesKindAndName(defn: RiddlValue): Boolean = {
      defn match {
        case d: Domain if kind == "domain"         => d.id.value == name
        case c: Context if kind == "context"       => c.id.value == name
        case e: Entity if kind == "entity"         => e.id.value == name
        case t: Type if kind == "type"             => t.id.value == name
        case ep: Epic if kind == "epic"            => ep.id.value == name
        case s: Saga if kind == "saga"             => s.id.value == name
        case a: Adaptor if kind == "adaptor"       => a.id.value == name
        case f: Function if kind == "function"     => f.id.value == name
        case p: Projector if kind == "projector"   => p.id.value == name
        case r: Repository if kind == "repository" => r.id.value == name
        case s: Streamlet if kind == "streamlet"   => s.id.value == name
        case a: Author if kind == "author"         => a.id.value == name
        case m: Module if kind == "module"         => m.id.value == name
        case u: User if kind == "user"             => u.id.value == name
        case c: Connector if kind == "connector"   => c.id.value == name
        case c: Constant if kind == "constant"     => c.id.value == name
        case i: Invariant if kind == "invariant"   => i.id.value == name
        case _                                     => false
      }
    }

    // Breadth-first: everything at this level, then every nested container in order. A BASTImport's
    // own contents are skipped — an import re-exports nothing.
    def searchContents[T <: RiddlValue](contents: Contents[T]): Option[NebulaContents] = {
      contents.toSeq
        .collectFirst { case defn: NebulaContents if matchesKindAndName(defn) => defn }
        .orElse {
          contents.toSeq.iterator
            .collect { case c: Container[?] if !c.isInstanceOf[BASTImport] => c }
            .map(c => searchContents(c.contents))
            .collectFirst { case Some(found) => found }
        }
    }

    searchContents(module.contents)
  }

  /** Rename a definition by replacing its identifier with a new one.
    *
    * @param defn
    *   The definition to rename
    * @param newId
    *   The new identifier
    * @return
    *   A copy of the definition with the new identifier
    */
  private def renameDefinition(defn: NebulaContents, newId: Identifier): NebulaContents = {
    defn match {
      case d: Domain     => d.copy(id = newId)
      case c: Context    => c.copy(id = newId)
      case e: Entity     => e.copy(id = newId)
      case t: Type       => t.copy(id = newId)
      case ep: Epic      => ep.copy(id = newId)
      case s: Saga       => s.copy(id = newId)
      case a: Adaptor    => a.copy(id = newId)
      case f: Function   => f.copy(id = newId)
      case p: Projector  => p.copy(id = newId)
      case r: Repository => r.copy(id = newId)
      case s: Streamlet  => s.copy(id = newId)
      case a: Author     => a.copy(id = newId)
      case m: Module     => m.copy(id = newId)
      case u: User       => u.copy(id = newId)
      case c: Connector  => c.copy(id = newId)
      case c: Constant   => c.copy(id = newId)
      case i: Invariant  => i.copy(id = newId)
      case other         => other // Can't rename, return as-is
    }
  }

  /** Check if a container holds any unloaded BASTImport nodes, at any depth.
    *
    * @param container
    *   The container to check
    * @return
    *   true if there are BASTImport nodes with empty contents
    */
  def hasUnloadedImports(container: Container[?]): Boolean = {
    var found = false
    foreachImport(container) { bi => if bi.contents.isEmpty then found = true }
    found
  }

  /** Get all BASTImport nodes reachable from a container, at any depth.
    *
    * @param container
    *   The container to search
    * @return
    *   Sequence of BASTImport nodes
    */
  def getImports(container: Container[?]): Seq[BASTImport] = {
    val result = mutable.ListBuffer.empty[BASTImport]
    foreachImport(container) { bi => result += bi }
    result.toSeq
  }
}
