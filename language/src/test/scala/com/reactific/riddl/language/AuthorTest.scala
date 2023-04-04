package com.reactific.riddl.language

import com.reactific.riddl.language.passes.validation.ValidatingTest

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
      parseAndValidateDomain(input) { case (_, _, msgs) =>
        msgs.isOnlyIgnorable must be(false)
        val errs = msgs.filter(_.kind == Messages.Error)
        errs mustNot be(empty)
        errs.head.message must include("author Bar is not defined")
      }
    }
    "referenced from Application" in {
      val input = """domain Foo is {
                    |  author Reid is {
                    |    name: "Reid Spencer"
                    |    email: "reid@ossum.biz"
                    |  }
                    |  application Bar by author Reid is { ??? }
                    |}
                    |""".stripMargin
      parseAndValidateDomain(input) { case (_, _, msgs) =>
        msgs.isOnlyIgnorable must be(true)
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
    "referenced from Domain" in {
      val input = """domain Foo is {
                    |  author Reid is {
                    |    name: "Reid Spencer"
                    |    email: "reid@ossum.biz"
                    |  }
                    |  domain Bar by author Reid is { ??? }
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
        msgs.isOnlyIgnorable must be(false)
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
        msgs.isOnlyIgnorable must be(false)
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
