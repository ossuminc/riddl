/*
 * Copyright 2024 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.utils

/** This is the JVM version of the Loader utility. It is used to load file content in UTF-8 via a URL as a String and
  * returning the Future that will obtain it. Further processing can be chained onto the future. This handles the I/O
  * part of parsing in a platform specific way.
  */
case class Loader(url: URL) {

  import scala.concurrent.ExecutionContext.global
  import scala.concurrent.{ExecutionContext, Future}
  import scala.io.Source

  def load(implicit ec: ExecutionContext = global): Future[Iterator[String]] = {
    Future {
      import scala.io.Codec
      val jurl = java.net.URI(url.toExternalForm).toURL
      val source = Source.fromURL(jurl)(Codec.UTF8)
      try {
        source.getLines()
      } finally {
        source.close()
      }
    }
  }
}