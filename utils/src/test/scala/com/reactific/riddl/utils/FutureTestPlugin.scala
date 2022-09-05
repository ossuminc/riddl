package com.reactific.riddl.utils

/** A very simple plugin for testing */
class FutureTestPlugin extends PluginInterface {
  val pluginName: String = "TestPlugin"
  val pluginVersion: String = "0.0.0"
  override val interfaceVersion: Int = Plugin.interfaceVersion + 1
}
