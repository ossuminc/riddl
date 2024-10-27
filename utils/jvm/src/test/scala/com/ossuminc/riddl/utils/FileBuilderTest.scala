/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.utils

import org.scalatest._
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.must.Matchers

/** Unit Tests For FileBuilder */
class FileBuilderTest extends AnyWordSpec with Matchers {

  class TestFileBuilder extends FileBuilder {
    def indentLine(str: String, indent: Int =0): Unit = {
      for i <- 1 to indent do incr
      addIndent(str)
      for i <- 1 to indent do decr
    }
  }
  "FileBuilder" should {
    "handle indent" in {
      val fb = new TestFileBuilder
      fb.indentLine("hello world", 2)
      val result = fb.toString
      result mustBe "    hello world"
    }
  }
}
