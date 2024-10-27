/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.utils

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.must.Matchers

class LoaderTest extends AnyWordSpec with Matchers {

  "Loader" must {
    "load" in {
      import scala.concurrent.duration.DurationInt
      val url = URL(
        "https://raw.githubusercontent.com/ossuminc/riddl/main/language/jvm/src/test/input/domains/rbbq.riddl"
      )
      val contentF = JVMPlatformContext().load(url)
      val content = Await.result(contentF, 5.seconds)
      // info(content)
    }
  }
}
