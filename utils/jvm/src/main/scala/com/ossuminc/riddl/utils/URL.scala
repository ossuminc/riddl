package com.ossuminc.riddl.utils

import scala.concurrent.Future

case class URL(url_as_string: String) {

  val url: java.net.URL = java.net.URI.create(url_as_string).toURL
  inline override def toString: String = url.toString
  inline def toExternalForm: String = url.toExternalForm
  inline def getFile: String = url.getFile
}

object URL {
  def load(url: URL): Future[Seq[String]] = {
    implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global
    import scala.io.Source
    Future {
      val source = Source.fromURL(url.url)
      try {
        source.getLines.toSeq
      } finally {
        source.close()
      }
    }
  }
}
