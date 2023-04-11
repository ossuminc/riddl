package com.reactific.riddl.language

import com.reactific.riddl.language.parsing.RiddlParserInput
import com.reactific.riddl.language.passes.validate.ValidatingTest

class AuthorTest extends ValidatingTest {

  "Authors" should {
    "be defined empty in domains" in {
      val input = """domain Foo is {
                    |  author Reid is {
                    |    name: "Reid Spencer"
                    |    email: "reid@ossum.biz"
                    |  }
                    |}
                    |""".stripMargin
      parseAndValidateDomain(input) { case (_, _, msgs) =>
        msgs.isOnlyIgnorable must be(true)
      }
    }
    "not supported in contexts" in {
      val input =
        """domain foo is { context bar is { author FooBar is { ??? } } }"""
          .stripMargin
      parseDomainDefinition(input, identity) match {
        case Left(msgs) =>
          msgs.isOnlyIgnorable must be(false)
          msgs.head.message must include("Expected one of")
        case Right(_) => fail("should not have parsed")
      }
    }
    "must be defined" in {
      val input = """domain foo by author Bar is { ??? }"""
      parseAndValidateDomain(input, CommonOptions.noMinorWarnings, shouldFailOnErrors = false) {
        case (_, _, msgs) =>
          val errs = msgs.justErrors
          errs mustNot be(empty)
          assertValidationMessage(errs, Messages.Error, "author Bar is not defined")
          assertValidationMessage(errs, Messages.Error,
            "Path 'Bar' was not resolved, in Domain 'foo', but should refer to an Author")
      }
    }
    "referenced from Application" in {
      val input = RiddlParserInput(
                  """author Reid is {
                    |    name: "Reid Spencer"
                    |    email: "reid@ossum.biz"
                    |  }
                    |domain Foo is {
                    |  application Bar by author Reid is { ??? }
                    |}
                    |""".stripMargin)
      Riddl.parseAndValidate(input) match {
        case Left(errors) => fail(errors.format)
        case Right(result) =>
          result.messages.isOnlyIgnorable must be(true)
      }
    }
    "referenced from Context" in {
      val input = """domain Foo is {
                    |  author Reid is {
                    |    name: "Reid Spencer"
                    |    email: "reid@ossum.biz"
                    |  }
                    |  context Bar by author Reid is { ??? }
                    |}
                    |""".stripMargin
      parseAndValidateDomain(input) { case (_, _, msgs) =>
        msgs.isOnlyIgnorable must be(true)
      }
    }
    "referencable from neighbor Domain" in {
      val input = """domain Foo is {
                    |  author Reid is {
                    |    name: "Reid Spencer"
                    |    email: "reid@ossum.biz"
                    |  }
                    |}
                    |domain Bar by author Reid is { ??? }
                    |""".stripMargin
      parseAndValidateDomain(input) { case (_, _, msgs) =>
        msgs.hasErrors must be(false)
      }
    }
    "referenced from sub-domain" in {
      val input = """domain Foo is {
                    |  author Reid is {
                    |    name: "Reid Spencer"
                    |    email: "reid@ossum.biz"
                    |  }
                    |  domain Bar by author Foo.Reid is { ??? }
                    |}
                    |""".stripMargin
      parseAndValidateDomain(input) { case (_, _, msgs) =>
        msgs.isOnlyIgnorable must be(true)
      }
    }
      "referenced from Entity" in {
      val input = """domain Foo is {
                    |  author Reid is {
                    |    name: "Reid Spencer"
                    |    email: "reid@ossum.biz"
                    |  }
                    |  context Bar by author Reid is {
                    |    entity Bar by author Reid is { ??? }
                    |  }
                    |}
                    |""".stripMargin
      parseAndValidateDomain(input) { case (_, _, msgs) =>
        msgs.isOnlyIgnorable must be(true)
        msgs.isOnlyWarnings must be(true)
      }
    }
    "referenced from Function" in {
      val input = """domain Foo is {
                    |  author Reid is {
                    |    name: "Reid Spencer"
                    |    email: "reid@ossum.biz"
                    |  }
                    |  context Bar {
                    |    function FooBar by author Reid is { ??? }
                    |  }
                    |}
                    |""".stripMargin
      parseAndValidateDomain(input) { case (_, _, msgs) =>
        msgs.isOnlyIgnorable must be(true)
        msgs.isOnlyWarnings must be(true)
      }
    }
    "referenced from Repository" in {
      val input = """domain Foo is {
                    |  author Reid is {
                    |    name: "Reid Spencer"
                    |    email: "reid@ossum.biz"
                    |  }
                    |  context Bar is {
                    |    repository FooBar by author Reid is { ??? }
                    |  }
                    |}
                    |""".stripMargin
      parseAndValidateDomain(input) { case (_, _, msgs) =>
        msgs.isOnlyIgnorable must be(true)
      }
    }
    "referenced from an Epic" in {
      val input = """domain Foo is {
                    |  author Reid is {
                    |    name: "Reid Spencer"
                    |    email: "reid@ossum.biz"
                    |  }
                    |  epic Bar by author Reid is { ??? }
                    |}
                    |""".stripMargin
      parseAndValidateDomain(input) { case (_, _, msgs) =>
        msgs.isOnlyIgnorable must be(true)
      }
    }
  }
}
