/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.utils

import scala.scalajs.js.annotation.JSExportTopLevel
import org.scalajs.dom

/** This is the JVM version of the Loader utility. It is used to load file content in UTF-8 via a URL as a String and
  * returning the Future that will obtain it. Further processing can be chained onto the future. This handles the I/O
  * part of parsing in a platform specific way.
  */
@JSExportTopLevel("DOMPlatformContext")
case class DOMPlatformContext() extends PlatformContext {

  import scala.concurrent.{ExecutionContext, Future}
  import scala.scalajs.js.annotation.JSExport
  import scalajs.js
  
  case class FileNotFoundException(url: URL)
      extends Exception(
        s"Files cannot be loaded from Javascript: ${url.toString}"
      )

  @JSExport
  override def load(url: URL): Future[String] = {
    import org.scalajs.dom.RequestInit
    import org.scalajs.dom.HttpMethod
    if url.scheme == "file" then throw FileNotFoundException(url)
    else
      val requestInit = new RequestInit { method = HttpMethod.GET }
      dom.fetch(url.toExternalForm, requestInit).toFuture.flatMap { response =>
        if response.status != 200 then {
          Future.failed(new Exception(s"GET failed with status ${response.status} ${response.statusText}"))
        } else {
          response.text().toFuture
        }
      }
  }

  override def dump(url: URL, content: String): Future[Unit] = ???

  override def read(url: URL): String = {
    val fr = new dom.FileReader()
    val file: dom.File = ???
    fr.readAsText(file, "utf8")
    "Not Implemented Well: TBD "
  }

  override def write(file: URL, content: String): Unit = {
    ???
  }

  override def stdout(message: String): Unit = dom.console.info(message)

  override def stdoutln(message: String): Unit = dom.console.info(message + newline)

  override def log: Logger = SysLogger()

  override def newline: String = "\n"

  override def ec: ExecutionContext = scala.concurrent.ExecutionContext.global

}
