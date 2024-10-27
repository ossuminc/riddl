/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.testkit

import com.ossuminc.riddl.language.parsing
import com.ossuminc.riddl.utils.{pc,ec}
import org.scalatest.Suite

class ParsingTestTest extends ParsingTest {
  val delegate: Suite = new parsing.ParsingTestTest {}
  delegate.execute()
}
