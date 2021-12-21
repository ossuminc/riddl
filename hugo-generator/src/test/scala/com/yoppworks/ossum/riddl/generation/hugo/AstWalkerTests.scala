package com.yoppworks.ossum.riddl.generation.hugo

import com.yoppworks.ossum.riddl.language.Riddl.SysLogger
import com.yoppworks.ossum.riddl.language.AST
import com.yoppworks.ossum.riddl.language.RiddlParserInput
import com.yoppworks.ossum.riddl.language.TopLevelParser
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.must
import org.scalatest.wordspec.AnyWordSpec

import java.io.File

class AstWalkerTests extends AnyWordSpec with must.Matchers with BeforeAndAfterAll {

  val testFilePath = s"language/src/test/input/everything.riddl"
  val input = RiddlParserInput(new File(testFilePath))
  private[this] var _container: Option[AST.RootContainer] = None
  private def container: AST.RootContainer = _container
    .getOrElse(throw new RuntimeException(s"Could not parse input file: $testFilePath"))

  override def beforeAll(): Unit = {
    _container = TopLevelParser.parse(input) match {
      case Left(errors) =>
        errors.map(_.format).foreach(SysLogger.error(_))
        None
      case Right(root) => Some(root)
    }
    container
  }

  "AstWalker" should {

    "generate a non-empty HugoRoot from `everything.riddl` test file" in {
      val root = container
      val hugoRoot = LukeAstWalker(root)
      val allHugoNodes = hugoRoot.allContents.toSeq
      allHugoNodes must have size 21
    }

    "properly collect all types from `everything.riddl` test file" in {
      val root = container
      val hugoRoot = LukeAstWalker(root)
      val refTypes = TypeResolver.unresolved(hugoRoot)
      refTypes must have size 7

      val resolvedRoot = TypeResolution(hugoRoot)
      val unresolved = TypeResolver.unresolved(resolvedRoot)

      hugoRoot.allContents must have size resolvedRoot.allContents.size
      unresolved mustBe empty
    }
  }
}
