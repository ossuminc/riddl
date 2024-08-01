package com.ossuminc.riddl.utils

import scala.concurrent.Future
import scala.util.{Try, Success, Failure}
import org.scalajs.dom
import org.scalajs.dom.HttpMethod
import org.scalajs.dom.RequestInit
import org.scalajs.dom.RequestInfo

import scala.scalajs.js
import scala.scalajs.js.annotation._

implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

@JSExportTopLevel("URL")
case class URL(url: String) extends AnyRef {

  import java.util.concurrent.TimeUnit
  import scala.concurrent.duration.{FiniteDuration, TimeUnit}

  @JSExport override def toString: String = url
  @JSExport def toExternalForm: String = url

  @JSExport
  def getFile: String = {
    val spot = url.lastIndexOf('/')
    url.takeRight(url.length - spot)
  }

}

@JSExportTopLevel("URL$")
object URL {
  @JSExport
  def load(url: URL): Future[Seq[String]] = {
    val info: RequestInfo = url.url
    dom.fetch(info, new RequestInit { method = HttpMethod.GET })
      .toFuture
      .flatMap(resp => {
        if resp.status != 200 then {
          throw Exception(s"GET failed with status ${resp.statusText}")
        }
        resp.text().toFuture.map(_.split('\n').toIndexedSeq)
      }
    )
  }
}
