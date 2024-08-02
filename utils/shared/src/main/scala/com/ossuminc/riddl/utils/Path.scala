package com.ossuminc.riddl.utils

/**
  * A replacement for [[java.nio.file.Path]] that aims to support only the relative
  * path in a [[com.ossuminc.riddl.utils.URL]]. A [[Path]] is always relative to the
  * URL from which the
  */
case class Path(path: String) {
  // make sure the string passed really is a "Path"
  require(path.matches("[A-Za-z0-9_-](\\.[A-Za-z0-9_-])*"))
}

object Path {
  def of(path: String) = new Path(path)
}