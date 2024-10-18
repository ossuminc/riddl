package com.ossuminc.riddl.utils

import java.nio.file.Path
import scala.concurrent.Future

/** This trait allows RIDDL to abstract away its IO operations. Several places in RIDDL declare a `using` clause with
  * this trait in order to allow RIDDL to invoke synchronous and asynchronous I/O operations. This allows RIDDL's
  * pure-scala implementation to be used with: JVM, scala-native, scala-js for Browser, scala-js for Node, or any other
  * environment that supports simple input/output operations on files.
  */
trait PlatformIOContext {

  def load(url: URL): Future[String] = ???
  def dump(url: URL, content: String): Future[Unit] = ???
  def read(file: URL): String = ???
  def write(file: URL, content: String): Unit = ???
  def stdout(message: String): Unit = ???
  def stderr(message: String): Unit = ???
  def log: Logger = ???
  def newline: String = ???
}
