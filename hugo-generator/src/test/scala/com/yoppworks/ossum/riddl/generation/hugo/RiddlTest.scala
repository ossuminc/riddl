package com.yoppworks.ossum.riddl.generation.hugo

import com.yoppworks.ossum.riddl.language.Riddl.SysLogger
import com.yoppworks.ossum.riddl.language.AST
import com.yoppworks.ossum.riddl.language.parsing.RiddlParserInput
import com.yoppworks.ossum.riddl.language.parsing.TopLevelParser
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.must
import org.scalatest.wordspec.AnyWordSpec

import java.io.File

trait RiddlTest extends AnyWordSpec with must.Matchers with BeforeAndAfterAll {
  private[this] var _container: Option[AST.RootContainer] = None
  protected final def container: AST.RootContainer = _container
    .getOrElse(throw new RuntimeException(s"Could not parse input file: ${RiddlTest.testFilePath}"))

  override def beforeAll(): Unit = { _container = RiddlTest.getContainer }
}

object RiddlTest {

  val testFilePath = s"language/src/test/input/everything.riddl"
  val input = RiddlParserInput(new File(testFilePath))
  private[this] lazy val _container = TopLevelParser.parse(input) match {
    case Left(errors) =>
      errors.map(_.format).foreach(SysLogger.error(_))
      None
    case Right(root) => Some(root)
  }
  private def getContainer = _container
}
