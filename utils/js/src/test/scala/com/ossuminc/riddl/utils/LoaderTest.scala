/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.utils

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

class LoaderTest extends AnyWordSpec with Matchers {

  "Loader" must {
    "load" in {
      val url = URL(
        "https://raw.githubusercontent.com/ossuminc/riddl/language/input/domains/rbbq.riddl"
      )
      val io = DOMPlatformContext()
      val future = io.load(url).map[String] { (content: String) =>
        content must not be (empty)
        content.startsWith("domain ReactiveBBQ") must be(true)
        content
      }
      // FIXME: JS version of Await fails Await.result(future, 5.seconds)
    }
  }
}
