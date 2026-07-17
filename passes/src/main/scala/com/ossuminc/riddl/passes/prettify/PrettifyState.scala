/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.prettify

import com.ossuminc.riddl.language.AST.BASTImport
import com.ossuminc.riddl.utils.{PlatformContext, URL}

import scala.collection.mutable
import scala.scalajs.js.annotation.JSExport

case class PrettifyState(flatten: Boolean = false, topFile: String = "prettify-output.riddl", outDir: String = ".", inputDir: String)(using PlatformContext):

  // Strip leading/trailing '/' from outDir for URL basis field, which
  // already gets '/' separators in URL format (scheme://authority/basis/path)
  private val normalizedOutDir: String =
    val stripped = if outDir.startsWith("/") then outDir.drop(1) else outDir
    if stripped.endsWith("/") then stripped.dropRight(1) else stripped

  // Prefix to strip from include paths so output files are relative
  // to the main input file's directory, not to CWD
  private val inputDirPrefix: String =
    if inputDir.isEmpty then ""
    else
      val normalized = inputDir.replace('\\', '/')
      if normalized.endsWith("/") then normalized else normalized + "/"

  def filesAsString: String = {
    closeStack()
    files.map(fe => fe.toString).mkString("\n")
  }

  def pushFile(file: RiddlFileEmitter): Unit = {
    fileStack.push(file)
    files.append(file)
  }

  def popFile(): Unit = { fileStack.pop() }

  inline private def current: RiddlFileEmitter = fileStack.head

  def withCurrent(f: RiddlFileEmitter => Unit): this.type = {
    f(current);
    this
  }

  def toDestination(url: URL): URL = {
    val relativePath =
      if inputDirPrefix.nonEmpty && url.path.startsWith(inputDirPrefix) then
        url.path.drop(inputDirPrefix.length)
      else url.path
    URL(url.scheme, url.authority, normalizedOutDir, relativePath)
  }
  def numFiles: Int = files.length

  def withFiles(f: RiddlFileEmitter => Unit): Unit =
    closeStack()
    files.foreach(f)

  private val files: mutable.ListBuffer[RiddlFileEmitter] = mutable.ListBuffer.empty[RiddlFileEmitter]

  private val fileStack: mutable.Stack[RiddlFileEmitter] = mutable.Stack
    .empty[RiddlFileEmitter]

  private val bastImports: mutable.ListBuffer[BASTImport] = mutable.ListBuffer.empty

  def addBASTImport(bi: BASTImport): Unit = bastImports.append(bi)
  def getBastImports: Seq[BASTImport] = bastImports.toSeq

  private def closeStack(): Unit = { while fileStack.nonEmpty do popFile() }

  // Get the ball rolling, this creates the first RFE and pushes it on to fileStack
  pushFile(RiddlFileEmitter(toDestination(URL.fromCwdPath(topFile))))
end PrettifyState
