package com.yoppworks.ossum.riddl.language

import com.yoppworks.ossum.riddl.language.AST.Description
import com.yoppworks.ossum.riddl.language.AST.Identifier
import com.yoppworks.ossum.riddl.language.AST.LiteralString
import com.yoppworks.ossum.riddl.language.AST.Term
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
            Some(Description(2 -> 15, Seq(LiteralString(2 -> 28, "uno"))))
          )
          found contains Term(
            4 -> 5,
            Identifier(Location(), "two"),
            None,
            Some(Description(4 -> 17, Seq(LiteralString(4 -> 30, "dos"))))
          )
      }
    }
  }
}
