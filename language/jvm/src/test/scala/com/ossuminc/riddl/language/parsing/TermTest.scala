/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.{BlockDescription, Identifier, LiteralString, Term, NamedValue}
import com.ossuminc.riddl.language.{At, Finder}
import org.scalatest.TestData

class TermTest extends ParsingTest {

  "Term" should {
    "do something" in { (td:TestData) =>
      val input = RiddlParserInput(
        """domain foo {
          |  term one is described by "uno"
          |  context bar is {
          |    term two is described by "dos"
          |    entity foo is { ??? }
          |  }
          |}""".stripMargin,td)
      parseTopLevelDomain(input, identity) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right((model, _)) =>
          import com.ossuminc.riddl.language.AST.Parent
          val finder = Finder(model)
          val found = finder.find(_.isInstanceOf[Term])
          found contains Term(
            2 -> 3,
            Identifier(At(), "one"),
            None,
            Some(BlockDescription(2 -> 15, Seq(LiteralString(2 -> 28, "uno"))))
          )
          found contains Term(
            4 -> 5,
            Identifier(At(), "two"),
            None,
            Some(BlockDescription(4 -> 17, Seq(LiteralString(4 -> 30, "dos"))))
          )
          val result: Finder#DefWithParents[Parent]  =  finder.findEmpty
          result.size mustBe 1
          result.head match {
            case (entity,_) =>
              entity.asInstanceOf[NamedValue].id.value mustBe "foo"
          }
      }
    }
  }
}
