package com.ossuminc.riddl.utils

import scala.scalajs.js
import scala.scalajs.js.annotation._

implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

@JSExportTopLevel("URL")
case class URL(url: String) extends AnyRef {
  require(url.matches("(file:///|https?://).*"),"Invalid URL syntax")
  @JSExport override def toString: String = url
  @JSExport def toExternalForm: String = url

  @JSExport
  def getFile: String = {
    val spot = url.lastIndexOf('/')
    url.takeRight(url.length - spot)
  }

  inline def root: URL = {
    URL(url.dropRight(this.getFile.length))
  }

  inline def resolve(pathElement: String): URL = {
    URL(url + "/" + pathElement)
  }
}
