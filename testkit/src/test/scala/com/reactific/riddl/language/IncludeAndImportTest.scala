package com.reactific.riddl.language

import com.reactific.riddl.language.AST.Identifier
import com.reactific.riddl.language.AST.Strng
import com.reactific.riddl.language.AST.Type
import com.reactific.riddl.language.parsing.RiddlParserInput
import com.reactific.riddl.testkit.ParsingTest

/** Unit Tests For Includes */
class IncludeAndImportTest extends ParsingTest {

  "Include" should {
    "handle missing files" in {
      parseDomainDefinition(
        RiddlParserInput(
          "domain foo is { include \"unexisting\" } explained as \"foo\""
        ),
        identity
      ) match {
        case Right(_) => fail("Should have gotten 'does not exist' error")
        case Left(errors) =>
          errors.size must be(1)
          errors.exists(_.format.contains("does not exist,"))
      }
    }
    "handle inclusions into domain" in {
      val rc = checkFile("Domain Includes", "domainIncludes.riddl")
      rc.contents mustNot be(empty)
      rc.contents.head.includes mustNot be(empty)
      rc.contents.head.includes.head.contents mustNot be(empty)
      rc.contents.head.includes.head.contents.head mustBe Type(
        (1, 1, rc.inputs.head),
        Identifier((1, 6, rc.inputs.head), "foo"),
        Strng((1, 13, rc.inputs.head)),
        None
      )
    }
    "handle inclusions into contexts" in {
      val rc = checkFile("Context Includes", "contextIncludes.riddl")
      rc.contents mustNot be(empty)
      rc.contents.head.contexts mustNot be(empty)
      rc.contents.head.contexts.head.includes mustNot be(empty)
      rc.contents.head.contexts.head.includes.head.contents mustNot be(empty)
      rc.contents.head.contexts.head.includes.head.contents.head mustBe Type(
        (1, 1, rc.inputs.head),
        Identifier((1, 6, rc.inputs.head), "foo"),
        Strng((1, 12, rc.inputs.head)),
        None
      )
    }
  }

  "Import" should {
    "work syntactically" in {
      val root = checkFile("Import", "import.riddl")
      root.contents must not(be(empty))
      root.contents.head.domains must not(be(empty))
      root.contents.head.domains.head.id.value must be("NotImplemented")
    }
    "handle missing files" in {
      val input =
        "domain foo is { import domain foo from \"nonexisting\" } described as \"foo\""
      parseDomainDefinition(RiddlParserInput(input), identity) match {
        case Right(_) => fail("Should have gotten 'does not exist' error")
        case Left(errors) =>
          errors.size must be(1)
          errors.exists(_.format.contains("does not exist,"))
      }
    }
  }
}
