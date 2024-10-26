/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.utils

import scala.scalajs.js.annotation.JSExportTopLevel

object ExceptionUtils {
  // NOTE: Using commons.lang3 library from Apache doesn't work in Javascript, hence this facade
  def getRootCauseStackTrace(exception: Throwable): Array[String] = {
    import org.apache.commons.lang3.exception.{ExceptionUtils => L3EU}
    L3EU.getRootCauseStackTrace(exception)
  }
}
