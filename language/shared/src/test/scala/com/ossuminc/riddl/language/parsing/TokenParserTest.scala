/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.Token.Comment
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.language.{AST,At}
import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.utils.{Await, PlatformContext, Timer, URL}
import org.scalatest.TestData

import scala.concurrent.ExecutionContext
import scala.io.AnsiColor.{GREEN, RED, RESET}

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

  "handle rbbq.riddl, a more complete example" in { (td: TestData) =>
    implicit val ec: ExecutionContext = pc.ec
    val url = URL.fromCwdPath("language/input/rbbq.riddl")
    val future = RiddlParserInput.fromURL(url, td).map { rpi =>
      val expectedTokens: List[AST.Token] = List(
        AST.Token.Keyword(At(rpi,0,6)),              // domain
        AST.Token.Identifier(At(rpi,7,18)),          // ReactiveBBQ
        AST.Token.Readability(At(rpi,19,21)),        // is
        AST.Token.Punctuation(At(rpi,22,23)),        // {
        AST.Token.Keyword(At(rpi,27,31)),            // type
        AST.Token.Identifier(At(rpi,32,42)),         // CustomerId
        AST.Token.Readability(At(rpi,43,45)),        // is
        AST.Token.Predefined(At(rpi,46,48)),         // Id
        AST.Token.Punctuation(At(rpi,48,49)),        // (
        AST.Token.Identifier(At(rpi,49,60)),         // ReactiveBBQ
        AST.Token.Punctuation(At(rpi,60,61)),        // .
        AST.Token.Identifier(At(rpi,61,69)),         // Customer
        AST.Token.Punctuation(At(rpi,69,70)),        // .
        AST.Token.Identifier(At(rpi,70,78)),         // Customer
        AST.Token.Punctuation(At(rpi,78,79)),        // )
        AST.Token.Keyword(At(rpi,80,84)),            // with
        AST.Token.Punctuation(At(rpi,85,86)),        // {
        AST.Token.Keyword(At(rpi,91,100)),           // described
        AST.Token.Readability(At(rpi,101,103)),      // as
        AST.Token.Punctuation(At(rpi,104,105)),      // {
        AST.Token.QuotedString(At(rpi,112,146)),     // "Unique identifier for a customer"
        AST.Token.Punctuation(At(rpi,151,152)),      // }
        AST.Token.Punctuation(At(rpi,155,156)),      // }
        AST.Token.Keyword(At(rpi,160,164)),          // type
        AST.Token.Identifier(At(rpi,165,172)),       // OrderId
        AST.Token.Readability(At(rpi,173,175)),      // is
        AST.Token.Predefined(At(rpi,176,178)),       // Id
        AST.Token.Punctuation(At(rpi,178,179)),      // (
        AST.Token.Identifier(At(rpi,179,190)),       // ReactiveBBQ
        AST.Token.Punctuation(At(rpi,190,191)),      // .
        AST.Token.Identifier(At(rpi,191,196)),       // Order
        AST.Token.Punctuation(At(rpi,196,197)),      // .
        AST.Token.Identifier(At(rpi,197,202)),       // Order
        AST.Token.Punctuation(At(rpi,202,203)),      // )
        AST.Token.Keyword(At(rpi,204,208)),          // with
        AST.Token.Punctuation(At(rpi,209,210)),      // {
        AST.Token.Keyword(At(rpi,215,224)),          // described
        AST.Token.Readability(At(rpi,225,227)),      // as
        AST.Token.Punctuation(At(rpi,228,229)),      // {
        AST.Token.MarkdownLine(At(rpi,236,244)),     // |# brief
        AST.Token.MarkdownLine(At(rpi,251,292)),     // |Unique identifier for a customer's order
        AST.Token.MarkdownLine(At(rpi,299,305)),     // |# see
        AST.Token.MarkdownLine(At(rpi,312,372)),     // |* [OrderId](http://www.example.com/show/details/on/OrderId)
        AST.Token.Punctuation(At(rpi,377,378)),      // }
        AST.Token.Punctuation(At(rpi,381,382)),      // }
        AST.Token.Keyword(At(rpi,386,390)),          // type
        AST.Token.Identifier(At(rpi,391,396)),       // Empty
        AST.Token.Punctuation(At(rpi,397,398)),      // =
        AST.Token.Predefined(At(rpi,399,406)),       // Nothing
        AST.Token.Keyword(At(rpi,410,417)),          // context
        AST.Token.Identifier(At(rpi,418,426)),       // Customer
        AST.Token.Readability(At(rpi,427,429)),      // is
        AST.Token.Punctuation(At(rpi,430,431)),      // {
        AST.Token.Keyword(At(rpi,436,442)),          // entity
        AST.Token.Identifier(At(rpi,443,451)),       // Customer
        AST.Token.Readability(At(rpi,452,454)),      // is
        AST.Token.Punctuation(At(rpi,455,456)),      // {
        AST.Token.Keyword(At(rpi,463,468)),          // state
        AST.Token.Identifier(At(rpi,469,473)),       // main
        AST.Token.Readability(At(rpi,474,476)),      // of
        AST.Token.Identifier(At(rpi,477,488)),       // ReactiveBBQ
        AST.Token.Punctuation(At(rpi,488,489)),      // .
        AST.Token.Identifier(At(rpi,489,494)),       // Empty
        AST.Token.Keyword(At(rpi,501,508)),          // handler
        AST.Token.Identifier(At(rpi,509,514)),       // Input
        AST.Token.Readability(At(rpi,515,517)),      // is
        AST.Token.Punctuation(At(rpi,518,519)),      // {
        AST.Token.Punctuation(At(rpi,520,523)),      // ???
        AST.Token.Punctuation(At(rpi,524,525)),      // }
        AST.Token.Punctuation(At(rpi,530,531)),      // }
        AST.Token.Punctuation(At(rpi,534,535)),      // }
        AST.Token.Keyword(At(rpi,540,547)),          // context
        AST.Token.Identifier(At(rpi,548,555)),       // Kitchen
        AST.Token.Readability(At(rpi,556,558)),      // is
        AST.Token.Punctuation(At(rpi,559,560)),      // {
        AST.Token.Keyword(At(rpi,565,569)),          // type
        AST.Token.Identifier(At(rpi,570,580)),       // IP4Address
        AST.Token.Readability(At(rpi,581,583)),      // is
        AST.Token.Punctuation(At(rpi,584,585)),      // {
        AST.Token.Identifier(At(rpi,586,587)),       // a
        AST.Token.Punctuation(At(rpi,587,588)),      // :
        AST.Token.Predefined(At(rpi,589,595)),       // Number
        AST.Token.Punctuation(At(rpi,595,596)),      // ,
        AST.Token.Identifier(At(rpi,597,598)),       // b
        AST.Token.Punctuation(At(rpi,598,599)),      // :
        AST.Token.Predefined(At(rpi,600,606)),       // Number
        AST.Token.Punctuation(At(rpi,606,607)),      // ,
        AST.Token.Identifier(At(rpi,608,609)),       // c
        AST.Token.Punctuation(At(rpi,609,610)),      // :
        AST.Token.Predefined(At(rpi,611,617)),       // Number
        AST.Token.Punctuation(At(rpi,617,618)),      // ,
        AST.Token.Identifier(At(rpi,619,620)),       // d
        AST.Token.Punctuation(At(rpi,620,621)),      // :
        AST.Token.Predefined(At(rpi,622,628)),       // Number
        AST.Token.Punctuation(At(rpi,628,629)),      // }
        AST.Token.Keyword(At(rpi,634,638)),          // type
        AST.Token.Identifier(At(rpi,639,652)),       // OrderViewType
        AST.Token.Readability(At(rpi,653,655)),      // is
        AST.Token.Punctuation(At(rpi,656,657)),      // {
        AST.Token.Identifier(At(rpi,664,671)),       // address
        AST.Token.Punctuation(At(rpi,671,672)),      // :
        AST.Token.Keyword(At(rpi,674,678)),          // type
        AST.Token.Identifier(At(rpi,679,689)),       // IP4Address
        AST.Token.Punctuation(At(rpi,694,695)),      // }
        AST.Token.Keyword(At(rpi,700,706)),          // entity
        AST.Token.Identifier(At(rpi,707,718)),       // OrderViewer
        AST.Token.Readability(At(rpi,719,721)),      // is
        AST.Token.Punctuation(At(rpi,722,723)),      // {
        AST.Token.Keyword(At(rpi,729,735)),          // option
        AST.Token.Readability(At(rpi,736,738)),      // is
        AST.Token.Identifier(At(rpi,739,743)),       // kind
        AST.Token.Punctuation(At(rpi,743,744)),      // (
        AST.Token.QuotedString(At(rpi,744,752)),     // "device"
        AST.Token.Punctuation(At(rpi,752,753)),      // )
        AST.Token.Keyword(At(rpi,759,765)),          // record
        AST.Token.Identifier(At(rpi,766,772)),       // AField
        AST.Token.Readability(At(rpi,773,775)),      // is
        AST.Token.Punctuation(At(rpi,776,777)),      // {
        AST.Token.Keyword(At(rpi,778,783)),          // field
        AST.Token.Punctuation(At(rpi,783,784)),      // :
        AST.Token.Keyword(At(rpi,785,789)),          // type
        AST.Token.Identifier(At(rpi,790,803)),       // OrderViewType
        AST.Token.Punctuation(At(rpi,804,805)),      // }
        AST.Token.Keyword(At(rpi,811,816)),          // state
        AST.Token.Identifier(At(rpi,817,827)),       // OrderState
        AST.Token.Readability(At(rpi,828,830)),      // of
        AST.Token.Identifier(At(rpi,831,842)),       // OrderViewer
        AST.Token.Punctuation(At(rpi,842,843)),      // .
        AST.Token.Identifier(At(rpi,843,849)),       // AField
        AST.Token.Keyword(At(rpi,855,862)),          // handler
        AST.Token.Identifier(At(rpi,863,868)),       // Input
        AST.Token.Readability(At(rpi,869,871)),      // is
        AST.Token.Punctuation(At(rpi,872,873)),      // {
        AST.Token.Punctuation(At(rpi,874,877)),      // ???
        AST.Token.Punctuation(At(rpi,878,879)),      // }
        AST.Token.Punctuation(At(rpi,884,885)),      // }
        AST.Token.Keyword(At(rpi,886,890)),          // with
        AST.Token.Punctuation(At(rpi,891,892)),      // {
        AST.Token.Keyword(At(rpi,898,907)),          // explained
        AST.Token.Readability(At(rpi,908,910)),      // as
        AST.Token.Punctuation(At(rpi,911,912)),      // {
        AST.Token.MarkdownLine(At(rpi,920,928)),     // |# brief
        AST.Token.MarkdownLine(At(rpi,936,959)),     // |This is an OrderViewer
        AST.Token.MarkdownLine(At(rpi,967,977)),     // |# details
        AST.Token.MarkdownLine(At(rpi,985,1056)),    // |The OrderViewer is the device in the kitchen, probably a touch screen,
        AST.Token.MarkdownLine(At(rpi,1064,1122)),   // |that the cooks use to view the sequence of orders to cook
        AST.Token.MarkdownLine(At(rpi,1130,1136)),   // |# see
        AST.Token.MarkdownLine(At(rpi,1144,1162)),   // |* http://foo.com/
        AST.Token.Punctuation(At(rpi,1168,1169)),    // }
        AST.Token.Punctuation(At(rpi,1173,1174)),    // }
        AST.Token.Punctuation(At(rpi,1176,1177)),    // }
        AST.Token.Keyword(At(rpi,1178,1182)),        // with
        AST.Token.Punctuation(At(rpi,1183,1184)),    // {
        AST.Token.Keyword(At(rpi,1187,1196)),        // explained
        AST.Token.Readability(At(rpi,1197,1199)),    // as
        AST.Token.Punctuation(At(rpi,1200,1201)),    // {
        AST.Token.MarkdownLine(At(rpi,1208,1216)),   // |# brief
        AST.Token.MarkdownLine(At(rpi,1223,1261)),   // |The kitchen is where food is prepared
        AST.Token.MarkdownLine(At(rpi,1268,1278)),   // |# details
        AST.Token.MarkdownLine(At(rpi,1285,1356)),   // |The kitchen bounded context provides the ability for the kitchen staff
        AST.Token.MarkdownLine(At(rpi,1363,1434)),   // |to interact with orders they are preparing. The kitchen is a client of
        AST.Token.MarkdownLine(At(rpi,1441,1506)),   // |the orders bounded context and interacts with that context alone
        AST.Token.MarkdownLine(At(rpi,1513,1584)),   // |the outstanding orders to be prepared. Everything else that happens in
        AST.Token.MarkdownLine(At(rpi,1591,1659)),   // |the kitchen is out of scope for the restaurant automation software.
        AST.Token.MarkdownLine(At(rpi,1666,1720)),   // |Consequently, this bounded context is pretty minimal.
        AST.Token.MarkdownLine(At(rpi,1727,1762)),   // |### Subject-Verb-Object Statements
        AST.Token.MarkdownLine(At(rpi,1769,1796)),   // |1. Kitchen displays orders
        AST.Token.MarkdownLine(At(rpi,1803,1843)),   // |1. Order is sent to Kitchen for display
        AST.Token.MarkdownLine(At(rpi,1850,1897)),   // |1. Order sends order status changes to Kitchen
        AST.Token.MarkdownLine(At(rpi,1904,1944)),   // |1. Kitchen ignores drink items on order
        AST.Token.MarkdownLine(At(rpi,1951,1954)),   // |1.
        AST.Token.Punctuation(At(rpi,1959,1960)),    // }
        AST.Token.Punctuation(At(rpi,1963,1964)),    // }
        AST.Token.Keyword(At(rpi,1968,1975)),        // context
        AST.Token.Identifier(At(rpi,1976,1983)),     // Loyalty
        AST.Token.Readability(At(rpi,1984,1986)),    // is
        AST.Token.Punctuation(At(rpi,1987,1988)),    // {
        AST.Token.Keyword(At(rpi,1993,1997)),        // type
        AST.Token.Identifier(At(rpi,1998,2010)),     // AccrualEvent
        AST.Token.Readability(At(rpi,2011,2013)),    // is
        AST.Token.Punctuation(At(rpi,2014,2015)),    // {
        AST.Token.Keyword(At(rpi,2022,2026)),        // when
        AST.Token.Readability(At(rpi,2027,2029)),    // is
        AST.Token.Predefined(At(rpi,2030,2039)),     // Timestamp
        AST.Token.Punctuation(At(rpi,2039,2040)),    // ,
        AST.Token.Identifier(At(rpi,2047,2050)),     // who
        AST.Token.Punctuation(At(rpi,2050,2051)),    // :
        AST.Token.Identifier(At(rpi,2053,2063)),     // CustomerId
        AST.Token.Punctuation(At(rpi,2063,2064)),    // ,
        AST.Token.Identifier(At(rpi,2071,2084)),     // pointsAccrued
        AST.Token.Readability(At(rpi,2085,2088)),    // are
        AST.Token.Predefined(At(rpi,2089,2095)),     // Number
        AST.Token.Punctuation(At(rpi,2095,2096)),    // ,
        AST.Token.Identifier(At(rpi,2103,2112)),     // fromOrder
        AST.Token.Punctuation(At(rpi,2113,2114)),    // =
        AST.Token.Identifier(At(rpi,2115,2122)),     // OrderId
        AST.Token.Punctuation(At(rpi,2127,2128)),    // }
        AST.Token.Keyword(At(rpi,2133,2137)),        // type
        AST.Token.Identifier(At(rpi,2138,2148)),     // AwardEvent
        AST.Token.Readability(At(rpi,2149,2151)),    // is
        AST.Token.Punctuation(At(rpi,2152,2153)),    // {
        AST.Token.Keyword(At(rpi,2160,2164)),        // when
        AST.Token.Readability(At(rpi,2165,2167)),    // is
        AST.Token.Predefined(At(rpi,2168,2177)),     // TimeStamp
        AST.Token.Punctuation(At(rpi,2177,2178)),    // ,
        AST.Token.Identifier(At(rpi,2185,2188)),     // who
        AST.Token.Readability(At(rpi,2189,2191)),    // is
        AST.Token.Identifier(At(rpi,2192,2202)),     // CustomerId
        AST.Token.Punctuation(At(rpi,2202,2203)),    // ,
        AST.Token.Identifier(At(rpi,2210,2223)),     // poinstAwarded
        AST.Token.Readability(At(rpi,2224,2226)),    // is
        AST.Token.Predefined(At(rpi,2227,2233)),     // Number
        AST.Token.Punctuation(At(rpi,2233,2234)),    // ,
        AST.Token.Identifier(At(rpi,2241,2248)),     // toOrder
        AST.Token.Readability(At(rpi,2249,2251)),    // is
        AST.Token.Identifier(At(rpi,2252,2259)),     // OrderId
        AST.Token.Punctuation(At(rpi,2264,2265)),    // }
        AST.Token.Keyword(At(rpi,2270,2274)),        // type
        AST.Token.Identifier(At(rpi,2275,2286)),     // RewardEvent
        AST.Token.Readability(At(rpi,2287,2289)),    // is
        AST.Token.Keyword(At(rpi,2290,2293)),        // one
        AST.Token.Readability(At(rpi,2294,2296)),    // of
        AST.Token.Punctuation(At(rpi,2297,2298)),    // {
        AST.Token.Identifier(At(rpi,2299,2311)),     // AccrualEvent
        AST.Token.Identifier(At(rpi,2312,2314)),     // or
        AST.Token.Identifier(At(rpi,2315,2325)),     // AwardEvent
        AST.Token.Punctuation(At(rpi,2326,2327)),    // }
        AST.Token.Keyword(At(rpi,2332,2338)),        // entity
        AST.Token.Identifier(At(rpi,2339,2353)),     // RewardsAccount
        AST.Token.Readability(At(rpi,2354,2356)),    // is
        AST.Token.Punctuation(At(rpi,2357,2358)),    // {
        AST.Token.Keyword(At(rpi,2365,2371)),        // record
        AST.Token.Identifier(At(rpi,2372,2378)),     // Fields
        AST.Token.Readability(At(rpi,2379,2381)),    // is
        AST.Token.Punctuation(At(rpi,2382,2383)),    // {
        AST.Token.Identifier(At(rpi,2392,2394)),     // id
        AST.Token.Readability(At(rpi,2395,2397)),    // is
        AST.Token.Identifier(At(rpi,2398,2408)),     // CustomerId
        AST.Token.Punctuation(At(rpi,2408,2409)),    // ,
        AST.Token.Identifier(At(rpi,2418,2424)),     // points
        AST.Token.Readability(At(rpi,2425,2427)),    // is
        AST.Token.Predefined(At(rpi,2428,2434)),     // Number
        AST.Token.Punctuation(At(rpi,2434,2435)),    // ,
        AST.Token.Identifier(At(rpi,2444,2456)),     // rewardEvents
        AST.Token.Readability(At(rpi,2457,2459)),    // is
        AST.Token.Keyword(At(rpi,2460,2464)),        // many
        AST.Token.Keyword(At(rpi,2465,2473)),        // optional
        AST.Token.Identifier(At(rpi,2474,2485)),     // RewardEvent
        AST.Token.Punctuation(At(rpi,2492,2493)),    // }
        AST.Token.Keyword(At(rpi,2500,2505)),        // state
        AST.Token.Identifier(At(rpi,2506,2517)),     // RewardState
        AST.Token.Readability(At(rpi,2518,2520)),    // of
        AST.Token.Identifier(At(rpi,2521,2535)),     // RewardsAccount
        AST.Token.Punctuation(At(rpi,2535,2536)),    // .
        AST.Token.Identifier(At(rpi,2536,2542)),     // Fields
        AST.Token.Keyword(At(rpi,2549,2556)),        // handler
        AST.Token.Identifier(At(rpi,2557,2563)),     // Inputs
        AST.Token.Readability(At(rpi,2564,2566)),    // is
        AST.Token.Punctuation(At(rpi,2567,2568)),    // {
        AST.Token.Punctuation(At(rpi,2569,2572)),    // ???
        AST.Token.Punctuation(At(rpi,2573,2574)),    // }
        AST.Token.Punctuation(At(rpi,2579,2580)),    // }
        AST.Token.Keyword(At(rpi,2586,2593)),        // adaptor
        AST.Token.Identifier(At(rpi,2594,2608)),     // PaymentAdapter
        AST.Token.Keyword(At(rpi,2609,2613)),        // from
        AST.Token.Keyword(At(rpi,2614,2621)),        // context
        AST.Token.Identifier(At(rpi,2622,2633)),     // ReactiveBBQ
        AST.Token.Punctuation(At(rpi,2633,2634)),    // .
        AST.Token.Identifier(At(rpi,2634,2641)),     // Payment
        AST.Token.Readability(At(rpi,2642,2644)),    // is
        AST.Token.Punctuation(At(rpi,2645,2646)),    // {
        AST.Token.Punctuation(At(rpi,2653,2656)),    // ???
        AST.Token.Punctuation(At(rpi,2661,2662)),    // }
        AST.Token.Punctuation(At(rpi,2665,2666)),    // }
        AST.Token.Keyword(At(rpi,2670,2677)),        // context
        AST.Token.Identifier(At(rpi,2678,2683)),     // Order
        AST.Token.Readability(At(rpi,2684,2686)),    // is
        AST.Token.Punctuation(At(rpi,2687,2688)),    // {
        AST.Token.Keyword(At(rpi,2693,2699)),        // entity
        AST.Token.Identifier(At(rpi,2700,2705)),     // Order
        AST.Token.Readability(At(rpi,2706,2708)),    // is
        AST.Token.Punctuation(At(rpi,2709,2710)),    // {
        AST.Token.Keyword(At(rpi,2717,2723)),        // option
        AST.Token.Readability(At(rpi,2724,2726)),    // is
        AST.Token.Identifier(At(rpi,2727,2736)),     // aggregate
        AST.Token.Keyword(At(rpi,2743,2749)),
        AST.Token.Identifier(At(rpi,2750,2756)),
        AST.Token.Readability(At(rpi,2757,2759)),
        AST.Token.Punctuation(At(rpi,2760,2761)),
        AST.Token.Identifier(At(rpi,2770,2777)),
        AST.Token.Readability(At(rpi,2778,2780)),
        AST.Token.Identifier(At(rpi,2781,2788)),
        AST.Token.Punctuation(At(rpi,2788,2789)),
        AST.Token.Identifier(At(rpi,2798,2808)),
        AST.Token.Readability(At(rpi,2809,2811)),
        AST.Token.Identifier(At(rpi,2812,2822)),
        AST.Token.Punctuation(At(rpi,2829,2830)),
        AST.Token.Keyword(At(rpi,2837,2842)),
        AST.Token.Identifier(At(rpi,2843,2853)),
        AST.Token.Readability(At(rpi,2854,2856)),
        AST.Token.Identifier(At(rpi,2857,2862)),
        AST.Token.Punctuation(At(rpi,2862,2863)),
        AST.Token.Identifier(At(rpi,2863,2869)),
        AST.Token.Keyword(At(rpi,2876,2883)),
        AST.Token.Identifier(At(rpi,2884,2887)),
        AST.Token.Readability(At(rpi,2889,2891)),
        AST.Token.Punctuation(At(rpi,2892,2893)),
        AST.Token.Punctuation(At(rpi,2893,2894)),
        AST.Token.Punctuation(At(rpi,2899,2900)),
        AST.Token.Punctuation(At(rpi,2903,2904)),
        AST.Token.Keyword(At(rpi,2908,2915)),
        AST.Token.Identifier(At(rpi,2916,2923)),
        AST.Token.Readability(At(rpi,2924,2926)),
        AST.Token.Punctuation(At(rpi,2927,2928)),
        AST.Token.Keyword(At(rpi,2933,2939)),
        AST.Token.Identifier(At(rpi,2940,2947)),
        AST.Token.Readability(At(rpi,2948,2950)),
        AST.Token.Punctuation(At(rpi,2951,2952)),
        AST.Token.Keyword(At(rpi,2959,2965)),
        AST.Token.Readability(At(rpi,2966,2968)),
        AST.Token.Identifier(At(rpi,2969,2978)),
        AST.Token.Keyword(At(rpi,2985,2991)),
        AST.Token.Identifier(At(rpi,2992,2998)),
        AST.Token.Readability(At(rpi,2999,3001)),
        AST.Token.Punctuation(At(rpi,3002,3003)),
        AST.Token.Identifier(At(rpi,3012,3019)),
        AST.Token.Readability(At(rpi,3020,3022)),
        AST.Token.Identifier(At(rpi,3023,3030)),
        AST.Token.Punctuation(At(rpi,3030,3031)),
        AST.Token.Identifier(At(rpi,3040,3046)),
        AST.Token.Readability(At(rpi,3047,3049)),
        AST.Token.Predefined(At(rpi,3050,3056)),
        AST.Token.Punctuation(At(rpi,3056,3057)),
        AST.Token.Identifier(At(rpi,3066,3075)),
        AST.Token.Readability(At(rpi,3076,3078)),
        AST.Token.Predefined(At(rpi,3079,3085)),
        AST.Token.Punctuation(At(rpi,3092,3093)),
        AST.Token.Keyword(At(rpi,3100,3105)),
        AST.Token.Identifier(At(rpi,3106,3118)),
        AST.Token.Readability(At(rpi,3119,3121)),
        AST.Token.Identifier(At(rpi,3122,3129)),
        AST.Token.Punctuation(At(rpi,3129,3130)),
        AST.Token.Identifier(At(rpi,3130,3136)),
        AST.Token.Keyword(At(rpi,3143,3150)),
        AST.Token.Identifier(At(rpi,3151,3154)),
        AST.Token.Readability(At(rpi,3155,3157)),
        AST.Token.Punctuation(At(rpi,3158,3159)),
        AST.Token.Punctuation(At(rpi,3160,3163)),
        AST.Token.Punctuation(At(rpi,3164,3165)),
        AST.Token.Punctuation(At(rpi,3170,3171)),
        AST.Token.Punctuation(At(rpi,3174,3175)),
        AST.Token.Keyword(At(rpi,3179,3186)),
        AST.Token.Identifier(At(rpi,3187,3191)),
        AST.Token.Readability(At(rpi,3192,3194)),
        AST.Token.Punctuation(At(rpi,3195,3196)),
        AST.Token.Keyword(At(rpi,3201,3207)),
        AST.Token.Identifier(At(rpi,3208,3216)),
        AST.Token.Readability(At(rpi,3217,3219)),
        AST.Token.Punctuation(At(rpi,3220,3221)),
        AST.Token.Keyword(At(rpi,3228,3234)),
        AST.Token.Identifier(At(rpi,3235,3241)),
        AST.Token.Readability(At(rpi,3242,3244)),
        AST.Token.Punctuation(At(rpi,3245,3246)),
        AST.Token.Identifier(At(rpi,3247,3256)),
        AST.Token.Punctuation(At(rpi,3256,3257)),
        AST.Token.Predefined(At(rpi,3258,3264)),
        AST.Token.Punctuation(At(rpi,3265,3266)),
        AST.Token.Keyword(At(rpi,3273,3278)),
        AST.Token.Identifier(At(rpi,3279,3288)),
        AST.Token.Readability(At(rpi,3289,3291)),
        AST.Token.Identifier(At(rpi,3292,3300)),
        AST.Token.Punctuation(At(rpi,3300,3301)),
        AST.Token.Identifier(At(rpi,3301,3307)),
        AST.Token.Keyword(At(rpi,3314,3321)),
        AST.Token.Identifier(At(rpi,3322,3325)),
        AST.Token.Readability(At(rpi,3326,3328)),
        AST.Token.Punctuation(At(rpi,3329,3330)),
        AST.Token.Punctuation(At(rpi,3330,3331)),
        AST.Token.Punctuation(At(rpi,3336,3337)),
        AST.Token.Keyword(At(rpi,3342,3346)),
        AST.Token.Identifier(At(rpi,3347,3358)),
        AST.Token.Readability(At(rpi,3359,3361)),
        AST.Token.Keyword(At(rpi,3362,3371)),
        AST.Token.Readability(At(rpi,3372,3374)),
        AST.Token.Keyword(At(rpi,3375,3381)),
        AST.Token.Identifier(At(rpi,3382,3390)),
        AST.Token.Keyword(At(rpi,3395,3401)),
        AST.Token.Identifier(At(rpi,3402,3406)),
        AST.Token.Readability(At(rpi,3407,3409)),
        AST.Token.Punctuation(At(rpi,3410,3411)),
        AST.Token.Keyword(At(rpi,3418,3424)),
        AST.Token.Readability(At(rpi,3425,3427)),
        AST.Token.Identifier(At(rpi,3428,3437)),
        AST.Token.Keyword(At(rpi,3444,3450)),
        AST.Token.Identifier(At(rpi,3451,3457)),
        AST.Token.Readability(At(rpi,3458,3460)),
        AST.Token.Punctuation(At(rpi,3461,3462)),
        AST.Token.Keyword(At(rpi,3463,3468)),
        AST.Token.Punctuation(At(rpi,3468,3469)),
        AST.Token.Keyword(At(rpi,3470,3474)),
        AST.Token.Identifier(At(rpi,3475,3486)),
        AST.Token.Punctuation(At(rpi,3487,3488)),
        AST.Token.Keyword(At(rpi,3495,3500)),
        AST.Token.Identifier(At(rpi,3501,3508)),
        AST.Token.Readability(At(rpi,3509,3511)),
        AST.Token.Identifier(At(rpi,3512,3516)),
        AST.Token.Punctuation(At(rpi,3516,3517)),
        AST.Token.Identifier(At(rpi,3517,3523)),
        AST.Token.Keyword(At(rpi,3530,3537)),
        AST.Token.Identifier(At(rpi,3538,3541)),
        AST.Token.Readability(At(rpi,3542,3544)),
        AST.Token.Punctuation(At(rpi,3545,3546)),
        AST.Token.Punctuation(At(rpi,3547,3550)),
        AST.Token.Punctuation(At(rpi,3551,3552)),
        AST.Token.Punctuation(At(rpi,3557,3558)),
        AST.Token.Punctuation(At(rpi,3561,3562)),
        AST.Token.Keyword(At(rpi,3566,3573)),
        AST.Token.Identifier(At(rpi,3574,3585)),
        AST.Token.Readability(At(rpi,3586,3588)),
        AST.Token.Punctuation(At(rpi,3589,3590)),
        AST.Token.Keyword(At(rpi,3595,3599)),
        AST.Token.Identifier(At(rpi,3600,3616)),
        AST.Token.Readability(At(rpi,3617,3619)),
        AST.Token.Punctuation(At(rpi,3620,3621)),
        AST.Token.Identifier(At(rpi,3628,3637)),
        AST.Token.Readability(At(rpi,3638,3640)),
        AST.Token.Predefined(At(rpi,3641,3647)),
        AST.Token.Punctuation(At(rpi,3647,3648)),
        AST.Token.Identifier(At(rpi,3655,3666)),
        AST.Token.Readability(At(rpi,3667,3669)),
        AST.Token.Predefined(At(rpi,3670,3676)),
        AST.Token.Punctuation(At(rpi,3676,3677)),
        AST.Token.Identifier(At(rpi,3684,3692)),
        AST.Token.Readability(At(rpi,3693,3695)),
        AST.Token.Predefined(At(rpi,3696,3698)),
        AST.Token.Punctuation(At(rpi,3698,3699)),
        AST.Token.Predefined(At(rpi,3699,3707)),
        AST.Token.Punctuation(At(rpi,3707,3708)),
        AST.Token.Punctuation(At(rpi,3708,3709)),
        AST.Token.Identifier(At(rpi,3716,3720)),
        AST.Token.Readability(At(rpi,3721,3723)),
        AST.Token.Predefined(At(rpi,3724,3728)),
        AST.Token.Punctuation(At(rpi,3728,3729)),
        AST.Token.Identifier(At(rpi,3736,3740)),
        AST.Token.Readability(At(rpi,3741,3743)),
        AST.Token.Predefined(At(rpi,3744,3748)),
        AST.Token.Punctuation(At(rpi,3753,3754)),
        AST.Token.Keyword(At(rpi,3759,3765)),
        AST.Token.Predefined(At(rpi,3766,3774)),
        AST.Token.Readability(At(rpi,3775,3777)),
        AST.Token.Punctuation(At(rpi,3778,3779)),
        AST.Token.Keyword(At(rpi,3786,3792)),
        AST.Token.Identifier(At(rpi,3793,3799)),
        AST.Token.Readability(At(rpi,3800,3802)),
        AST.Token.Punctuation(At(rpi,3803,3804)),
        AST.Token.Keyword(At(rpi,3805,3809)),
        AST.Token.Punctuation(At(rpi,3809,3810)),
        AST.Token.Predefined(At(rpi,3811,3817)),
        AST.Token.Punctuation(At(rpi,3818,3819)),
        AST.Token.Keyword(At(rpi,3826,3831)),
        AST.Token.Identifier(At(rpi,3832,3839)),
        AST.Token.Readability(At(rpi,3840,3842)),
        AST.Token.Predefined(At(rpi,3843,3851)),
        AST.Token.Punctuation(At(rpi,3851,3852)),
        AST.Token.Identifier(At(rpi,3852,3858)),
        AST.Token.Keyword(At(rpi,3865,3872)),
        AST.Token.Identifier(At(rpi,3873,3876)),
        AST.Token.Readability(At(rpi,3877,3879)),
        AST.Token.Punctuation(At(rpi,3880,3881)),
        AST.Token.Punctuation(At(rpi,3881,3882)),
        AST.Token.Punctuation(At(rpi,3887,3888)),
        AST.Token.Keyword(At(rpi,3889,3893)),
        AST.Token.Punctuation(At(rpi,3894,3895)),
        AST.Token.Keyword(At(rpi,3896,3905)),
        AST.Token.Readability(At(rpi,3906,3908)),
        AST.Token.QuotedString(At(rpi,3909,3942)),
        AST.Token.Punctuation(At(rpi,3943,3944)),
        AST.Token.Keyword(At(rpi,3950,3956)),
        AST.Token.Identifier(At(rpi,3957,3968)),
        AST.Token.Readability(At(rpi,3969,3971)),
        AST.Token.Punctuation(At(rpi,3972,3973)),
        AST.Token.Keyword(At(rpi,3979,3985)),
        AST.Token.Identifier(At(rpi,3986,3995)),
        AST.Token.Keyword(At(rpi,4001,4007)),
        AST.Token.Identifier(At(rpi,4008,4014)),
        AST.Token.Readability(At(rpi,4015,4017)),
        AST.Token.Punctuation(At(rpi,4018,4019)),
        AST.Token.Identifier(At(rpi,4020,4025)),
        AST.Token.Punctuation(At(rpi,4025,4026)),
        AST.Token.Identifier(At(rpi,4027,4043)),
        AST.Token.Punctuation(At(rpi,4044,4045)),
        AST.Token.Keyword(At(rpi,4051,4056)),
        AST.Token.Identifier(At(rpi,4057,4068)),
        AST.Token.Readability(At(rpi,4069,4071)),
        AST.Token.Identifier(At(rpi,4072,4083)),
        AST.Token.Punctuation(At(rpi,4083,4084)),
        AST.Token.Identifier(At(rpi,4084,4090)),
        AST.Token.Keyword(At(rpi,4096,4103)),
        AST.Token.Identifier(At(rpi,4104,4112)),
        AST.Token.Readability(At(rpi,4113,4115)),
        AST.Token.Punctuation(At(rpi,4116,4117)),
        AST.Token.Punctuation(At(rpi,4117,4118)),
        AST.Token.Punctuation(At(rpi,4123,4124)),
        AST.Token.Punctuation(At(rpi,4127,4128)),
        AST.Token.Punctuation(At(rpi,4129,4130)),
        AST.Token.Keyword(At(rpi,4131,4135)),
        AST.Token.Punctuation(At(rpi,4136,4137)),
        AST.Token.Keyword(At(rpi,4140,4149)),      // explained
        AST.Token.Readability(At(rpi,4150,4152)),  // as
        AST.Token.Punctuation(At(rpi,4153,4154)),  // {
        AST.Token.MarkdownLine(At(rpi,4159,4167)),
        AST.Token.MarkdownLine(At(rpi,4172,4204)),
        AST.Token.MarkdownLine(At(rpi,4209,4218)),
        AST.Token.MarkdownLine(At(rpi,4223,4299)),
        AST.Token.MarkdownLine(At(rpi,4304,4378)),
        AST.Token.MarkdownLine(At(rpi,4383,4459)),
        AST.Token.MarkdownLine(At(rpi,4464,4541)),
        AST.Token.MarkdownLine(At(rpi,4546,4622)),
        AST.Token.Punctuation(At(rpi,4625,4626)),  // }
        AST.Token.Punctuation(At(rpi,4627,4628)), // }
        AST.Token.Comment(At(rpi,4629,4646)) // // #end-of-domain
      )
      val result = pc.withOptions(pc.options.copy(showTimes=true)) { _ =>
        Timer.time("parseToTokens") {
          TopLevelParser.parseToTokens(rpi)
        }
      }
      result match
        case Left(messages) =>
          fail(messages.format)
        case Right(tokens) =>
          val sample = tokens.take(expectedTokens.length).toString
          val expected = expectedTokens.toString
          sample must be(expected)
      end match
    }
    Await.result(future, 30)

  }
}
