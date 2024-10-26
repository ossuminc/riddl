/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.utils

class URLTest2 extends AbstractTestingBasis {

  "URL" should {
    "create with fromCwdPath" in {
      val cwd = Option(System.getProperty("user.dir")).getOrElse("").drop(1)
      val url = URL.fromCwdPath("utils/jvm/input/diff1/a.txt")
      url.toExternalForm must be(s"file:///$cwd/utils/jvm/input/diff1/a.txt")
    }
    "create with fromFullPath" in {
      val url = URL.fromFullPath("/this/is/a/full/path")
      url.basis must be("this/is/a/full")
      url.path must be("path")
    }
  }
}
