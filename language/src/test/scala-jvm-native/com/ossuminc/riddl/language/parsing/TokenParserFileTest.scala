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
          // Error -- `(thingField = "the thing")` is five tokens. 2026-08-19: +7, because the
          // fixture's `send event Inebriated` went to an outlet declared `type DoAThing` and the
          // send/portlet conformance check made that an Error; it now sends a DoAThing, and a
          // constructor plus its three comment lines is seven tokens more than a bare event ref.
          // 2026-08-27: -1, and the arithmetic reconciles exactly. Both sends were repointed from
          // `outlet APlant.Source.OutCommands` to `inlet APlant.Commands` when reaching past a
          // context onto a portlet of something it contains became an Error
          // (`msg-target-crosses-boundary`). A path tokenizes PER SEGMENT -- Identifier,
          // Punctuation, Identifier, ... -- so three segments (5 tokens) became two (3), i.e. -2
          // per site, -4 in total; the second site's explanatory comment went from three `//`
          // lines to six, and each is its own Comment token, so +3. -4 + 3 = -1.
          // 2026-09-02: +34, from wiring `everything_full.riddl`'s projector-to-repository
          // `tell` with a real channel (A6 reachability became an Error). Attributed, not
          // guessed, because a token count that is merely bumped to whatever the run printed
          // records nothing: 3 Comment tokens on the explanatory lines, 5 for `inlet Incoming
          // is command ACommand`, 5 for `outlet Outgoing is command ACommand`, and 21 for the
          // `connector ProjectToStore` block -- its two paths tokenizing per segment. 3+5+5+21.
          tokens.length must be(453)
          val tasStr = tokens.toString
          tokens.head must be(AST.Token.Keyword(At(rpi, 0, 6)))
          tasStr must include("LiteralCode")
          tasStr must not include ("Other")
      end match
    }
    Await.result(future, 30)
  }
}
