/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.utils

import java.nio.file.Files
import java.nio.file.Path
import java.net.URL
import java.util.ServiceLoader
import scala.jdk.StreamConverters.*
import scala.jdk.CollectionConverters.*
import scala.reflect.ClassTag
import scala.reflect.classTag

object Plugin {

  final private[utils] val interfaceVersion: Int = 1

  final private val pluginDirEnvVarName = "RIDDL_PLUGINS_DIR"
  final val pluginsDir: Path = Path
    .of(Option(System.getenv(pluginDirEnvVarName)).getOrElse("plugins"))

  def loadPluginsFrom[T <: PluginInterface: ClassTag](
    pluginsDir: Path = pluginsDir
  ): List[T] = {
    val clazz = classTag[T].runtimeClass.asInstanceOf[Class[T]]
    loadSpecificPluginsFrom[T](clazz, pluginsDir)
  }

  def getClassLoader(pluginsDir: Path = pluginsDir): ClassLoader = {
    if (Files.isDirectory(pluginsDir)) {
      val stream = Files.list(pluginsDir)

      val jars = stream.filter(_.getFileName.toString.endsWith(".jar"))
        .toScala(List).sortBy(_.toString)
      stream.close()

      val urls = for {
        path <- jars
      } yield {
        require(
          Files.isRegularFile(path),
          s"Candidate plugin $path is not a regular file"
        )
        require(
          Files.isReadable(path),
          s"Candidate plugin $path is not a regular file"
        )
        new URL("jar", "", -1, path.toAbsolutePath.toString)
      }
      PluginClassLoader(urls, getClass.getClassLoader)
    } else { getClass.getClassLoader }
  }

  def loadSpecificPluginsFrom[T <: PluginInterface](
    svcType: Class[T],
    pluginsDir: Path = pluginsDir
  ): List[T] = {
    synchronized { // mostly to support parallel test runs
      val pluginClassLoader = getClassLoader(pluginsDir)
      val savedClassLoader = Thread.currentThread.getContextClassLoader
      try {
        Thread.currentThread.setContextClassLoader(pluginClassLoader)
        val loader = ServiceLoader.load(svcType, pluginClassLoader)
        val list = loader.iterator().asScala.toList
        val result = for {
          plugin <- list
        } yield {
          require(
            plugin.interfaceVersion <= interfaceVersion,
            s"Plugin ${plugin.getClass.getSimpleName} of interface version ${plugin.interfaceVersion} cannot be supported by Plugin system at " ++
              s"interface version $interfaceVersion."
          )
          val pParts = plugin.riddlVersion.split('.')
          require(
            pParts.length >= 2,
            s"Invalid RIDDL version number; ${plugin.riddlVersion}"
          )
          val pMajor = pParts(0).toInt
          val pMinor = pParts(1).toInt
          require(
            pMajor >= 0 && pMinor >= 0,
            s"Invalid RIDDL version number; ${plugin.riddlVersion}"
          )
          val rParts = RiddlBuildInfo.version.split('.')
          val rMajor = rParts(0).toInt
          val rMinor = rParts(1).toInt
          require(
            pMajor <= rMajor && pMinor <= rMinor,
            s"Plugin compiled with future RIDDL version ${plugin.riddlVersion}"
          )
          plugin
        }
        result
      } finally { Thread.currentThread.setContextClassLoader(savedClassLoader) }
    }
  }
}
