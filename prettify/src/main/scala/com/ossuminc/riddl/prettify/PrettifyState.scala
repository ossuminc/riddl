/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.prettify

import com.ossuminc.riddl.utils.URL
import scala.collection.mutable

case class PrettifyState(flatten: Boolean = false, topFile: String = "nada", outDir: String = "nada"):

  def filesAsString: String = {
    closeStack()
    files.map(fe => fe.toString).mkString
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
  
  private val files: mutable.ListBuffer[RiddlFileEmitter] = mutable.ListBuffer.empty[RiddlFileEmitter]

  private val fileStack: mutable.Stack[RiddlFileEmitter] = mutable.Stack
    .empty[RiddlFileEmitter]

  private def closeStack(): Unit = { while fileStack.nonEmpty do popFile() }

  // Get the ball rolling, this creates the first RFE and pushes it on to fileStack
  pushFile(RiddlFileEmitter(toDestination(URL.fromCwdPath(topFile))))
end PrettifyState
