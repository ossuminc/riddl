package com.ossuminc.riddl.utils

import scala.scalajs.js
import scala.scalajs.js.annotation.*

@JSExportTopLevel("URL")
case class URL(url: String) extends AnyRef {
  require(isValid, s"Invalid URL syntax: $url")

  @JSExport def isEmpty: Boolean = url.isEmpty

  @JSExport def isValid: Boolean = URL.isValid(url)

  @JSExport override def toString: String = url

  @JSExport def toExternalForm: String = url

  @JSExport def getFile: String = {
    val spot = url.lastIndexOf('/')
    url.takeRight(url.length - spot)
  }

  @JSExport def root: URL = {
    URL(url.dropRight(this.getFile.length))
  }

  @JSExport def parent: URL = {
    val index = url.lastIndexOf('/')
    URL(url.substring(0,index))
  }
  
  @JSExport def resolve(pathElement: String): URL = {
    URL(url + "/" + pathElement)
  }
}

@JSExportTopLevel("URL$")
object URL {
  @JSExport val empty: URL = URL("")
  @JSExport def isValid(url: String): Boolean = url.isEmpty || url.matches("(file:|https?://).*")

}
