package com.ossuminc.riddl.passes

import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.language.AST
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.File
import java.nio.file.Path

class RiddlTest extends AnyWordSpec with Matchers {

  val testPath = "language/jvm/src/test/input/everything.riddl"

  "Riddl" must {

    "parse a file" in {
      val input = RiddlParserInput.rpiFromPath(Path.of(testPath))
      Riddl.parse(input) match {
        case Left(messages) => fail(messages.format)
        case Right(root) =>
          val domains = AST.getTopLevelDomains(root)
          domains.size mustBe 1
          val domain = domains.head
          val contexts = AST.getContexts(domain)
          contexts.size mustBe 2
          AST.getDomains(domain) mustBe empty
      }
    }

    "validate a file" in {
      val input = RiddlParserInput.rpiFromFile(new File(testPath))
      Riddl.parse(input) match
        case Left(messages) => fail(messages.format)
        case Right(root) =>
          Riddl.validate(root) match
            case Left(messages) => fail(messages.justErrors.format)
            case Right(result)  => succeed

    }

    "parse and validate a RiddlParserInput" in {
      val input = RiddlParserInput.rpiFromFile(new File(testPath))
      Riddl.parseAndValidate(input) match
        case Left(messages) => fail(messages.justErrors.format)
        case Right(result)  => succeed
    }

    "parse and validate a Path" in {
      val path = Path.of(testPath)
      Riddl.parseAndValidatePath(path) match
        case Left(messages) => fail(messages.justErrors.format)
        case Right(result)  => succeed
    }
  }
}
