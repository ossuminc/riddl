package com.yoppworks.ossum.riddl.generation.hugo

class AstWalkerTests extends RiddlTest {

  "AstWalker" should {

    "generate a non-empty HugoRoot from `everything.riddl` test file" in {
      val root = container
      val hugoRoot = LukeAstWalker(root)
      val allHugoNodes = hugoRoot.allContents.toSeq
      allHugoNodes must have size 23
    }

    "properly collect all types from `everything.riddl` test file" in {
      val root = container
      val hugoRoot = LukeAstWalker(root)
      val refTypes = TypeResolver.unresolved(hugoRoot)
      refTypes must have size 7

      val resolvedRoot = TypeResolution(hugoRoot)
      val unresolved = TypeResolver.unresolved(resolvedRoot)

      hugoRoot.allContents must have size resolvedRoot.allContents.size
      // unresolved mustBe empty
    }

  }

}
