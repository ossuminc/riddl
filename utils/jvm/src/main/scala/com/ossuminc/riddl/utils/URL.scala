package com.ossuminc.riddl.utils

import scala.concurrent.Future

case class URL(url_as_string: String) {

  val url: java.net.URL = java.net.URI.create(url_as_string).toURL
  inline override def toString: String = url.toString
  inline def toExternalForm: String = url.toExternalForm
  inline def getContents: Seq[String] = {
    implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global
    import scala.concurrent.Await
    import scala.concurrent.duration.DurationInt
    import scala.io.Source
    val f= Future {
      val source = Source.fromURL(url)
      try {
        source.getLines.toSeq 
      } finally {
        source.close()
      }
    }
    Await.result(f, 30.seconds)
  }
  inline def getFile: String = url.getFile
}
