package com.reactific.riddl.utils

/** Unit Tests For PluginSpec */
import org.scalatest.*
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.must.Matchers

import java.nio.file.Files

/** Tests For ${Name} */
class PluginSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {

  val tmpDir = Files.createTempDirectory("RiddlTest")
  val jarFile = tmpDir.resolve("test-plugin.jar")
  val classFile =
    "utils/target/scala-2.13/test-classes/com/reactific/riddl/utils/TestPlugin.class"

  override def beforeAll(): Unit =  {
    val command = s"jar cvf ${jarFile.toAbsolutePath} ${classFile}"
    val process = Runtime.getRuntime.exec(command)
    val exit = process.waitFor()
    require(exit == 0,s"jar command failed with $exit" )
  }
  override def afterAll(): Unit =  {
    Files.deleteIfExists(jarFile)
    Files.deleteIfExists(tmpDir)
  }

  "PluginSpec" should {
    "load a plugin" in {
      Plugin.loadPluginsFrom(tmpDir) mustNot be(empty)
    }
  }
}
