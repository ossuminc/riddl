package com.ossuminc.riddl.utils

object ExceptionUtils {
  // NOTE: Can't get exception stack traces from Scala in JS Environment
  def getRootCauseStackTrace(exception: Throwable): Array[String] = Array.empty[String]
}
