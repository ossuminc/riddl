/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.utils

/** Unit Tests For PluginSpecBase */
import org.scalatest.*
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.must.Matchers

import java.io.PrintWriter
import java.nio.file.Files
import java.nio.file.Path
import scala.sys.process.Process

/** Base class for testing plugins */
abstract class PluginSpecBase(
  svcClassPath: Path = Path
    .of("com/reactific/riddl/utils/PluginInterface.class"),
  implClassPath: Path = Path.of("com/reactific/riddl/utils/TestPlugin.class"),
  moduleName: String = "utils",
  jarFilename: String = "test-plugin.jar")
    extends AnyWordSpec with Matchers with BeforeAndAfterAll {

  val tmpDir: Path = Files.createTempDirectory("RiddlTest")
  final val providerConfigurationBasePath = Path.of("META-INF/services/")

  def testClassesDir: Path = Path.of(
    moduleName +
      s"/target/scala-${RiddlBuildInfo.scalaCompatVersion}/test-classes/"
  )

  def makeClassString(p: Path): String = {
    p.toString.dropRight(".class".length).replace('/', '.')
  }
  val svcClassStr: String = makeClassString(svcClassPath)
  val implClassStr: String = makeClassString(implClassPath)

  val providerRelativePath: Path = providerConfigurationBasePath
    .resolve(svcClassStr)

  val providerConfigurationPath: Path = testClassesDir
    .resolve(providerRelativePath).toAbsolutePath

  val jarFile: Path = tmpDir.resolve(jarFilename)

  val implPath: Path = testClassesDir.resolve(implClassPath)

  override def beforeAll(): Unit = {
    Files.createDirectories(providerConfigurationPath.getParent)
    new PrintWriter(providerConfigurationPath.toString) {
      println(implClassStr)
      close()
    }
    val command =
      s"jar cvf ${jarFile.toAbsolutePath} $implClassPath $providerRelativePath"
    val process = Process.apply(command, testClassesDir.toFile).run()
    val rc = process.exitValue()
    if rc != 0 then { fail(s"'$command' failed with RC $rc != 0\n") }
  }
  override def afterAll(): Unit = {
    Files.deleteIfExists(jarFile)
    Files.deleteIfExists(tmpDir)
  }
}
