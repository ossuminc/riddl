/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language

import com.ossuminc.riddl.language.{Contents, *}
import com.ossuminc.riddl.utils.pc
import com.ossuminc.riddl.language.AST.{Root, Parents, RootContents, Type, Handler, Entity, Context}
import com.ossuminc.riddl.language.parsing.{
  AbstractParsingTest,
  RiddlParserInput,
  TestParser,
  TopLevelParser
}
import com.ossuminc.riddl.utils.{
  AbstractTestingBasis,
  AbstractTestingBasisWithTestData,
  PlatformContext
}
import org.scalatest.TestData

class SharedFinderTest extends AbstractTestingBasis {

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
  val input = RiddlParserInput(content, "FinderTest")
  val root: Root =
    TopLevelParser.parseInput(input, true) match
      case Left(messages) =>
        fail(messages.justErrors.format)
      case Right(root: Root) =>
        root
    end match
  val finder: Finder[RootContents] = Finder(root)

  "Finder" must {
    "find a node" in {
      val a = root.modules.head
      val b = a.domains.head
      val c = b.contexts.head
      val d = c.entities.head
      val e = d.handlers.head
      finder.findParents(d) match {
        case s: Parents if s.isEmpty => fail("Path not found")
        case s: Parents =>
          s must be(Parents(c, b, a, root))
      }
    }
    "findInParents finds definitions in ancestor chain" in {
      val content2 =
        """domain D {
          |  type DomainCmd is command { ??? }
          |  context C {
          |    type CtxCmd is command { ??? }
          |    entity E {
          |      type EntCmd is command { ??? }
          |      handler H { ??? }
          |    }
          |  }
          |}
          |""".stripMargin
      val input2 = RiddlParserInput(content2, "findInParentsTest")
      val root2 = TopLevelParser.parseInput(input2, true) match
        case Left(messages) => fail(messages.justErrors.format)
        case Right(r: Root) => r
      val dom = root2.domains.head
      val ctx = dom.contexts.head
      val ent = ctx.entities.head
      val handler = ent.handlers.head
      // Parents of handler: Entity, Context, Domain, Root
      val handlerParents = Parents(ent, ctx, dom, root2)
      val found = Finder.findInParents[Type](handlerParents)
      // Should find EntCmd in Entity, CtxCmd in Context,
      // DomainCmd in Domain — 3 total
      found.size must be(3)
      val names = found.map(_._1.id.value)
      names must contain("EntCmd")
      names must contain("CtxCmd")
      names must contain("DomainCmd")
      // Verify parents: EntCmd's parents should be
      // [Context, Domain, Root] (Entity's parents)
      val entCmdParents = found.find(
        _._1.id.value == "EntCmd"
      ).get._2
      entCmdParents must be(Parents(ctx, dom, root2))
    }
    "build path map correctly" in {
      val a = root.modules.head
      val b = a.domains.head
      val c = b.contexts.head
      val d = c.entities.head
      val e = d.handlers.head
      val pf = finder.findAllPaths
      val a_par = pf.getOrElse(a, fail("no path for a"))
      val b_par = pf.getOrElse(b, fail("no path for b"))
      val c_par = pf.getOrElse(c, fail("no path for c"))
      val d_par = pf.getOrElse(d, fail("no path for d"))
      val e_par = pf.getOrElse(e, fail("no path for e"))
      a_par must be(Parents(root))
      b_par must be(Parents(a, root))
      c_par must be(Parents(b, a, root))
      d_par must be(Parents(c, b, a, root))
      e_par must be(Parents(d, c, b, a, root))
    }
  }
}
