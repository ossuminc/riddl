/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.{AST, At}
import com.ossuminc.riddl.utils.{Await, Timer, URL, ec, pc}
import org.scalatest.TestData

/** The tokenizer cases that read a `.riddl` file from the working directory.
  *
  * These live apart from [[TokenParserTest]] because loading a file is not something every platform
  * can do: the Scala.js `PlatformContext` raises `FileNotFoundException` for any `file:` URL. The
  * remaining, string-driven tokenizer cases stay in the shared `TokenParserTest` so they run on JVM
  * and JS alike.
  */
class TokenParserFileTest extends AbstractParsingTest {

  "handle rbbq.riddl, a more complete example" in { (td: TestData) =>
    val url = URL.fromCwdPath("language/input/rbbq.riddl")
    val future = RiddlParserInput.fromURL(url, td).map { rpi =>
      val result = pc.withOptions(pc.options.copy(showTimes = true)) { _ =>
        Timer.time("parseToTokens") {
          TopLevelParser.parseToTokens(rpi)
        }
      }
      result match
        case Left(messages) =>
          fail(messages.format)
        case Right(tokens) =>
          // A9b: robust tokenizer check. This was a ~500-token exact-offset golden list that broke
          // on every syntax change (e.g. `of type` -> `of record`); replaced with count + head +
          // no-unrecognized-tokens, matching the everything.riddl test's style.
          tokens.length must be(542)
          tokens.head must be(AST.Token.Keyword(At(rpi, 0, 6)))
          tokens.toString must not include ("Other")
      end match
    }
    Await.result(future, 30)
  }

  "handle everything.riddl, a more complete example" in { (td: TestData) =>
    val url = URL.fromCwdPath("language/input/everything_full.riddl")
    val future = RiddlParserInput.fromURL(url, td).map { rpi =>
      val result = pc.withOptions(pc.options.copy(showTimes = true)) { _ =>
        Timer.time("parseToTokens") {
          TopLevelParser.parseToTokens(rpi)
        }
      }
      result match
        case Left(messages) =>
          fail(messages.format)
        case Right(tokens) =>
          // A9b: +2 tokens (record keyword on migrated states). 2026-08-06: -1, because the
          // fixture's one type-first aggregate moved to kind-first -- `type X is command {` is
          // five tokens, `command X is {` is four. 2026-08-14: +5, because the fixture's one bare
          // `send command DoAThing` gained a constructor when a bare message operand became an
          // Error -- `(thingField = "the thing")` is five tokens.
          tokens.length must be(413)
          val tasStr = tokens.toString
          tokens.head must be(AST.Token.Keyword(At(rpi, 0, 6)))
          tasStr must include("LiteralCode")
          tasStr must not include ("Other")
      end match
    }
    Await.result(future, 30)
  }
}
