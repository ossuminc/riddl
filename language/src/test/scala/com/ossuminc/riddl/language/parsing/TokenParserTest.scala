/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.Token.{Comment, Readability}
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.language.{AST, At}
import com.ossuminc.riddl.language.AST.{Token, *}
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.utils.{PlatformContext, Timer}
import org.scalatest.TestData

/** Tokenizer cases that run on every platform. The two cases that read a `.riddl` file from the
  * working directory live in the JVM-only `TokenParserFileTest`, because Scala.js cannot load
  * files.
  */
abstract class TokenParserTest(using pc: PlatformContext) extends AbstractParsingTest {
  "TokenParser" must {
    "handle simple document fragment" in { (td: TestData) =>
      val rpi: RiddlParserInput = RiddlParserInput(
        """module foo is {
          |   // this is a comment
          |   domain blah is { ??? }
          |   invariant bar is "condition"
          |   type enum is any of { Apple, Pear,  Peach(23),  Persimmon(24) }
          |}
          |""".stripMargin,
        td
      )
      val result = Timer.time("Token Collection: simple document") {
        TopLevelParser.parseToTokens(rpi)
      }
      result match
        case Left(messages) =>
          fail(messages.format)
        case Right(tokens) =>
          val expected = Seq(
            AST.Token.Keyword(At(rpi, 0, 6)),
            AST.Token.Identifier(At(rpi, 7, 10)),
            AST.Token.Readability(At(rpi, 11, 13)),
            AST.Token.Punctuation(At(rpi, 14, 15)),
            AST.Token.Comment(At(rpi, 19, 39)),
            AST.Token.Keyword(At(rpi, 43, 49)),
            AST.Token.Identifier(At(rpi, 50, 54)),
            AST.Token.Readability(At(rpi, 55, 57)),
            AST.Token.Punctuation(At(rpi, 58, 59)),
            AST.Token.Punctuation(At(rpi, 60, 63)),
            AST.Token.Punctuation(At(rpi, 64, 65)),
            AST.Token.Keyword(At(rpi, 69, 78)),
            AST.Token.Identifier(At(rpi, 79, 82)),
            AST.Token.Readability(At(rpi, 83, 85)),
            AST.Token.QuotedString(At(rpi, 86, 97)),
            AST.Token.Keyword(At(rpi, 101, 105)),
            AST.Token.Identifier(At(rpi, 106, 110)),
            AST.Token.Readability(At(rpi, 111, 113)),
            AST.Token.Keyword(At(rpi, 114, 117)),
            AST.Token.Readability(At(rpi, 118, 120)),
            AST.Token.Punctuation(At(rpi, 121, 122)),
            AST.Token.Identifier(At(rpi, 123, 128)),
            AST.Token.Punctuation(At(rpi, 128, 129)),
            AST.Token.Identifier(At(rpi, 130, 134)),
            AST.Token.Punctuation(At(rpi, 134, 135)),
            AST.Token.Identifier(At(rpi, 137, 142)),
            AST.Token.Punctuation(At(rpi, 142, 143)),
            AST.Token.Numeric(At(rpi, 143, 145)),
            AST.Token.Punctuation(At(rpi, 145, 146)),
            AST.Token.Punctuation(At(rpi, 146, 147)),
            AST.Token.Identifier(At(rpi, 149, 158)),
            AST.Token.Punctuation(At(rpi, 158, 159)),
            AST.Token.Numeric(At(rpi, 159, 161)),
            AST.Token.Punctuation(At(rpi, 161, 162)),
            AST.Token.Punctuation(At(rpi, 163, 164)),
            AST.Token.Punctuation(At(rpi, 165, 166))
          )
          tokens must be(expected)
    }
  }

  /** `!` is a first-class negation spelling since the 2026-08-14 ruling that made it synonymous
    * with `not` everywhere. The tokenizer never learned that: `Punctuation.tokenPunctuation`
    * omitted it, so `TokenParser.otherToken` swallowed `!isValid` as ONE `Token.Other` blob and
    * editor tooling (riddl-idea-plugin, synapify) could not highlight either part.
    */
  "tokenize `!` as punctuation, not as part of an Other blob" in { (td: TestData) =>
    val rpi = RiddlParserInput("""when !isValid then do "no" end""", td)
    TopLevelParser.mapTextAndToken[String](rpi) { (slice, token) =>
      token.getClass.getSimpleName.replace("$", "") + "(" + slice.mkString + ")"
    } match
      case Left(messages) => fail(messages.format)
      case Right(list) =>
        withClue(list.mkString(", ")) {
          list must contain("Punctuation(!)")
          list must contain("Identifier(isValid)")
          list.exists(_.startsWith("Other(")) mustBe false
        }
  }

  /** The guard that keeps the fix honest: `!=` is a comparison OPERATOR, not a negation, and
    * splitting it into two punctuation tokens would misreport it to an editor as `!` followed by
    * `=`. Pinned separately because the parser's own `"!" ~~ !"="` lookahead has no counterpart in
    * the tokenizer, so nothing else would notice.
    */
  "tokenize `!=` without splitting it into two punctuation tokens" in { (td: TestData) =>
    val rpi = RiddlParserInput("""when count != total then do "no" end""", td)
    TopLevelParser.mapTextAndToken[String](rpi) { (slice, token) =>
      token.getClass.getSimpleName.replace("$", "") + "(" + slice.mkString + ")"
    } match
      case Left(messages) => fail(messages.format)
      case Right(list) =>
        withClue(list.mkString(", ")) { list must not contain "Punctuation(!)" }
  }

  "handle mapping text with tokens" in { (td: TestData) =>
    val data =
      """
        |context full is {
        |  type str is String             // Define str as a String
        |  type num is Number             // Define num as a Number
        |  type boo is Boolean            // Define boo as a Boolean
        |  type ident is Id(Something)    // Define ident as an Id
        |  type dat is Date               // Define dat as a Date
        |  type tim is Time               // Define tim as a Time
        |  type stamp is TimeStamp        // Define stamp as a TimeStamp
        |  type url is URL                // Define url as a Uniform Resource Locator
        |
        |  type PeachType is { a: Integer with { ??? } }
        |  type enum is any of { Apple Pear Peach(23)   Persimmon(24) }
        |
        |  type alt is one of { enum or stamp or url } with {
        |    described as {
        |      | Alternations select one type from a list of types
        |    }
        |  }
        |
        |
        |  type agg is {
        |    key: num,
        |    id: ident,
        |    time is TimeStamp
        |  }
        |
        |  type moreThanNone is many agg
        |  type zeroOrMore is agg*
        |  type optionality is agg?
        |
        |  repository StoreIt is {
        |    schema One is relational
        |      of a as type agg
        |      link relationship as field agg.time to field agg.ident
        |      index on field agg.id
        |    with { briefly as "This is how to store data" }
        |
        |    handler Putter is {
        |      on command ACommand {
        |        put "something" to type agg
        |      }
        |    }
        |  } with {
        |    briefly as "This is a simple repository"
        |    term foo is "an arbitrary name as a contraction for fubar which has grotesque connotations"
        |  }
        |
        |
        |  projector ProjectIt is {
        |    updates repository StoreIt
        |    record Record is { ??? }
        |    handler projector is {
        |      on init {
        |        tell command ACommand to repository StoreIt
        |      }
        |    }
        |  }
        |
        |  command ACommand()
        |
        |  adaptor fromAPlant to context APlant is {
        |    handler adaptCommands is {
        |      on command ACommand {
        |        send command DoAThing to outlet APlant.Source.Commands
        |      }
        |    }
        |  }
        |
        |  entity Something is {
        |    function misc is {
        |      requires { n: Nothing }
        |      returns { b: Boolean }
        |      ???
        |    } with {
        |      option aggregate
        |      option transient
        |    }
        |    type somethingDate is Date
        |
        |    event Inebriated is { ??? }
        |
        |    record someData(field:  SomeType)
        |    state someState of record Something.someData
        |
        |    handler foo is {
        |      // Handle the ACommand
        |      on command ACommand {
        |        if "Something arrives" then {
        |          // we want to send an event
        |          send event Inebriated to outlet APlant.Source.Commands
        |        }
        |      }
        |    }
        |
        |    function whenUnderTheInfluence is {
        |      requires { n: Nothing }
        |      returns  { b: Boolean }
        |      "aribtrary statement"
        |      ```scala
        |        // Simulate a creative state
        |        val randomFactor = Math.random() // A random value between 0 and 1
        |        val threshold = 0.7 // Threshold for creativity
        |
        |        // If the random factor exceeds the threshold, consider it a creative state
        |        b = randomFactor > threshold
        |      ```
        |    } with {
        |      briefly as "Something is nothing interesting"
        |    }
        |  }
        |
        |  entity SomeOtherThing is {
        |    event ItHappened is { aField: String }
        |    record otherThingData is { aField: String }
        |    state otherThingState of record SomeOtherThing.otherThingData
        |    handler fee is {
        |      on event ItHappened {
        |        set field SomeOtherThing.otherThingState.aField to "arbitrary string value"
        |      }
        |    }
        |  }
        |}
        |
        |""".stripMargin
    import scala.collection.IndexedSeqView
    def mapTokens(slice: IndexedSeqView[Char], token: AST.Token): String =
      token.getClass.getSimpleName + "(" + slice.mkString + ")"
    end mapTokens
    val rpi = RiddlParserInput(data, td)
    val result: Either[Messages, List[String]] =
      pc.withOptions(pc.options.copy(showTimes = true)) { _ =>
        Timer.time("parseToTokensAndText") {
          TopLevelParser.mapTextAndToken[String](rpi)((x, y) => mapTokens(x, y))
        }
      }
    result match
      case Left(messages) =>
        fail(messages.format)
      case Right(list) =>
        // A9b: +2 tokens (record keyword on migrated states). 2026-08-06: -1, because the
        // fixture's one type-first aggregate moved to kind-first -- `type X is command {` is five
        // tokens, `command X is {` is four.
        list.length must be(405)
        list.head mustBe ("Keyword(context)")
        list(1) mustBe ("Identifier(full)")
    end match
  }
}
