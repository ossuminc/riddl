package com.ossuminc.riddl.utils

import scala.concurrent.Future
import org.scalajs.dom
import org.scalajs.dom.HttpMethod
import org.scalajs.dom.RequestInit

import scala.scalajs.js
import scala.scalajs.js.annotation._

implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

@JSExportTopLevel("URL")
case class URL(url: String) extends AnyRef {
  @JSExport override def toString: String = url
  @JSExport
  def toExternalForm: String = ""

  @JSExport
  def getContents: Seq[String] = {
    import scala.concurrent.Await
    import scala.concurrent.duration.DurationInt
    val future: Future[String] = dom
      .fetch(url, new RequestInit { method = HttpMethod.GET })
      .toFuture
      .flatMap(resp => {
        if resp.status != 200 then {
          throw Exception(s"GET failed with status ${resp.statusText}")
        }
        resp.text().toFuture
      })
    val result: String = Await.result[String](future, 30.seconds)
    result.split('\n').toIndexedSeq
  }
  @JSExport
  def getFile: String = {
    val spot = url.lastIndexOf('/')
    url.takeRight(url.length - spot)
  }

}
