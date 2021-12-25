package com.yoppworks.ossum.riddl.generation.hugo

import org.scalatest.TryValues

class GenerationTests extends RiddlTest with TryValues {

  "HugoGenerator" should {

    "create Hugo documentation based on Riddl AST" in {
      pending
      val tempDirTry = FileUtils.createTemporaryDirectory(deleteOnExit = true)
      tempDirTry.success.value must exist
      val tempDir = tempDirTry.get

      val options = GeneratorOptions(tempDir.getAbsolutePath, verbose = true)
      HugoGenerator(container, options)

      val expectedFilesCount = 531
      val outputFiles = FileUtils.listFilesRecursive(tempDir)
      outputFiles must have size expectedFilesCount
    }

  }

}
