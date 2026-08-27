/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.toSeq
import com.ossuminc.riddl.utils.pc
import org.scalatest.TestData

/** A `???` stub may be introduced by a comment saying what belongs there.
  *
  * That is what a "start from scratch" template looks like, and it used to be unparseable: once the
  * comment was consumed as ordinary content, the `???` branch of the body was unreachable. The
  * ossum.ai Playground's Empty Template had to drop its explanatory comment in order to parse.
  *
  * The comment is KEPT, because a comment that parsed but could not be emitted would vanish on the
  * next prettify.
  */
class CommentedStubTest extends AbstractParsingTest {

  private def parse(src: String, td: TestData): Either[?, Root] =
    TopLevelParser.parseInput(RiddlParserInput(src, td))

  "a `???` body" should {

    "accept a comment BEFORE the `???`" in { (td: TestData) =>
      parse("domain D is {\n  // what goes here\n  ???\n}\n", td) match
        case Left(msgs) => fail(s"a commented stub must parse:\n$msgs")
        case Right(root) =>
          val d = root.domains.head
          withClue("the comment must be KEPT, not discarded: ") {
            d.contents.toSeq.collect { case c: LineComment => c.text }.head must
              include("what goes here")
          }
    }

    "accept several comments before the `???`" in { (td: TestData) =>
      parse("domain D is {\n  // one\n  // two\n  ???\n}\n", td) match
        case Left(msgs)  => fail(s"must parse:\n$msgs")
        case Right(root) => root.domains.head.contents.toSeq.count(_.isComment) mustBe 2
    }

    "accept a bare `???` exactly as before" in { (td: TestData) =>
      parse("domain D is { ??? }\n", td) match
        case Left(msgs)  => fail(s"must parse:\n$msgs")
        case Right(root) => root.domains.head.contents.toSeq mustBe empty
    }

    /** `???` ends the body; a comment after it is not part of the stub. */
    "REJECT a comment after the `???`" in { (td: TestData) =>
      parse("domain D is {\n  ???\n  // too late\n}\n", td) match
        case Left(_)  => succeed
        case Right(_) => fail("a comment AFTER `???` must not parse")
    }
  }
}
