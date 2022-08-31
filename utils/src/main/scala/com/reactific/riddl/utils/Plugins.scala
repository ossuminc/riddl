package com.reactific.riddl.utils

import java.nio.file.Path

object Plugins {

  trait PluginInterface {
    def name: String
    def buildInfo: RiddlBuildInfo

  }

  def getPluginsFrom[I <: PluginInterface](dir: Path): Seq[I] = {

  }
  def foo: Unit = {
    import java.io.File
    import java.net.URLClassLoader
    val dir = new File("put path to classes you want to load here")
    val loadPath = dir.toURI.toURL
    val classUrl = Array[Nothing](loadPath)

    val cl = new URLClassLoader(classUrl)

    val loadedClass = cl.loadClass("classname") // must be in package.class name format

  }
}
