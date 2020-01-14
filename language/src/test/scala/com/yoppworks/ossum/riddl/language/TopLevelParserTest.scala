package com.yoppworks.ossum.riddl.language

import java.io.File

import com.yoppworks.ossum.riddl.language.AST.Domain
import com.yoppworks.ossum.riddl.language.AST.Identifier
import com.yoppworks.ossum.riddl.language.AST.RootContainer

import scala.io.Source

class TopLevelParserTest extends ParsingTestBase {

  val simpleDomainFile = new File(
    "language/src/test/input/domains/simpleDomain.riddl"
  )

  val simpleDomain = RootContainer(
    List(
      Domain(
        (1, 1),
        Identifier((1, 8), "foo"),
        List(),
        List(),
        List(),
        List(),
        List(),
        None
      )
    )
  )

  "parse" should {
    "parse RiddlParserInput" in {
      TopLevelParser.parse(RiddlParserInput(simpleDomainFile)) mustBe Right(
        simpleDomain
      )
    }
    "parse File" in {
      TopLevelParser.parse(simpleDomainFile) mustBe Right(simpleDomain)
    }
    "parse Path" in {
      TopLevelParser.parse(simpleDomainFile.toPath) mustBe Right(simpleDomain)
    }
    "parse String" in {
      val source = Source.fromFile(simpleDomainFile)
      try {
        val stringContents = source.mkString
        TopLevelParser.parse(stringContents) mustBe Right(simpleDomain)
      } finally {
        source.close()
      }
    }
    "parse empty String" in {
      TopLevelParser.parse("") mustBe Right(RootContainer.empty)
    }
  }

}
