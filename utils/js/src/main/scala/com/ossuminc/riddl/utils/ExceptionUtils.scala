package com.ossuminc.riddl.utils

object ExceptionUtils {

  import scala.scalajs.js.annotation.JSExport

  // NOTE: Can't get exception stack traces from Scala in JS Environment
  @JSExport
  def getRootCauseStackTrace(exception: Throwable): Array[String] = Array.empty[String]
}
