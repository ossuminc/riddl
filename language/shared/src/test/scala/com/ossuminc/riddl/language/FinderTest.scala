package com.ossuminc.riddl.language

import com.ossuminc.riddl.language.parsing.{ParsingTest, RiddlParserInput}

class FinderTest extends ParsingTest {

  val input: RiddlParserInput = RiddlParserInput(
    """domain one is {
      |  context one is {
      |    connector a is from outlet foo to inlet bar
      |    flow b is {
      |      inlet b_in is String
      |      outlet b_out is Number
      |    }
      |  } with {
      |    term whomprat is { "a 2m long rat on Tatooine" }
      |  }
      |  // context one is { ??? }
      |  context two is {
      |    function foo is {
      |       requires { a: Integer, b: String }
      |       returns { ??? }
      |       ???
      |     }
      |    type oneState is Integer
      |    entity one is {
      |      state entityState of oneState
      |      handler one  is { ??? }
      |      function one is { ??? }
      |      invariant one is ""
      |    }
      |    entity two is {
      |      state entityState of oneState
      |      handler one  is { ??? }
      |      function one is { ??? }
      |      invariant one is ???
      |    }
      |    adaptor one to context over.consumption is { ??? }
      |  } with {
      |    term ForcePush is { "an ability of the Jedi" }
      |  }
      |  type AString = String
      |}
      |""".stripMargin,
    "empty"
  )
  
  "Finder" should {
    "transform a tree" in {
      parseTopLevelDomains(input) match
        case Left(messages) if messages.hasErrors => fail(messages.justErrors.format)
        case Left(messages) => fail(messages.format)
        case Right(root) =>
          val finder = Finder(root.contents)
          finder.transform(_.isInstanceOf[AST.Type])( rv =>
            val typ = rv.asInstanceOf[AST.Type]
            if typ.id.value == "AString" then
              typ.copy(id = Identifier(typ.id.loc, "Text"))
            else
              typ
          )
      end match
    }
  }


}
