/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language

import com.ossuminc.riddl.language.{Contents, *}
import com.ossuminc.riddl.utils.pc
import com.ossuminc.riddl.language.AST.{
  Root,
  Parents,
  RootContents,
  Type,
  Handler,
  Entity,
  Context,
  NumericLiteral,
  LetStatement
}
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
          |  command DomainCmd is { ??? }
          |  context C {
          |    command CtxCmd is { ??? }
          |    entity E {
          |      command EntCmd is { ??? }
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
      val entCmdParents = found
        .find(
          _._1.id.value == "EntCmd"
        )
        .get
        ._2
      entCmdParents must be(Parents(ctx, dom, root2))
    }
    // Defect filed 2026-08-15 (BACKLOG § 1), fixed same day: `recursiveFindByType` walked
    // `WhenStatement`'s then/else statement lists but never its `condition` FIELD, so a
    // Finder-based search silently missed everything held in a field rather than in `contents` --
    // a ComparisonExpression, a NumericLiteral, a Correlation's `timeoutStatements`, a
    // RequireStatement's `with <expr>` argument, a MatchCase guard. This pins the fix's audit:
    // every field-held site named in the BACKLOG entry, found by a NumericLiteral search at each.
    // Parsing only (no validation) -- Finder operates on the parsed AST, so an unresolved
    // reference is irrelevant to what these cases pin.
    "descend into a WhenStatement's condition" in {
      val content2 =
        """domain D is {
          |  context C is {
          |    function F is {
          |      when 5 > 3 then
          |        let ignored = 1
          |      end
          |    }
          |  }
          |}
          |""".stripMargin
      val root2 = TopLevelParser.parseInput(RiddlParserInput(content2, "whenCondTest"), true) match
        case Left(messages) => fail(messages.justErrors.format)
        case Right(r: Root) => r
      val literals = Finder(root2).recursiveFindByType[NumericLiteral]
      // "5" and "3" from the condition, "1" from the let inside `then` -- all three must be found.
      literals.map(_.text) must contain("5")
      literals.map(_.text) must contain("3")
      literals.map(_.text) must contain("1")
    }

    "descend into a MatchCase guard" in {
      val content2 =
        """domain D is {
          |  context C is {
          |    command Track is { count: Integer } with { briefly "t" }
          |    entity E is {
          |      handler H is {
          |        on command Track {
          |          match count {
          |            case > 3 when count > 7 { error "g" }
          |            default { error "d" }
          |          }
          |        }
          |      } with { briefly "h" }
          |    } with { briefly "e" }
          |  } with { briefly "c" }
          |} with { briefly "d" }
          |""".stripMargin
      val root2 = TopLevelParser.parseInput(RiddlParserInput(content2, "matchGuardTest"), true) match
        case Left(messages) => fail(messages.justErrors.format)
        case Right(r: Root) => r
      val literals = Finder(root2).recursiveFindByType[NumericLiteral]
      // "3" is the pattern's comparand (`case > 3`) and "7" is the guard's operand (`when count >
      // 7`) -- neither field was traversed pre-fix; both are new.
      literals.map(_.text) must contain("3")
      literals.map(_.text) must contain("7")
    }

    "descend into a Correlation's timeoutStatements" in {
      val content2 =
        """domain D is {
          |  context C is {
          |    command Ping is { n: Integer } with { briefly "cmd" }
          |    event Pinged is { n: Integer } with { briefly "evt" }
          |    projector P is {
          |      correlation Corr by n yields command Ping is {
          |        handler H is {
          |          on e: event Pinged is { set field n to e.n }
          |        } with { briefly "h" }
          |      } times out after "1 day" {
          |        let ignored = 42
          |      } with { briefly "corr" }
          |    } with { briefly "proj" }
          |  } with { briefly "c" }
          |} with { briefly "d" }
          |""".stripMargin
      val root2 = TopLevelParser.parseInput(RiddlParserInput(content2, "corrTimeoutTest"), true) match
        case Left(messages) => fail(messages.justErrors.format)
        case Right(r: Root) => r
      val literals = Finder(root2).recursiveFindByType[NumericLiteral]
      literals.map(_.text) must contain("42")
    }

    "descend into a RequireStatement's `with <expr>` argument" in {
      val content2 =
        """domain D is {
          |  context C is {
          |    function F is {
          |      require invariant UnderLimit with 42
          |    }
          |  }
          |}
          |""".stripMargin
      val root2 = TopLevelParser.parseInput(RiddlParserInput(content2, "requireArgTest"), true) match
        case Left(messages) => fail(messages.justErrors.format)
        case Right(r: Root) => r
      val literals = Finder(root2).recursiveFindByType[NumericLiteral]
      literals.map(_.text) must contain("42")
    }

    "still find the STATEMENTS in a SagaStep's do/undo (already reachable pre-fix)" in {
      // Unlike the other cases above, `SagaStep(_, _, dos, undos, _)` was ALREADY one of the old
      // `consider` match's explicit arms, so the two `LetStatement`s themselves were already
      // found before this fix -- pinned here so a future change to `fieldChildren`'s SagaStep arm
      // cannot silently narrow what used to work. (The NumericLiteral NESTED inside each
      // LetStatement's `expression` was NOT reachable pre-fix, but that gap is the same one every
      // other case above pins -- `LetStatement.expression` was never a traversed field for ANY
      // LetStatement anywhere, not something specific to sitting inside a SagaStep -- so it is not
      // re-tested here.)
      val content2 =
        """domain D is {
          |  context C is {
          |    saga S is {
          |      step One is { let a = 1 } reverted by { let b = 2 }
          |    } with { briefly "s" }
          |  } with { briefly "c" }
          |} with { briefly "d" }
          |""".stripMargin
      val root2 = TopLevelParser.parseInput(RiddlParserInput(content2, "sagaStepTest"), true) match
        case Left(messages) => fail(messages.justErrors.format)
        case Right(r: Root) => r
      val lets = Finder(root2).recursiveFindByType[LetStatement]
      lets.map(_.identifier.value) must contain("a")
      lets.map(_.identifier.value) must contain("b")
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
