/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{toSeq, Messages}
import com.ossuminc.riddl.utils.pc
import org.scalatest.TestData

/** How many alternatives an alternation may have — 0, 1 and 2 spelled out, so the boundary is
  * visible rather than inferred.
  *
  * `one of { }` offers no choice at all and is an error; `???` is how a model says "not decided
  * yet". `one of { A }` is a choice of one, which is just `A` wearing a wrapper: it still parses,
  * so no model breaks today, but it is deprecated. Two or more is the real thing.
  */
class AlternationArityTest extends AbstractParsingTest {

  private def parseIt(body: String, td: TestData): Either[Messages.Messages, Root] =
    TopLevelParser.parseInput(
      RiddlParserInput(
        s"""domain D is {
           |  type Foo = String
           |  type Bar = Integer
           |  type Alt is $body
           |}
           |""".stripMargin,
        td
      )
    )

  "an alternation" should {

    "REJECT zero alternatives" in { (td: TestData) =>
      parseIt("one of { }", td) match
        case Right(_) => fail("`one of { }` must not parse: it offers no choice at all")
        case Left(msgs) =>
          msgs.justErrors mustNot be(empty)
    }

    "accept `???` as the explicit placeholder" in { (td: TestData) =>
      parseIt("one of { ??? }", td) match
        case Left(msgs) => fail(s"`one of { ??? }` must parse:\n${msgs.format}")
        case Right(_)   => succeed
    }

    "accept ONE alternative, with a deprecation" in { (td: TestData) =>
      parseIt("one of { type Foo }", td) match
        case Left(msgs) => fail(s"`one of { type Foo }` must still parse:\n${msgs.format}")
        case Right(_)   => succeed
    }

    "accept TWO alternatives cleanly" in { (td: TestData) =>
      parseIt("one of { type Foo or type Bar }", td) match
        case Left(msgs) => fail(s"two alternatives must parse:\n${msgs.format}")
        case Right(root) =>
          val alt = root.domains.head.types
            .find(_.id.value == "Alt")
            .map(_.typEx)
            .collect { case a: Alternation => a }
          alt match
            case Some(a) => a.of.toSeq.size mustBe 2
            case None    => fail("no Alternation was parsed")
    }
  }
}
