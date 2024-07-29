/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.utils

import java.nio.file.Path

/** Unit Tests For a plugin with a future version */

class FuturePluginTest
    extends PluginSpecBase(
      svcClassPath =
        Path.of("com/ossuminc/riddl/utils/FutureTestPlugin.class"),
      implClassPath = Path
        .of("com/ossuminc/riddl/utils/FutureTestPlugin.class")
    ) {
  "FuturePlugin" should {
    "not load" in {

      val exception = intercept[IllegalArgumentException] {
        Plugin.loadPluginsFrom[FutureTestPlugin](tmpDir)
      }
      exception.getMessage.contains("interface version 1") must be(true)
    }
  }
}
