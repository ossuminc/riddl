package com.reactific.riddl.utils

import java.nio.file.{Files, Path}
import java.net.URL
import java.util.ServiceLoader
import java.util.concurrent.atomic.AtomicBoolean
import scala.jdk.StreamConverters.*
import scala.jdk.CollectionConverters.*
import scala.reflect.{ClassTag, classTag}

object Plugin {

  final private [utils] val interfaceVersion: Int = 1
  final private val loading = new AtomicBoolean

  def loadPluginsFrom[T <: PluginInterface : ClassTag](pluginsDir: Path): List[PluginInterface] = {
    require(
      loading.compareAndSet(false, true),
      "Plugins are already loading!"
    )
    require(Files.isDirectory(pluginsDir) && Files.isReadable(pluginsDir),
      s"Plugin directory $pluginsDir is not a readable directory"
    )
    val stream = Files.list(pluginsDir)

    val jars = stream.filter(_.getFileName.toString.endsWith(".jar"))
      .toScala(List)
      .sortBy(_.toString)
    stream.close()

    val urls = for { path <- jars } yield {
      require(Files.isRegularFile(path),
        s"Candidate plugin $path is not a regular file")
      require(Files.isReadable(path),
        s"Candidate plugin $path is not a regular file")
      new URL("jar", "", -1, path.toAbsolutePath.toString)
    }
    val pluginClassLoader = PluginClassLoader(urls, getClass.getClassLoader)
    val savedClassLoader = Thread.currentThread.getContextClassLoader
    try {
      Thread.currentThread.setContextClassLoader(pluginClassLoader)
      val svcType = classTag[T].runtimeClass.asInstanceOf[Class[T]]
      val loader = ServiceLoader.load(svcType, pluginClassLoader)
      val list = loader.iterator().asScala.toList
      val result = for {
        plugin <- list
      } yield {
        require(plugin.interfaceVersion <= interfaceVersion,
          s"Plugin ${plugin.getClass.getSimpleName} of interface version ${
            plugin.interfaceVersion} cannot be supported by Plugin system at "
          ++ s"interface version $interfaceVersion.")
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
      }
      result
    } finally {
      Thread.currentThread.setContextClassLoader(savedClassLoader)
      require(
        loading.compareAndSet(true, false),
        "Plugins loading was not locked!"
      )
    }
  }
}
