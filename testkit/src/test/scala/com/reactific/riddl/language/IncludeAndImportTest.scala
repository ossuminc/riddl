package com.reactific.riddl.language

import com.reactific.riddl.language.AST.{Identifier, Strng, Type}
import com.reactific.riddl.language.parsing.RiddlParserInput
import com.reactific.riddl.language.testkit.ParsingTest

/** Unit Tests For Includes */
class IncludeAndImportTest extends ParsingTest {

  "Include" should {
    "handle missing files" in {
      parseDomainDefinition(
        RiddlParserInput("domain foo is { include \"unexisting\" } explained as \"foo\""),
        identity
      ) match {
        case Right(_) => fail("Should have gotten 'does not exist' error")
        case Left(errors) =>
          errors.size must be(1)
          errors.exists(_.format.contains("does not exist,"))
      }
    }
    "handle inclusions into domain" in {
      val (domain,rpi) = checkFile("Domain Includes", "domainIncludes.riddl")
      domain.contents mustNot be(empty)
      domain.contents.head.includes mustNot be(empty)
      domain.contents.head.includes.head.contents mustNot be(empty)
      domain.contents.head.includes.head.contents.head mustBe Type(
        Location(1, 1, rpi),
        Identifier(Location(1, 6, rpi), "foo"),
        Strng(Location(1, 13, rpi)),
        None
      )
    }
    "handle inclusions into contexts" in {
      val (domain,rpi) = checkFile("Context Includes", "contextIncludes.riddl")
      domain.contents mustNot be(empty)
      domain.contents.head.contexts mustNot be(empty)
      domain.contents.head.contexts.head.includes mustNot be(empty)
      domain.contents.head.contexts.head.includes.head.contents mustNot be(empty)
      domain.contents.head.contexts.head.includes.head.contents.head mustBe Type(
        Location(1, 1, rpi),
        Identifier(Location(1, 6, rpi), "foo"),
        Strng(Location(1, 12, rpi)),
        None
      )
    }
  }

  "Import" should {
    "work syntactically" in {
      val (root,_) = checkFile("Import", "import.riddl")
      root.contents must not(be(empty))
      root.contents.head.domains must not(be(empty))
      root.contents.head.domains.head.id.value must be("NotImplemented")
    }
    "handle missing files" in {
      val input = "domain foo is { import domain foo from \"nonexisting\" } described as \"foo\""
      parseDomainDefinition(RiddlParserInput(input), identity) match {
        case Right(_) => fail("Should have gotten 'does not exist' error")
        case Left(errors) =>
          errors.size must be(1)
          errors.exists(_.format.contains("does not exist,"))
      }
    }
  }
}
