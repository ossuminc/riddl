package com.yoppworks.ossum.riddl.parser

import java.io.File

import AST.AST

/** Unit Tests For TraversalTest */
class TraversalTest extends ParsingTest {

  "Traversal" should {
    "can reflect its input" in {
      val everything = "parser/src/test/input/everything.riddl"
      val file = new File(everything)
      val result =
        TopLevelParser.parseFile(file, TopLevelParser.topLevelDomains(_))
      result match {
        case Left(message) ⇒
          fail(message)
        case Right(domains) ⇒
          val visitor = new RIDDLVisitor
          domains.foreach(Traversal.traverse(_, visitor))
      }
    }
  }
}
