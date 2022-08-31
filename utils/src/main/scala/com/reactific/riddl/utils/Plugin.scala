package com.reactific.riddl.utils

import java.nio.file.{Files, Path}
import java.net.{URL, URLClassLoader}
import java.util.ServiceLoader
import java.util.concurrent.atomic.AtomicBoolean

import scala.jdk.StreamConverters.*

object Plugin {

  final val interfaceVersion: Int = 1

  trait Interface {
    def interfaceVersion: Int = Plugin.interfaceVersion
    def riddlVersion: String = RiddlBuildInfo.version
    def pluginName: String
    def pluginVersion: String
  }

  /** Loads a plugin using a [[URLClassLoader]].
    */
  case class PluginClassLoader(
    val urls: List[URL],
    val parentClassLoader: ClassLoader)
      extends URLClassLoader(urls.toArray, parentClassLoader) {
    @throws[ClassNotFoundException]
    override protected def loadClass(
      name: String,
      resolve: Boolean
    ): Class[_] = { // has the class loaded already?
      var loadedClass: Class[_] = findLoadedClass(name)
      if (loadedClass == null) {
        loadedClass = super.loadClass(name, resolve)
      }
      if (resolve) { // marked to resolve
        resolveClass(loadedClass)
      }
      loadedClass
    }
  }

  final private val loading = new AtomicBoolean

  def loadPluginsFrom(pluginsDir: Path): Seq[Interface] = {
    require(Files.isDirectory(pluginsDir) && Files.isReadable(pluginsDir),
      s"Plugin directory $pluginsDir is not a readable directory"
    )
    require(loading.compareAndSet(false, true),
      "Plugins are already loading"
    )
    val stream = Files.list(pluginsDir)

    val list = stream.filter(_.getFileName.toString.endsWith(".jar"))
      .toScala(List)
      .sortBy(_.toString)
    stream.close()

    val urls = for { path <- list } yield {
      require(Files.isRegularFile(path),
        s"Candidate plugin $path is not a regular file")
      require(Files.isReadable(path),
        s"Candidate plugin $path is not a regular file")
      path.toUri.toURL
    }
    val pluginClassLoader = PluginClassLoader(urls, getClass.getClassLoader)
    val savedClassLoader = Thread.currentThread.getContextClassLoader
    try {
      Thread.currentThread.setContextClassLoader(pluginClassLoader)
      val loaders = ServiceLoader.load(classOf[Interface], pluginClassLoader)
      (for {
        provider <- loaders.stream.toScala(List)
        plugin = provider.get()
       } yield {
        require(plugin.interfaceVersion <= interfaceVersion,
          s"Plugin ${plugin.getClass.getSimpleName} of interface version ${
            plugin.interfaceVersion} cannot be supported by Plugin system at "
          ++ s"interface version ${interfaceVersion}.")
        val pParts = plugin.riddlVersion.split('.')
        require(pParts.length >= 2,
          s"Invalid RIDDL version number; ${plugin.riddlVersion}")
        val pMajor = pParts(0).toInt
        val pMinor = pParts(1).toInt
        require(pMajor >= 0 && pMinor >= 0,
          s"Invalid RIDDL version number; ${plugin.riddlVersion}")
        val rParts = RiddlBuildInfo.version.split('.')
        val rMajor = rParts(0).toInt
        val rMinor = rParts(1).toInt
        require(pMajor <= rMajor && pMinor <= rMinor,
          s"Plugin compiled with future RIDDL version ${plugin.riddlVersion}")
        plugin
      }).toSeq
    } finally {
      Thread.currentThread.setContextClassLoader(savedClassLoader)
    }
  }
}
