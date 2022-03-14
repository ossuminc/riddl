package com.reactific.riddl.language

import com.reactific.riddl.language.AST.{BlockDescription, Identifier, LiteralString, Term}
import com.reactific.riddl.language.testkit.ParsingTest
class TermTest extends ParsingTest {

  "Term" should {
    "do something" in {
      val input = """domain foo {
                    |  term one is described by "uno"
                    |  context bar is {
                    |    term two is described by "dos"
                    |  }
                    |}""".stripMargin
      parseTopLevelDomain(input, identity) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right(model) =>
          val finder = Finder(model)
          val found = finder.find(_.isInstanceOf[Term])
          found contains Term(
            2 -> 3,
            Identifier(Location(), "one"),
            None,
            Some(BlockDescription(2 -> 15, Seq(LiteralString(2 -> 28, "uno"))))
          )
          found contains Term(
            4 -> 5,
            Identifier(Location(), "two"),
            None,
            Some(BlockDescription(4 -> 17, Seq(LiteralString(4 -> 30, "dos"))))
          )
      }
    }
  }
}
