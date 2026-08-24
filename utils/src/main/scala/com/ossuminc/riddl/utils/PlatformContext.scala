/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.utils

import scala.collection.convert.StreamExtensions
import scala.concurrent.{ExecutionContext, Future}
import scala.annotation.nowarn
import scala.util.control.NonFatal

/** This trait allows RIDDL to abstract away its IO operations. Several places in RIDDL declare a
  * `using` clause with this trait in order to allow RIDDL to invoke synchronous and asynchronous
  * I/O operations. This allows RIDDL's pure-scala implementation to be used with: JVM,
  * scala-native, scala-js for Browser, scala-js for Node, or any other environment that supports
  * simple input/output operations on files.
  */
trait PlatformContext {

  given pc: PlatformContext = this

  /** The Logger instance to use on this platform. */
  protected var logger: Logger = SysLogger()
  def log: Logger = logger
  def withLogger[T, L <: Logger](newLogger: L)(doIt: (L) => T): T = synchronized {
    val save = logger
    logger = newLogger
    // Restore in a `finally` for the same reason `withOptions` does (2eefeec52): a throwing body
    // otherwise leaves the swapped-in logger installed globally, so every LATER sequential suite
    // writes into a dead test's capture buffer. The failure lands on an innocent test.
    try doIt(newLogger)
    finally logger = save
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
      try doIt(newOptions)
      finally options_ = cachedOptions
    }
  }

  /** The ExecutionContext that will be used for Futures and Promises */
  def ec: ExecutionContext

  /** Load the content of a text file asynchronously and return it as a string. THe content,
    * typically a RIDDL or Markdown file, is expected to be encoded in UTF-8
    * @param url
    *   The URL to specify the file to load. This should specify the `file://` protocol.
    * @return
    *   The content of the file as a String, asynchronously in a Future
    */
  @deprecated("Use loadSafe, which reports failure instead of throwing", "2.0.0")
  def load(url: URL): Future[String]

  /** Load the content of a text file, reporting failure rather than throwing.
    *
    * [[load]] throws — and on the JVM it throws SYNCHRONOUSLY for a missing file, before the Future
    * even exists, so `load(url).recover { … }` does not catch it. The exception then travels to the
    * command-level catch-all and a user sees a Java class name instead of a diagnosis. This is the
    * total version: every expected condition comes back as a [[LoadFailure]], and anything
    * unexpected is caught and reported as [[LoadFailure.Unreachable]] rather than escaping.
    *
    * The returned Future's own failure channel is therefore dead by contract. That redundancy is
    * deliberate: the point is that a caller never has to think about exceptions again.
    *
    * Implemented once here in terms of [[load]], so every platform gets it without reimplementing
    * the classification.
    *
    * @param url
    *   The URL of the file to load, typically with the `file://` scheme.
    * @return
    *   The content, or the reason there is none — never a failed Future.
    */
  // This IS the sanctioned bridge to the deprecated `load`, so the deprecation is expected here
  // and only here.
  @nowarn("cat=deprecation")
  def loadSafe(url: URL): Future[Either[LoadFailure, String]] =
    given ExecutionContext = ec
    try
      load(url)
        .map(Right(_): Either[LoadFailure, String])
        .recover { case NonFatal(x) => Left(LoadFailure.from(url, x)) }
    catch
      // load throws synchronously on the JVM for a missing file or a directory, so catching only
      // the Future's failure would miss exactly the cases this method exists for.
      case NonFatal(x) => Future.successful(Left(LoadFailure.from(url, x)))
    end try
  end loadSafe

  /** Load the BYTES at a URL asynchronously.
    *
    * The binary counterpart of [[load]], and it exists because decoding is lossy: [[load]] returns
    * a `String`, so using it for a ZIP or a `.bast` file corrupts the content. Anything that is not
    * text must come through here.
    *
    * **Added for [1.3].** `PathUtils.copyURLToDir` reached for `java.net.URL.openStream`, which is
    * a STUB on Scala Native (`java_net_url_stubs`) — it compiles and throws
    * `scala.NotImplementedError` when called. That is why the `commands` example-corpus suites
    * aborted on Native while passing on the JVM. Each platform already has a working fetch for
    * text; this asks the same stack for bytes.
    *
    * @param url
    *   The URL to load, `file://` or `http(s)://`.
    * @return
    *   The bytes, asynchronously.
    */
  def loadBytes(url: URL): Future[Array[Byte]]

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

  /** Write a newline appended message to the standard ERROR stream, or this platform's equivalent.
    *
    * Diagnostics belong here, not on stdout: a command whose stdout is a machine-readable artifact
    * (`dump --json`, `find -print`) cannot share that stream with messages, and `--quiet` is not a
    * fix because the run summary prints regardless.
    *
    * **Concrete, with a stdout default, on purpose.** An abstract method here would be a binary
    * incompatibility for every existing implementor of this published trait; defaulting to
    * `stdoutln` means an implementation that does not override it behaves exactly as it did before.
    */
  def stderrln(message: String): Unit = stdoutln(message)

  /** The newline character for this platform */
  def newline: String

}
