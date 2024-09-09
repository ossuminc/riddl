package com.ossuminc.riddl.utils

object ExceptionUtils {

  // NOTE: Using commons.lang3 library from Apache doesn't work Native platform, 
  //  hence this facade that just uses facilities in Throwable
  def getRootCauseStackTrace(exception: Throwable): Array[String] = {
    val headline = exception.getClass.getName + ": " + exception.getMessage + ":\n"
    val stackTrace = exception.getStackTrace.map { ste =>
      ste.toString + "\n"
    }
    headline +: stackTrace
  }
}
