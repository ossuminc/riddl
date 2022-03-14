package com.reactific.riddl.language

import com.reactific.riddl.language.AST.{Domain, Identifier, RootContainer}
import com.reactific.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.reactific.riddl.language.testkit.ParsingTestBase

import java.io.File
import scala.io.Source

class TopLevelParserTest extends ParsingTestBase {

  val origin = "simpleDomain.riddl"

  val simpleDomainFile = new File(s"testkit/src/test/input/domains/$origin")

  val simpleDomain = RootContainer(List(Domain(
    (1, 1, origin),
    Identifier((1, 8, origin), "foo"),
    None,
    List(),
    List(),
    List(),
    List(),
    List(),
    List(),
    List(),
    None
  )))

  "parse" should {
    "parse RiddlParserInput" in {
      TopLevelParser.parse(RiddlParserInput(simpleDomainFile)) mustBe Right(simpleDomain)
    }
    "parse File" in { TopLevelParser.parse(simpleDomainFile) mustBe Right(simpleDomain) }
    "parse Path" in { TopLevelParser.parse(simpleDomainFile.toPath) mustBe Right(simpleDomain) }
    "parse String" in {
      val source = Source.fromFile(simpleDomainFile)
      try {
        val stringContents = source.mkString
        TopLevelParser.parse(stringContents, origin) mustBe Right(simpleDomain)
      } finally { source.close() }
    }
    "parse empty String" in { TopLevelParser.parse("") mustBe Right(RootContainer.empty) }
  }

}
