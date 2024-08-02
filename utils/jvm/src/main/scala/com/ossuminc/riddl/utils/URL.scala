package com.ossuminc.riddl.utils

import scala.concurrent.Future

case class URL(url_as_string: String) {

  private val url: java.net.URL = java.net.URI.create(url_as_string).toURL
  inline override def toString: String = url.toString
  inline def toExternalForm: String = url.toExternalForm
  inline def getFile: String = url.getFile
  inline def root: URL = {
    URL(url.toExternalForm.dropRight(url.getFile.length))
  }
}

