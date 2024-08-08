/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.utils

/** Tests For Plugins */
class PluginSpec extends PluginSpecBase() {

  "Plugin" should {
    "load a plugin" in {
      val plugin = new TestPlugin // required empty constructor
      val plugins = Plugin.loadPluginsFrom[PluginInterface](tmpDir)
      plugins mustNot be(empty)
      plugins.exists(_.getClass == plugin.getClass) mustBe true
      plugins.find(_.getClass == plugin.getClass) match {
        case Some(plugin) =>
          plugin.asInstanceOf[TestPlugin].pluginVersion mustBe("0.0.0")
        case None => fail("Didn't find the plugin class")
      }

    }
  }
}
