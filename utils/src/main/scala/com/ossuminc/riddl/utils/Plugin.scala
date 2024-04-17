/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.utils

import com.ossuminc.riddl.utils.Plugin.pluginsDir

import java.nio.file.Files
import java.nio.file.Path
import java.util.ServiceLoader
import scala.jdk.StreamConverters.*
import scala.jdk.CollectionConverters.*
import scala.reflect.ClassTag
import scala.reflect.classTag
import scala.collection.mutable
import java.net.URI

object Plugin {

  final private[utils] val interfaceVersion: Int = 1

  final private val pluginDirEnvVarName = "RIDDL_PLUGINS_DIR"
  final val pluginsDir: Path =
    Path.of(
      Option(System.getenv(pluginDirEnvVarName))
        .getOrElse(
          Option(System.getProperty("user.home"))
            .map(d => d + "/.riddl/plugins")
            .getOrElse("riddl-plugins")
        )
    )

  val riddl_components = Seq("utils", "language", "passes", "analyses", "commands", "riddlc", "doc", "testkit")

  // for comparing version strings like:
  // 0.2.0-1-640b3b88-20240325-1108 <-- single digit numerics with commit hash and timestamp
  // 0.28.0-6-bef40bfa-20231211-2048 <-- same but with multi-digit numerics
  // 0.34.2 <-- no hash or timestamp
  // 0.2.0-2-95f3c45a <-- no timestamp
  // 0.32.1-5-0ad2693f
  def versionCompare(s1: String, s2: String): Int = {
    val p1 = s1.split(Array('-', '.'))
    val p2 = s2.split(Array('-', '.'))
    val min = math.min(p1.length, p2.length)
    val ints: Seq[(Int, Int)] = for {
      i <- 0 until min
    } yield {
      if i == 4 then Integer.parseUnsignedInt(p1(i), 16) -> Integer.parseUnsignedInt(p2(i), 16)
      else p1(i).toInt -> p2(i).toInt
    }
    ints.find(x => x._1 != x._2) match
      case Some(values) =>
        values._1 - values._2
      case None =>
        if p1.length > p2.length then 1
        else if p1.length < p2.length then -1
        else 0
  }

  def loadPluginsFromIvyCache[T <: PluginInterface: ClassTag](): Seq[T] = {
    val home = System.getProperty("user.home")
    val ivy2: Path = Path.of(home, ".ivy2", "local", "com.ossuminc")
    given Ordering[Path] = (x: Path, y: Path) => versionCompare(x.getFileName.toString, y.getFileName.toString)
    val candidates = mutable.Set.empty[T]
    Files.list(ivy2).toList.asScala.toSeq.map { case path: Path =>
      val fName = path.getFileName.toString
      if fName.startsWith("riddl-") then
        val suffix = fName.drop(6).dropRight(2)
        if !riddl_components.contains(suffix) then
          val versions = Files.list(path).toList.asScala.toSeq
          val version = versions.max(using Ordering[Path])
          val jars = version.resolve("jars")
          if Files.isDirectory(jars) then
            val found = loadPluginsFrom[T](jars)
            candidates.addAll(found)
    }
    candidates.toSeq
  }

  def getClassLoader(pluginsDir: Path = pluginsDir): ClassLoader = {
    if Files.isDirectory(pluginsDir) then {
      val stream = Files.list(pluginsDir)

      val jars = stream
        .filter(_.getFileName.toString.endsWith(".jar"))
        .toScala(List)
        .sortBy(_.toString)
      stream.close()

      val urls =
        for path <- jars
        yield {
          require(
            Files.isRegularFile(path),
            s"Candidate plugin $path is not a regular file"
          )
          require(
            Files.isReadable(path),
            s"Candidate plugin $path is not a regular file"
          )
          val ssp = "file://" + path.toAbsolutePath.toString + "!/"
          val uri = new URI("jar", ssp, "")
          uri.toURL
          // URI.create(s"jar:file:${path.toAbsolutePath.toString}").toURL
        }
      PluginClassLoader(urls, getClass.getClassLoader)
    } else { getClass.getClassLoader }
  }

  def loadPluginsFrom[T <: PluginInterface: ClassTag](
    pluginsDir: Path = pluginsDir
  ): List[T] = {
    val clazz = classTag[T].runtimeClass.asInstanceOf[Class[T]]
    loadSpecificPluginsFrom[T](clazz, pluginsDir)
  }

  def loadSpecificPluginsFrom[T <: PluginInterface](
    svcType: Class[T],
    pluginsDir: Path = pluginsDir
  ): List[T] = {
    val pluginClassLoader = getClassLoader(pluginsDir)
    val savedClassLoader = Thread.currentThread.getContextClassLoader
    synchronized { // mostly to support parallel test runs
      try {
        Thread.currentThread.setContextClassLoader(pluginClassLoader)
        val loader = ServiceLoader.load(svcType, pluginClassLoader)
        val list = loader.iterator().asScala.toList
        val result =
          for plugin <- list
          yield {
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
