/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.bast

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.utils.{PlatformContext, URL}

/** JavaScript implementation of BAST loading - throws exception since file I/O is not supported. */
private[bast] object BASTLoaderPlatform {

  /** BAST import loading is not supported on JavaScript platform.
    *
    * The browser environment cannot perform local file I/O operations required
    * to load BAST files. This is an inherent platform limitation.
    *
    * @throws UnsupportedOperationException always
    */
  def loadSingleImport(bi: BASTImport, baseURL: URL)(using pc: PlatformContext): Either[String, Nebula] = {
    Left("BAST import loading is not supported on JavaScript platform. " +
         "The browser cannot perform local file I/O operations.")
  }
}
