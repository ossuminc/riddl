/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.translate

import java.nio.file.Path

/** A base trait for the options that a TranslatingPass must have  */
trait TranslatingOptions  {
  def inputFile: Option[Path]
  def outputDir: Option[Path]
  def projectName: Option[String]
}
