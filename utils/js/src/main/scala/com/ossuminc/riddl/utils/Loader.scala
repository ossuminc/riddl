/*
 * Copyright 2024 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.utils

import scala.scalajs.js.annotation.{JSExportTopLevel, JSExport}
import org.scalajs.dom

/** This is the JVM version of the Loader utility. It is used to load file content in UTF-8 via a URL as a String and
  * returning the Future that will obtain it. Further processing can be chained onto the future. This handles the I/O
  * part of parsing in a platform specific way.
  */
@JSExportTopLevel("Loader")
case class Loader(url: URL) {

  import scala.concurrent.ExecutionContext.global
  import scala.concurrent.{ExecutionContext, Future}
  import scala.scalajs.js.annotation.JSExport
  import scalajs.js

  @JSExport
  def load(implicit ec: ExecutionContext = global): Future[String] = {
    import org.scalajs.dom.RequestInit
    import org.scalajs.dom.HttpMethod
    val requestInit = new RequestInit { method = HttpMethod.GET }
    dom.fetch(url.toExternalForm, requestInit).toFuture.flatMap { response =>
      if response.status != 200 then {
        Future.failed(new Exception(s"GET failed with status ${response.status} ${response.statusText}"))
      } else {
        response.text().toFuture
      }
    }
  }
}
