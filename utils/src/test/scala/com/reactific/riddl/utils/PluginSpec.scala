package com.reactific.riddl.utils

/** Tests For Plugins */
class PluginSpec extends PluginSpecBase() {

  "PluginSpec" should {
    "load a plugin" in {
      val plugin = new TestPlugin // required empty constructor
      val plugins = Plugin.loadPluginsFrom(tmpDir)
      plugins mustNot be(empty)
      plugins.forall(_.getClass == plugin.getClass) mustBe true
    }
  }
}
