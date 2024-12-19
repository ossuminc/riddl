/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.CommentTKN
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.language.{AST,At}
import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.utils.{Await, PlatformContext, Timer, URL}
import org.scalatest.TestData

import scala.concurrent.ExecutionContext
import scala.io.AnsiColor.{GREEN, RED, RESET}

abstract class TokenStreamParserTest(using pc: PlatformContext) extends AbstractParsingTest {
  "TokenStreamParser" must {
    "handle simple document fragment" in { (td: TestData) =>
      val rpi: RiddlParserInput = RiddlParserInput(
        """module foo is {
          |   // this is a comment
          |   domain blah is { ??? }
          |   invariant bar is "condition"
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
            AST.KeywordTKN(At(rpi, 0, 6)),
            AST.IdentifierTKN(At(rpi, 7, 10)),
            AST.ReadabilityTKN(At(rpi, 11, 13)),
            AST.PunctuationTKN(At(rpi, 14, 15)),
            AST.CommentTKN(At(rpi, 19, 39)),
            AST.KeywordTKN(At(rpi, 43, 49)),
            AST.IdentifierTKN(At(rpi, 50, 54)),
            AST.ReadabilityTKN(At(rpi, 55, 57)),
            AST.PunctuationTKN(At(rpi, 58, 59)),
            AST.PunctuationTKN(At(rpi, 60, 63)),
            AST.PunctuationTKN(At(rpi, 64, 65)),
            AST.KeywordTKN(At(rpi, 69, 78)),
            AST.IdentifierTKN(At(rpi, 79, 82)),
            AST.ReadabilityTKN(At(rpi, 83, 85)),
            AST.QuotedStringTKN(At(rpi, 86, 97)),
            AST.PunctuationTKN(At(rpi, 98, 99))
          )
          tokens must be(expected)
    }
  }

  "handle rbbq.riddl, a more complete example" in { (td: TestData) =>
    implicit val ec: ExecutionContext = pc.ec
    val url = URL.fromCwdPath("language/input/rbbq.riddl")
    val future = RiddlParserInput.fromURL(url, td).map { rpi =>
      val expectedTokens: List[AST.Token] = List(
        KeywordTKN(At(rpi,0,6)),              // domain
        IdentifierTKN(At(rpi,7,18)),          // ReactiveBBQ
        ReadabilityTKN(At(rpi,19,21)),        // is
        PunctuationTKN(At(rpi,22,23)),        // {
        KeywordTKN(At(rpi,27,31)),            // type
        IdentifierTKN(At(rpi,32,42)),         // CustomerId
        ReadabilityTKN(At(rpi,43,45)),        // is
        PredefinedTKN(At(rpi,46,48)),         // Id
        PunctuationTKN(At(rpi,48,49)),        // (
        IdentifierTKN(At(rpi,49,60)),         // ReactiveBBQ
        PunctuationTKN(At(rpi,60,61)),        // .
        IdentifierTKN(At(rpi,61,69)),         // Customer
        PunctuationTKN(At(rpi,69,70)),        // .
        IdentifierTKN(At(rpi,70,78)),         // Customer
        PunctuationTKN(At(rpi,78,79)),        // )
        KeywordTKN(At(rpi,80,84)),            // with
        PunctuationTKN(At(rpi,85,86)),        // {
        KeywordTKN(At(rpi,91,100)),           // described
        ReadabilityTKN(At(rpi,101,103)),      // as
        PunctuationTKN(At(rpi,104,105)),      // {
        QuotedStringTKN(At(rpi,112,146)),     // "Unique identifier for a customer"
        PunctuationTKN(At(rpi,151,152)),      // }
        PunctuationTKN(At(rpi,155,156)),      // }
        KeywordTKN(At(rpi,160,164)),          // type
        IdentifierTKN(At(rpi,165,172)),       // OrderId
        ReadabilityTKN(At(rpi,173,175)),      // is
        PredefinedTKN(At(rpi,176,178)),       // Id
        PunctuationTKN(At(rpi,178,179)),      // (
        IdentifierTKN(At(rpi,179,190)),       // ReactiveBBQ
        PunctuationTKN(At(rpi,190,191)),      // .
        IdentifierTKN(At(rpi,191,196)),       // Order
        PunctuationTKN(At(rpi,196,197)),      // .
        IdentifierTKN(At(rpi,197,202)),       // Order
        PunctuationTKN(At(rpi,202,203)),      // )
        KeywordTKN(At(rpi,204,208)),          // with
        PunctuationTKN(At(rpi,209,210)),      // {
        KeywordTKN(At(rpi,215,224)),          // described
        ReadabilityTKN(At(rpi,225,227)),      // as
        PunctuationTKN(At(rpi,228,229)),      // {
        MarkdownLineTKN(At(rpi,236,244)),     // |# brief
        MarkdownLineTKN(At(rpi,251,292)),     // |Unique identifier for a customer's order
        MarkdownLineTKN(At(rpi,299,305)),     // |# see
        MarkdownLineTKN(At(rpi,312,372)),     // |* [OrderId](http://www.example.com/show/details/on/OrderId)
        PunctuationTKN(At(rpi,377,378)),      // }
        PunctuationTKN(At(rpi,381,382)),      // }
        KeywordTKN(At(rpi,386,390)),          // type
        IdentifierTKN(At(rpi,391,396)),       // Empty
        PunctuationTKN(At(rpi,397,398)),      // =
        PredefinedTKN(At(rpi,399,406)),       // Nothing
        KeywordTKN(At(rpi,410,417)),          // context
        IdentifierTKN(At(rpi,418,426)),       // Customer
        ReadabilityTKN(At(rpi,427,429)),      // is
        PunctuationTKN(At(rpi,430,431)),      // {
        KeywordTKN(At(rpi,436,442)),          // entity
        IdentifierTKN(At(rpi,443,451)),       // Customer
        ReadabilityTKN(At(rpi,452,454)),      // is
        PunctuationTKN(At(rpi,455,456)),      // {
        KeywordTKN(At(rpi,463,468)),          // state
        IdentifierTKN(At(rpi,469,473)),       // main
        ReadabilityTKN(At(rpi,474,476)),      // of
        IdentifierTKN(At(rpi,477,488)),       // ReactiveBBQ
        PunctuationTKN(At(rpi,488,489)),      // .
        IdentifierTKN(At(rpi,489,494)),       // Empty
        KeywordTKN(At(rpi,501,508)),          // handler
        IdentifierTKN(At(rpi,509,514)),       // Input
        ReadabilityTKN(At(rpi,515,517)),      // is
        PunctuationTKN(At(rpi,518,519)),      // {
        PunctuationTKN(At(rpi,520,523)),      // ???
        PunctuationTKN(At(rpi,524,525)),      // }
        PunctuationTKN(At(rpi,530,531)),      // }
        PunctuationTKN(At(rpi,534,535)),      // }
        KeywordTKN(At(rpi,540,547)),          // context
        IdentifierTKN(At(rpi,548,555)),       // Kitchen
        ReadabilityTKN(At(rpi,556,558)),      // is
        PunctuationTKN(At(rpi,559,560)),      // {
        KeywordTKN(At(rpi,565,569)),          // type
        IdentifierTKN(At(rpi,570,580)),       // IP4Address
        ReadabilityTKN(At(rpi,581,583)),      // is
        PunctuationTKN(At(rpi,584,585)),      // {
        IdentifierTKN(At(rpi,586,587)),       // a
        PunctuationTKN(At(rpi,587,588)),      // :
        PredefinedTKN(At(rpi,589,595)),       // Number
        PunctuationTKN(At(rpi,595,596)),      // ,
        IdentifierTKN(At(rpi,597,598)),       // b
        PunctuationTKN(At(rpi,598,599)),      // :
        PredefinedTKN(At(rpi,600,606)),       // Number
        PunctuationTKN(At(rpi,606,607)),      // ,
        IdentifierTKN(At(rpi,608,609)),       // c
        PunctuationTKN(At(rpi,609,610)),      // :
        PredefinedTKN(At(rpi,611,617)),       // Number
        PunctuationTKN(At(rpi,617,618)),      // ,
        IdentifierTKN(At(rpi,619,620)),       // d
        PunctuationTKN(At(rpi,620,621)),      // :
        PredefinedTKN(At(rpi,622,628)),       // Number
        PunctuationTKN(At(rpi,628,629)),      // }
        KeywordTKN(At(rpi,634,638)),          // type
        IdentifierTKN(At(rpi,639,652)),       // OrderViewType
        ReadabilityTKN(At(rpi,653,655)),      // is
        PunctuationTKN(At(rpi,656,657)),      // {
        IdentifierTKN(At(rpi,664,671)),       // address
        PunctuationTKN(At(rpi,671,672)),      // :
        KeywordTKN(At(rpi,674,678)),          // type
        IdentifierTKN(At(rpi,679,689)),       // IP4Address
        PunctuationTKN(At(rpi,694,695)),      // }
        KeywordTKN(At(rpi,700,706)),          // entity
        IdentifierTKN(At(rpi,707,718)),       // OrderViewer
        ReadabilityTKN(At(rpi,719,721)),      // is
        PunctuationTKN(At(rpi,722,723)),      // {
        KeywordTKN(At(rpi,729,735)),          // option
        ReadabilityTKN(At(rpi,736,738)),      // is
        IdentifierTKN(At(rpi,739,743)),       // kind
        PunctuationTKN(At(rpi,743,744)),      // (
        QuotedStringTKN(At(rpi,744,752)),     // "device"
        PunctuationTKN(At(rpi,752,753)),      // )
        KeywordTKN(At(rpi,759,765)),          // record
        IdentifierTKN(At(rpi,766,772)),       // AField
        ReadabilityTKN(At(rpi,773,775)),      // is
        PunctuationTKN(At(rpi,776,777)),      // {
        KeywordTKN(At(rpi,778,783)),          // field
        PunctuationTKN(At(rpi,783,784)),      // :
        KeywordTKN(At(rpi,785,789)),          // type
        IdentifierTKN(At(rpi,790,803)),       // OrderViewType
        PunctuationTKN(At(rpi,804,805)),      // }
        KeywordTKN(At(rpi,811,816)),          // state
        IdentifierTKN(At(rpi,817,827)),       // OrderState
        ReadabilityTKN(At(rpi,828,830)),      // of
        IdentifierTKN(At(rpi,831,842)),       // OrderViewer
        PunctuationTKN(At(rpi,842,843)),      // .
        IdentifierTKN(At(rpi,843,849)),       // AField
        KeywordTKN(At(rpi,855,862)),          // handler
        IdentifierTKN(At(rpi,863,868)),       // Input
        ReadabilityTKN(At(rpi,869,871)),      // is
        PunctuationTKN(At(rpi,872,873)),      // {
        PunctuationTKN(At(rpi,874,877)),      // ???
        PunctuationTKN(At(rpi,878,879)),      // }
        PunctuationTKN(At(rpi,884,885)),      // }
        KeywordTKN(At(rpi,886,890)),          // with
        PunctuationTKN(At(rpi,891,892)),      // {
        KeywordTKN(At(rpi,898,907)),          // explained
        ReadabilityTKN(At(rpi,908,910)),      // as
        PunctuationTKN(At(rpi,911,912)),      // {
        MarkdownLineTKN(At(rpi,920,928)),     // |# brief
        MarkdownLineTKN(At(rpi,936,959)),     // |This is an OrderViewer
        MarkdownLineTKN(At(rpi,967,977)),     // |# details
        MarkdownLineTKN(At(rpi,985,1056)),    // |The OrderViewer is the device in the kitchen, probably a touch screen,
        MarkdownLineTKN(At(rpi,1064,1122)),   // |that the cooks use to view the sequence of orders to cook
        MarkdownLineTKN(At(rpi,1130,1136)),   // |# see
        MarkdownLineTKN(At(rpi,1144,1162)),   // |* http://foo.com/
        PunctuationTKN(At(rpi,1168,1169)),    // }
        PunctuationTKN(At(rpi,1173,1174)),    // }
        PunctuationTKN(At(rpi,1176,1177)),    // }
        KeywordTKN(At(rpi,1178,1182)),        // with
        PunctuationTKN(At(rpi,1183,1184)),    // {
        KeywordTKN(At(rpi,1187,1196)),        // explained
        ReadabilityTKN(At(rpi,1197,1199)),    // as
        PunctuationTKN(At(rpi,1200,1201)),    // {
        MarkdownLineTKN(At(rpi,1208,1216)),   // |# brief
        MarkdownLineTKN(At(rpi,1223,1261)),   // |The kitchen is where food is prepared
        MarkdownLineTKN(At(rpi,1268,1278)),   // |# details
        MarkdownLineTKN(At(rpi,1285,1356)),   // |The kitchen bounded context provides the ability for the kitchen staff
        MarkdownLineTKN(At(rpi,1363,1434)),   // |to interact with orders they are preparing. The kitchen is a client of
        MarkdownLineTKN(At(rpi,1441,1506)),   // |the orders bounded context and interacts with that context alone
        MarkdownLineTKN(At(rpi,1513,1584)),   // |the outstanding orders to be prepared. Everything else that happens in
        MarkdownLineTKN(At(rpi,1591,1659)),   // |the kitchen is out of scope for the restaurant automation software.
        MarkdownLineTKN(At(rpi,1666,1720)),   // |Consequently, this bounded context is pretty minimal.
        MarkdownLineTKN(At(rpi,1727,1762)),   // |### Subject-Verb-Object Statements
        MarkdownLineTKN(At(rpi,1769,1796)),   // |1. Kitchen displays orders
        MarkdownLineTKN(At(rpi,1803,1843)),   // |1. Order is sent to Kitchen for display
        MarkdownLineTKN(At(rpi,1850,1897)),   // |1. Order sends order status changes to Kitchen
        MarkdownLineTKN(At(rpi,1904,1944)),   // |1. Kitchen ignores drink items on order
        MarkdownLineTKN(At(rpi,1951,1954)),   // |1.
        PunctuationTKN(At(rpi,1959,1960)),    // }
        PunctuationTKN(At(rpi,1963,1964)),    // }
        KeywordTKN(At(rpi,1968,1975)),        // context
        IdentifierTKN(At(rpi,1976,1983)),     // Loyalty
        ReadabilityTKN(At(rpi,1984,1986)),    // is
        PunctuationTKN(At(rpi,1987,1988)),    // {
        KeywordTKN(At(rpi,1993,1997)),        // type
        IdentifierTKN(At(rpi,1998,2010)),     // AccrualEvent
        ReadabilityTKN(At(rpi,2011,2013)),    // is
        PunctuationTKN(At(rpi,2014,2015)),    // {
        KeywordTKN(At(rpi,2022,2026)),        // when
        ReadabilityTKN(At(rpi,2027,2029)),    // is
        PredefinedTKN(At(rpi,2030,2039)),     // Timestamp
        PunctuationTKN(At(rpi,2039,2040)),    // ,
        IdentifierTKN(At(rpi,2047,2050)),     // who
        PunctuationTKN(At(rpi,2050,2051)),    // :
        IdentifierTKN(At(rpi,2053,2063)),     // CustomerId
        PunctuationTKN(At(rpi,2063,2064)),    // ,
        IdentifierTKN(At(rpi,2071,2084)),     // pointsAccrued
        ReadabilityTKN(At(rpi,2085,2088)),    // are
        PredefinedTKN(At(rpi,2089,2095)),     // Number
        PunctuationTKN(At(rpi,2095,2096)),    // ,
        IdentifierTKN(At(rpi,2103,2112)),     // fromOrder
        PunctuationTKN(At(rpi,2113,2114)),    // =
        IdentifierTKN(At(rpi,2115,2122)),     // OrderId
        PunctuationTKN(At(rpi,2127,2128)),    // }
        KeywordTKN(At(rpi,2133,2137)),        // type
        IdentifierTKN(At(rpi,2138,2148)),     // AwardEvent
        ReadabilityTKN(At(rpi,2149,2151)),    // is
        PunctuationTKN(At(rpi,2152,2153)),    // {
        KeywordTKN(At(rpi,2160,2164)),        // when
        ReadabilityTKN(At(rpi,2165,2167)),    // is
        PredefinedTKN(At(rpi,2168,2177)),     // TimeStamp
        PunctuationTKN(At(rpi,2177,2178)),    // ,
        IdentifierTKN(At(rpi,2185,2188)),     // who
        ReadabilityTKN(At(rpi,2189,2191)),    // is
        IdentifierTKN(At(rpi,2192,2202)),     // CustomerId
        PunctuationTKN(At(rpi,2202,2203)),    // ,
        IdentifierTKN(At(rpi,2210,2223)),     // poinstAwarded
        ReadabilityTKN(At(rpi,2224,2226)),    // is 
        PredefinedTKN(At(rpi,2227,2233)),     // Number
        PunctuationTKN(At(rpi,2233,2234)),    // ,
        IdentifierTKN(At(rpi,2241,2248)),     // toOrder
        ReadabilityTKN(At(rpi,2249,2251)),    // is
        IdentifierTKN(At(rpi,2252,2259)),     // OrderId
        PunctuationTKN(At(rpi,2264,2265)),    // }
        KeywordTKN(At(rpi,2270,2274)),        // type
        IdentifierTKN(At(rpi,2275,2286)),     // RewardEvent
        ReadabilityTKN(At(rpi,2287,2289)),    // is 
        KeywordTKN(At(rpi,2290,2293)),        // one
        ReadabilityTKN(At(rpi,2294,2296)),    // of
        PunctuationTKN(At(rpi,2297,2298)),    // {
        IdentifierTKN(At(rpi,2299,2311)),     // AccrualEvent
        IdentifierTKN(At(rpi,2312,2314)),     // or
        IdentifierTKN(At(rpi,2315,2325)),     // AwardEvent
        PunctuationTKN(At(rpi,2326,2327)),    // }
        KeywordTKN(At(rpi,2332,2338)),        // entity
        IdentifierTKN(At(rpi,2339,2353)),     // RewardsAccount
        ReadabilityTKN(At(rpi,2354,2356)),    // is
        PunctuationTKN(At(rpi,2357,2358)),    // {
        KeywordTKN(At(rpi,2365,2371)),        // record
        IdentifierTKN(At(rpi,2372,2378)),     // Fields
        ReadabilityTKN(At(rpi,2379,2381)),    // is
        PunctuationTKN(At(rpi,2382,2383)),    // {
        IdentifierTKN(At(rpi,2392,2394)),     // id
        ReadabilityTKN(At(rpi,2395,2397)),    // is
        IdentifierTKN(At(rpi,2398,2408)),     // CustomerId
        PunctuationTKN(At(rpi,2408,2409)),    // ,
        IdentifierTKN(At(rpi,2418,2424)),     // points
        ReadabilityTKN(At(rpi,2425,2427)),    // is
        PredefinedTKN(At(rpi,2428,2434)),     // Number
        PunctuationTKN(At(rpi,2434,2435)),    // ,
        IdentifierTKN(At(rpi,2444,2456)),     // rewardEvents
        ReadabilityTKN(At(rpi,2457,2459)),    // is
        KeywordTKN(At(rpi,2460,2464)),        // many
        KeywordTKN(At(rpi,2465,2473)),        // optional
        IdentifierTKN(At(rpi,2474,2485)),     // RewardEvent
        PunctuationTKN(At(rpi,2492,2493)),    // }
        KeywordTKN(At(rpi,2500,2505)),        // state
        IdentifierTKN(At(rpi,2506,2517)),     // RewardState
        ReadabilityTKN(At(rpi,2518,2520)),    // of
        IdentifierTKN(At(rpi,2521,2535)),     // RewardsAccount
        PunctuationTKN(At(rpi,2535,2536)),    // .
        IdentifierTKN(At(rpi,2536,2542)),     // Fields
        KeywordTKN(At(rpi,2549,2556)),        // handler
        IdentifierTKN(At(rpi,2557,2563)),     // Inputs
        ReadabilityTKN(At(rpi,2564,2566)),    // is 
        PunctuationTKN(At(rpi,2567,2568)),    // { 
        PunctuationTKN(At(rpi,2569,2572)),    // ???
        PunctuationTKN(At(rpi,2573,2574)),    // }
        PunctuationTKN(At(rpi,2579,2580)),    // }
        KeywordTKN(At(rpi,2586,2593)),        // adaptor 
        IdentifierTKN(At(rpi,2594,2608)),     // PaymentAdapter
        KeywordTKN(At(rpi,2609,2613)),        // from
        KeywordTKN(At(rpi,2614,2621)),        // context
        IdentifierTKN(At(rpi,2622,2633)),     // ReactiveBBQ
        PunctuationTKN(At(rpi,2633,2634)),    // .
        IdentifierTKN(At(rpi,2634,2641)),     // Payment
        ReadabilityTKN(At(rpi,2642,2644)),    // is
        PunctuationTKN(At(rpi,2645,2646)),    // {
        PunctuationTKN(At(rpi,2653,2656)),    // ???
        PunctuationTKN(At(rpi,2661,2662)),    // }
        PunctuationTKN(At(rpi,2665,2666)),    // }
        KeywordTKN(At(rpi,2670,2677)),        // context
        IdentifierTKN(At(rpi,2678,2683)),     // Order
        ReadabilityTKN(At(rpi,2684,2686)),    // is 
        PunctuationTKN(At(rpi,2687,2688)),    // {
        KeywordTKN(At(rpi,2693,2699)),        // entity
        IdentifierTKN(At(rpi,2700,2705)),     // Order
        ReadabilityTKN(At(rpi,2706,2708)),    // is
        PunctuationTKN(At(rpi,2709,2710)),    // { 
        KeywordTKN(At(rpi,2717,2723)),        // option
        ReadabilityTKN(At(rpi,2724,2726)),    // is 
        IdentifierTKN(At(rpi,2727,2736)),     // aggregate
        KeywordTKN(At(rpi,2743,2749)),
        IdentifierTKN(At(rpi,2750,2756)),
        ReadabilityTKN(At(rpi,2757,2759)),
        PunctuationTKN(At(rpi,2760,2761)),
        IdentifierTKN(At(rpi,2770,2777)),
        ReadabilityTKN(At(rpi,2778,2780)),
        IdentifierTKN(At(rpi,2781,2788)),
        PunctuationTKN(At(rpi,2788,2789)),
        IdentifierTKN(At(rpi,2798,2808)),
        ReadabilityTKN(At(rpi,2809,2811)),
        IdentifierTKN(At(rpi,2812,2822)),
        PunctuationTKN(At(rpi,2829,2830)),
        KeywordTKN(At(rpi,2837,2842)),
        IdentifierTKN(At(rpi,2843,2853)),
        ReadabilityTKN(At(rpi,2854,2856)),
        IdentifierTKN(At(rpi,2857,2862)),
        PunctuationTKN(At(rpi,2862,2863)),
        IdentifierTKN(At(rpi,2863,2869)),
        KeywordTKN(At(rpi,2876,2883)),
        IdentifierTKN(At(rpi,2884,2887)),
        ReadabilityTKN(At(rpi,2889,2891)),
        PunctuationTKN(At(rpi,2892,2893)),
        PunctuationTKN(At(rpi,2893,2894)),
        PunctuationTKN(At(rpi,2899,2900)),
        PunctuationTKN(At(rpi,2903,2904)),
        KeywordTKN(At(rpi,2908,2915)),
        IdentifierTKN(At(rpi,2916,2923)),
        ReadabilityTKN(At(rpi,2924,2926)),
        PunctuationTKN(At(rpi,2927,2928)),
        KeywordTKN(At(rpi,2933,2939)),
        IdentifierTKN(At(rpi,2940,2947)),
        ReadabilityTKN(At(rpi,2948,2950)),
        PunctuationTKN(At(rpi,2951,2952)),
        KeywordTKN(At(rpi,2959,2965)),
        ReadabilityTKN(At(rpi,2966,2968)),
        IdentifierTKN(At(rpi,2969,2978)),
        KeywordTKN(At(rpi,2985,2991)),
        IdentifierTKN(At(rpi,2992,2998)),
        ReadabilityTKN(At(rpi,2999,3001)),
        PunctuationTKN(At(rpi,3002,3003)),
        IdentifierTKN(At(rpi,3012,3019)),
        ReadabilityTKN(At(rpi,3020,3022)),
        IdentifierTKN(At(rpi,3023,3030)),
        PunctuationTKN(At(rpi,3030,3031)),
        IdentifierTKN(At(rpi,3040,3046)),
        ReadabilityTKN(At(rpi,3047,3049)),
        PredefinedTKN(At(rpi,3050,3056)),
        PunctuationTKN(At(rpi,3056,3057)),
        IdentifierTKN(At(rpi,3066,3075)),
        ReadabilityTKN(At(rpi,3076,3078)),
        PredefinedTKN(At(rpi,3079,3085)),
        PunctuationTKN(At(rpi,3092,3093)),
        KeywordTKN(At(rpi,3100,3105)),
        IdentifierTKN(At(rpi,3106,3118)),
        ReadabilityTKN(At(rpi,3119,3121)),
        IdentifierTKN(At(rpi,3122,3129)),
        PunctuationTKN(At(rpi,3129,3130)),
        IdentifierTKN(At(rpi,3130,3136)),
        KeywordTKN(At(rpi,3143,3150)),
        IdentifierTKN(At(rpi,3151,3154)),
        ReadabilityTKN(At(rpi,3155,3157)),
        PunctuationTKN(At(rpi,3158,3159)),
        PunctuationTKN(At(rpi,3160,3163)),
        PunctuationTKN(At(rpi,3164,3165)),
        PunctuationTKN(At(rpi,3170,3171)),
        PunctuationTKN(At(rpi,3174,3175)),
        KeywordTKN(At(rpi,3179,3186)),
        IdentifierTKN(At(rpi,3187,3191)),
        ReadabilityTKN(At(rpi,3192,3194)),
        PunctuationTKN(At(rpi,3195,3196)),
        KeywordTKN(At(rpi,3201,3207)),
        IdentifierTKN(At(rpi,3208,3216)),
        ReadabilityTKN(At(rpi,3217,3219)),
        PunctuationTKN(At(rpi,3220,3221)),
        KeywordTKN(At(rpi,3228,3234)),
        IdentifierTKN(At(rpi,3235,3241)),
        ReadabilityTKN(At(rpi,3242,3244)),
        PunctuationTKN(At(rpi,3245,3246)),
        IdentifierTKN(At(rpi,3247,3256)),
        PunctuationTKN(At(rpi,3256,3257)),
        PredefinedTKN(At(rpi,3258,3264)),
        PunctuationTKN(At(rpi,3265,3266)),
        KeywordTKN(At(rpi,3273,3278)),
        IdentifierTKN(At(rpi,3279,3288)),
        ReadabilityTKN(At(rpi,3289,3291)),
        IdentifierTKN(At(rpi,3292,3300)),
        PunctuationTKN(At(rpi,3300,3301)),
        IdentifierTKN(At(rpi,3301,3307)),
        KeywordTKN(At(rpi,3314,3321)),
        IdentifierTKN(At(rpi,3322,3325)),
        ReadabilityTKN(At(rpi,3326,3328)),
        PunctuationTKN(At(rpi,3329,3330)),
        PunctuationTKN(At(rpi,3330,3331)),
        PunctuationTKN(At(rpi,3336,3337)),
        KeywordTKN(At(rpi,3342,3346)),
        IdentifierTKN(At(rpi,3347,3358)),
        ReadabilityTKN(At(rpi,3359,3361)),
        KeywordTKN(At(rpi,3362,3371)),
        ReadabilityTKN(At(rpi,3372,3374)),
        KeywordTKN(At(rpi,3375,3381)),
        IdentifierTKN(At(rpi,3382,3390)),
        KeywordTKN(At(rpi,3395,3401)),
        IdentifierTKN(At(rpi,3402,3406)),
        ReadabilityTKN(At(rpi,3407,3409)),
        PunctuationTKN(At(rpi,3410,3411)),
        KeywordTKN(At(rpi,3418,3424)),
        ReadabilityTKN(At(rpi,3425,3427)),
        IdentifierTKN(At(rpi,3428,3437)),
        KeywordTKN(At(rpi,3444,3450)),
        IdentifierTKN(At(rpi,3451,3457)),
        ReadabilityTKN(At(rpi,3458,3460)),
        PunctuationTKN(At(rpi,3461,3462)),
        KeywordTKN(At(rpi,3463,3468)),
        PunctuationTKN(At(rpi,3468,3469)),
        KeywordTKN(At(rpi,3470,3474)),
        IdentifierTKN(At(rpi,3475,3486)),
        PunctuationTKN(At(rpi,3487,3488)),
        KeywordTKN(At(rpi,3495,3500)),
        IdentifierTKN(At(rpi,3501,3508)),
        ReadabilityTKN(At(rpi,3509,3511)),
        IdentifierTKN(At(rpi,3512,3516)),
        PunctuationTKN(At(rpi,3516,3517)),
        IdentifierTKN(At(rpi,3517,3523)),
        KeywordTKN(At(rpi,3530,3537)),
        IdentifierTKN(At(rpi,3538,3541)),
        ReadabilityTKN(At(rpi,3542,3544)),
        PunctuationTKN(At(rpi,3545,3546)),
        PunctuationTKN(At(rpi,3547,3550)),
        PunctuationTKN(At(rpi,3551,3552)),
        PunctuationTKN(At(rpi,3557,3558)),
        PunctuationTKN(At(rpi,3561,3562)),
        KeywordTKN(At(rpi,3566,3573)),
        IdentifierTKN(At(rpi,3574,3585)),
        ReadabilityTKN(At(rpi,3586,3588)),
        PunctuationTKN(At(rpi,3589,3590)),
        KeywordTKN(At(rpi,3595,3599)),
        IdentifierTKN(At(rpi,3600,3616)),
        ReadabilityTKN(At(rpi,3617,3619)),
        PunctuationTKN(At(rpi,3620,3621)),
        IdentifierTKN(At(rpi,3628,3637)),
        ReadabilityTKN(At(rpi,3638,3640)),
        PredefinedTKN(At(rpi,3641,3647)),
        PunctuationTKN(At(rpi,3647,3648)),
        IdentifierTKN(At(rpi,3655,3666)),
        ReadabilityTKN(At(rpi,3667,3669)),
        PredefinedTKN(At(rpi,3670,3676)),
        PunctuationTKN(At(rpi,3676,3677)),
        IdentifierTKN(At(rpi,3684,3692)),
        ReadabilityTKN(At(rpi,3693,3695)),
        PredefinedTKN(At(rpi,3696,3698)),
        PunctuationTKN(At(rpi,3698,3699)),
        PredefinedTKN(At(rpi,3699,3707)),
        PunctuationTKN(At(rpi,3707,3708)),
        PunctuationTKN(At(rpi,3708,3709)),
        IdentifierTKN(At(rpi,3716,3720)),
        ReadabilityTKN(At(rpi,3721,3723)),
        PredefinedTKN(At(rpi,3724,3728)),
        PunctuationTKN(At(rpi,3728,3729)),
        IdentifierTKN(At(rpi,3736,3740)),
        ReadabilityTKN(At(rpi,3741,3743)),
        PredefinedTKN(At(rpi,3744,3748)),
        PunctuationTKN(At(rpi,3753,3754)),
        KeywordTKN(At(rpi,3759,3765)),
        PredefinedTKN(At(rpi,3766,3774)),
        ReadabilityTKN(At(rpi,3775,3777)),
        PunctuationTKN(At(rpi,3778,3779)),
        KeywordTKN(At(rpi,3786,3792)),
        IdentifierTKN(At(rpi,3793,3799)),
        ReadabilityTKN(At(rpi,3800,3802)),
        PunctuationTKN(At(rpi,3803,3804)),
        KeywordTKN(At(rpi,3805,3809)),
        PunctuationTKN(At(rpi,3809,3810)),
        PredefinedTKN(At(rpi,3811,3817)),
        PunctuationTKN(At(rpi,3818,3819)),
        KeywordTKN(At(rpi,3826,3831)),
        IdentifierTKN(At(rpi,3832,3839)),
        ReadabilityTKN(At(rpi,3840,3842)),
        PredefinedTKN(At(rpi,3843,3851)),
        PunctuationTKN(At(rpi,3851,3852)),
        IdentifierTKN(At(rpi,3852,3858)),
        KeywordTKN(At(rpi,3865,3872)),
        IdentifierTKN(At(rpi,3873,3876)),
        ReadabilityTKN(At(rpi,3877,3879)),
        PunctuationTKN(At(rpi,3880,3881)),
        PunctuationTKN(At(rpi,3881,3882)),
        PunctuationTKN(At(rpi,3887,3888)),
        KeywordTKN(At(rpi,3889,3893)),
        PunctuationTKN(At(rpi,3894,3895)),
        KeywordTKN(At(rpi,3896,3905)),
        ReadabilityTKN(At(rpi,3906,3908)),
        QuotedStringTKN(At(rpi,3909,3942)),
        PunctuationTKN(At(rpi,3943,3944)),
        KeywordTKN(At(rpi,3950,3956)),
        IdentifierTKN(At(rpi,3957,3968)),
        ReadabilityTKN(At(rpi,3969,3971)),
        PunctuationTKN(At(rpi,3972,3973)),
        KeywordTKN(At(rpi,3979,3985)),
        IdentifierTKN(At(rpi,3986,3995)),
        KeywordTKN(At(rpi,4001,4007)),
        IdentifierTKN(At(rpi,4008,4014)),
        ReadabilityTKN(At(rpi,4015,4017)),
        PunctuationTKN(At(rpi,4018,4019)),
        IdentifierTKN(At(rpi,4020,4025)),
        PunctuationTKN(At(rpi,4025,4026)),
        IdentifierTKN(At(rpi,4027,4043)),
        PunctuationTKN(At(rpi,4044,4045)),
        KeywordTKN(At(rpi,4051,4056)),
        IdentifierTKN(At(rpi,4057,4068)),
        ReadabilityTKN(At(rpi,4069,4071)),
        IdentifierTKN(At(rpi,4072,4083)),
        PunctuationTKN(At(rpi,4083,4084)),
        IdentifierTKN(At(rpi,4084,4090)),
        KeywordTKN(At(rpi,4096,4103)),
        IdentifierTKN(At(rpi,4104,4112)),
        ReadabilityTKN(At(rpi,4113,4115)),
        PunctuationTKN(At(rpi,4116,4117)),
        PunctuationTKN(At(rpi,4117,4118)),
        PunctuationTKN(At(rpi,4123,4124)),
        PunctuationTKN(At(rpi,4127,4128)),
        PunctuationTKN(At(rpi,4129,4130)),
        KeywordTKN(At(rpi,4131,4135)),
        PunctuationTKN(At(rpi,4136,4137)),
        KeywordTKN(At(rpi,4140,4149)),      // explained
        ReadabilityTKN(At(rpi,4150,4152)),  // as
        PunctuationTKN(At(rpi,4153,4154)),  // {
        MarkdownLineTKN(At(rpi,4159,4167)),
        MarkdownLineTKN(At(rpi,4172,4204)),
        MarkdownLineTKN(At(rpi,4209,4218)),
        MarkdownLineTKN(At(rpi,4223,4299)),
        MarkdownLineTKN(At(rpi,4304,4378)),
        MarkdownLineTKN(At(rpi,4383,4459)),
        MarkdownLineTKN(At(rpi,4464,4541)),
        MarkdownLineTKN(At(rpi,4546,4622)),
        PunctuationTKN(At(rpi,4625,4626)),  // }
        PunctuationTKN(At(rpi,4627,4628)), // }
        CommentTKN(At(rpi,4629,4646)) // // #end-of-domain
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
