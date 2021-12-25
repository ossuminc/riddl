package com.yoppworks.ossum.riddl.generation.hugo

import org.scalatest.TryValues
import org.scalatest.matchers.must
import org.scalatest.wordspec.AnyWordSpec

import scala.util.Failure
import scala.util.Success

class ResourcesTest extends AnyWordSpec with must.Matchers with TryValues {

  "Resources" should {

    "list template contents correctly" in {
      pending
      Resources.listResourcesInJar("template\\/") match {
        case Success(value)     => value must not be empty
        case Failure(exception) => fail(exception)
      }
    }

    "re-create full template file/folder structure in temporary path" in {
      pending
      val templateContentsTry = Resources.listResourcesInJar("template\\/")
      templateContentsTry.success.value must not be empty
      val templateFilesCount = templateContentsTry.success.value.size

      val tempDirTry = FileUtils.createTemporaryDirectory(deleteOnExit = true)
      tempDirTry.success.value must exist

      val copied = for {
        contents <- templateContentsTry
        tempDir <- tempDirTry
        copied <- Resources.copyResourcesTo(tempDir.getAbsolutePath, contents)
      } yield copied

      copied.success.value mustEqual templateFilesCount
    }

  }

}
