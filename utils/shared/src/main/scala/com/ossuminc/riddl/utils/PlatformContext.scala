/*
 * Copyright 2019-2025 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.utils

import scala.collection.convert.StreamExtensions
import scala.concurrent.{ExecutionContext, Future}

/** This trait allows RIDDL to abstract away its IO operations. Several places in RIDDL declare a `using` clause with
  * this trait in order to allow RIDDL to invoke synchronous and asynchronous I/O operations. This allows RIDDL's
  * pure-scala implementation to be used with: JVM, scala-native, scala-js for Browser, scala-js for Node, or any other
  * environment that supports simple input/output operations on files.
  */
trait PlatformContext {

  given pc: PlatformContext = this

  /** The Logger instance to use on this platform. */
  protected var logger: Logger = SysLogger()
  def log: Logger = logger
  def withLogger[T,L <: Logger](newLogger: L)(doIt: (L) => T): T = synchronized {
    val save = logger
    logger = newLogger
    val result = doIt(newLogger)
    logger = save
    result
  }

  /** The default CommonOptions to use on this platform but not publicly available */
  protected var options_ : CommonOptions = CommonOptions()

  /** The public accessor to get the current options */
  def options: CommonOptions = options_

  /** Do a task with a different set of options and then return to what they were */
  def withOptions[T](newOptions: CommonOptions)(doIt: (options: CommonOptions) => T): T = {
    val cachedOptions = options_
    synchronized {
      options_ = newOptions
      val result = doIt(newOptions)
      options_ = cachedOptions
      result
    }
  }

  /** The ExecutionContext that will be used for Futures and Promises */
  def ec: ExecutionContext

  /** Load the content of a text file asynchronously and return it as a string. THe content, typically a RIDDL or
    * Markdown file, is expected to be encoded in UTF-8
    * @param url
    *   The URL to specify the file to load. This should specify the `file://` protocol.
    * @return
    *   The content of the file as a String, asynchronously in a Future
    */
  def load(url: URL): Future[String]

  /** Read the entire contents of a file and return it, synchronously
    *
    * @param file
    *   The file to read.
    * @return
    */
  def read(file: URL): String

  /** Write the provided content to a file
    *
    * @param file
    *   The file to be written.
    * @param content
    *   The content to write
    */
  def write(file: URL, content: String): Unit

  /** Write a message to the standard output or equivalent for this platform
    *
    * @param message
    *   The message to write to the standard output
    */
  def stdout(message: String): Unit

  /** Write a newline appended message to the stnadard output or equivalent for this platform
    *
    * @param message
    *   The message to write to the standard output
    */
  def stdoutln(message: String): Unit

  /** The newline character for this platform */
  def newline: String

}
