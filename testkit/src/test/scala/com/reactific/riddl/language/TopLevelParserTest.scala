package com.reactific.riddl.language

import com.reactific.riddl.language.AST.{Domain, DomainOption, Identifier, RootContainer}
import com.reactific.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.reactific.riddl.language.testkit.ParsingTestBase

import java.io.File
import scala.io.Source

class TopLevelParserTest extends ParsingTestBase {

  val origin = "simpleDomain.riddl"

  val simpleDomainFile = new File(s"testkit/src/test/input/domains/$origin")
  val rip = RiddlParserInput(simpleDomainFile)

  val simpleDomain = RootContainer(List(Domain(
    Location(1, 1, rip),
    Identifier(Location(1, 8, rip), "foo"),
    Seq.empty[DomainOption],
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
