/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.{BlockDescription, Identifier, LiteralString, Term, WithIdentifier}
import com.ossuminc.riddl.language.{At, Finder}
import org.scalatest.TestData

class TermTest extends ParsingTest {

  "Term" should {
    "do something" in { (td:TestData) =>
      val input = RiddlParserInput(
        """domain foo {
          |  term one is { "uno" }
          |  context bar is {
          |    term two is { "dos" }
          |    entity foo is { ??? }
          |  }
          |}""".stripMargin,td)
      parseTopLevelDomain(input, identity) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right((model, _)) =>
          import com.ossuminc.riddl.language.AST.{Parent, RiddlValue}
          val finder = Finder(model)
          val found = finder.find(_.isInstanceOf[Term])
          found contains Term(
            2 -> 3,
            Identifier(At(), "one"),
            Seq(LiteralString(2 -> 28, "uno"))
          )
          found contains Term(
            4 -> 5,
            Identifier(At(), "two"),
            Seq(LiteralString(4 -> 30, "dos"))
          )
          val result: Finder[RiddlValue]#DefWithParents[WithIdentifier]  =  finder.findEmpty
          result.size mustBe 3
          result.head match {
            case (entity: WithIdentifier,_) =>
              entity.id.value mustBe "one"
          }
      }
    }
  }
}
