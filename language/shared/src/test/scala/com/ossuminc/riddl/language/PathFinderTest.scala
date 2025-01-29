/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language

import com.ossuminc.riddl.language.AST.ParentStack
import com.ossuminc.riddl.language.parsing.{
  AbstractParsingTest,
  ParsingTest,
  RiddlParserInput,
  TestParser,
  TopLevelParser
}
import com.ossuminc.riddl.utils.{AbstractTestingBasis, PlatformContext}
import org.scalatest.TestData

class PathFinderTest(using PlatformContext) extends AbstractParsingTest {

  "PathFinder" must {
    "build path map correctly" in { (td: TestData) =>
      val content =
        """module A {
            |  domain B {
            |    context C {
            |      entity D {
            |        handler E { ??? }
            |      }
            |    }
            |  }
            |}
            |""".stripMargin
      val text = s""""$content""""
      val input = RiddlParserInput(text, td)
      TopLevelParser.parseInput(input, true) match
        case Left(messages) => fail(messages.justErrors.format)
        case Right(root) =>
          val a = root.modules.head
          val b = a.domains.head
          val c = b.contexts.head
          val d = c.entities.head
          val e = d.handlers.head
          val pf = findPaths(root)
          val a_map = pf.getOrElse(a, fail("no path for a"))
          val b_map = pf.getOrElse(b, fail("no path for b"))
          val c_map = pf.getOrElse(c, fail("no path for c"))
          val d_map = pf.getOrElse(d, fail("no path for d"))
          val e_map = pf.getOrElse(e, fail("no path for e"))
          a_map must be(ParentStack(a, root))

      end match
    }
  }
}
