package com.reactific.riddl.utils

/** A very simple plugin for testing */
case class TestPlugin(
  pluginName: String = "TestPlugin",
  pluginVersion: String = "0.0.0")
    extends Plugin.Interface
