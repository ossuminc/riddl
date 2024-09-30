/*
 * Copyright 2024 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.utils

import java.nio.file.Path
import scala.concurrent.ExecutionContext.Implicits.global
import scala.io.Source
import scala.scalajs.js.annotation.{JSExport, JSExportTopLevel}

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
  def load: Future[String] = {
    url.scheme match
      case "file" =>
        val path = Path.of(url.toFullPathString)
        Future { Source.fromFile(path.toFile).getLines().mkString("\n") }
      case "http" | "https" =>
        val uri = java.net.URI.create(url.toString).toURL
        Future.successful { "not implemented" } // TODO: write this properly 

  }
}
