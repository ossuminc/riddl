package com.reactific.riddl.utils

import java.nio.file.Path

/** Tests For Plugins */
class PluginSpec extends PluginSpecBase() {

  "Plugin" should {
    "load a plugin" in {
      val plugin = new TestPlugin // required empty constructor
      val plugins = Plugin.loadPluginsFrom[PluginInterface](tmpDir)
      plugins mustNot be(empty)
      plugins.forall(_.getClass == plugin.getClass) mustBe true
    }
  }
}

class FuturePluginSpec extends PluginSpecBase (
  svcClassPath = Path.of("com/reactific/riddl/utils/FutureTestPlugin.class"),
  implClassPath = Path.of("com/reactific/riddl/utils/FutureTestPlugin.class")
)  {
  "FuturePlugin" should {
    "not load" in {
      val exception = intercept[IllegalArgumentException] {
        Plugin.loadPluginsFrom[FutureTestPlugin](tmpDir)
      }
      exception.getMessage.contains("interface version 1") must be(true)
    }
  }
}
