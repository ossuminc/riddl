/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.utils

object ExceptionUtils {
  // NOTE: Using commons.lang3 library from Apache doesn't work Natively, hence this facade
  def getRootCauseStackTrace(exception: Throwable): Array[String] = Array.empty[String]

}
