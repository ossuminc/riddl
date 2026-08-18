/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.utils

import scala.scalajs.js.annotation.JSExportTopLevel
import org.scalajs.dom

/** This is the JVM version of the Loader utility. It is used to load file content in UTF-8 via a
  * URL as a String and returning the Future that will obtain it. Further processing can be chained
  * onto the future. This handles the I/O part of parsing in a platform specific way.
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
          Future.failed(
            new Exception(s"GET failed with status ${response.status} ${response.statusText}")
          )
        } else {
          response.text().toFuture
        }
      }
  }

  /** [1.3]: bytes, not text. Same `dom.fetch` [[load]] uses, taking `arrayBuffer()` rather than
    * `text()` so binary content survives. A `file://` URL is unreachable from a browser, which is
    * what [[load]] already says by throwing.
    */
  override def loadBytes(url: URL): Future[Array[Byte]] = {
    import org.scalajs.dom.RequestInit
    import org.scalajs.dom.HttpMethod
    import scala.scalajs.js.typedarray.{ArrayBuffer, Int8Array}
    if url.scheme == "file" then throw FileNotFoundException(url)
    else
      val requestInit = new RequestInit { method = HttpMethod.GET }
      dom.fetch(url.toExternalForm, requestInit).toFuture.flatMap { response =>
        if response.status != 200 then {
          Future.failed(
            new Exception(s"GET failed with status ${response.status} ${response.statusText}")
          )
        } else {
          response.arrayBuffer().toFuture.map { (buf: ArrayBuffer) =>
            val view = new Int8Array(buf)
            Array.tabulate(view.length)(i => view(i))
          }
        }
      }
  }

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
