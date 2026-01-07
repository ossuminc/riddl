/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.utils

object ExceptionUtils {

  import scala.scalajs.js.annotation.JSExport

  // NOTE: Can't get exception stack traces from Scala in JS Environment
  @JSExport
  def getRootCauseStackTrace(exception: Throwable): Array[String] = Array.empty[String]
}
