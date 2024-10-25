/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.prettify

import com.ossuminc.riddl.utils.URL

import scala.collection.mutable
import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}

@JSExportTopLevel("PrettifyState")
case class PrettifyState(flatten: Boolean = false, topFile: String = "nada", outDir: String = "nada"):

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
    URL(url.scheme, url.authority, outDir, url.path)
  }
  def numFiles: Int = files.length

  def withFiles(f: RiddlFileEmitter => Unit): Unit =
    closeStack()
    files.foreach(f)

  private val files: mutable.ListBuffer[RiddlFileEmitter] = mutable.ListBuffer.empty[RiddlFileEmitter]

  private val fileStack: mutable.Stack[RiddlFileEmitter] = mutable.Stack
    .empty[RiddlFileEmitter]

  private def closeStack(): Unit = { while fileStack.nonEmpty do popFile() }

  // Get the ball rolling, this creates the first RFE and pushes it on to fileStack
  pushFile(RiddlFileEmitter(toDestination(URL.fromCwdPath(topFile))))
end PrettifyState
