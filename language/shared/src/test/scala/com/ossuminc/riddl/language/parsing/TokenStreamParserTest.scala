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
        PredefinedTKN(At(rpi,2030,2039)),     // Timestampd
        PunctuationTKN(At(rpi,2039,2040)),
        IdentifierTKN(At(rpi,2047,2050)),
        ReadabilityTKN(At(rpi,2051,2053)),
        IdentifierTKN(At(rpi,2054,2064)),
        PunctuationTKN(At(rpi,2064,2065)),
        IdentifierTKN(At(rpi,2072,2085)),
        ReadabilityTKN(At(rpi,2086,2088)),
        PredefinedTKN(At(rpi,2089,2095)),
        PunctuationTKN(At(rpi,2095,2096)),
        KeywordTKN(At(rpi,2103,2107)),
        IdentifierTKN(At(rpi,2107,2112)),
        ReadabilityTKN(At(rpi,2113,2115)),
        IdentifierTKN(At(rpi,2116,2123)),
        PunctuationTKN(At(rpi,2128,2129)),
        KeywordTKN(At(rpi,2134,2138)),
        IdentifierTKN(At(rpi,2139,2149)),
        ReadabilityTKN(At(rpi,2150,2152)),
        PunctuationTKN(At(rpi,2153,2154)),
        KeywordTKN(At(rpi,2161,2165)),
        ReadabilityTKN(At(rpi,2166,2168)),
        PredefinedTKN(At(rpi,2169,2178)),
        PunctuationTKN(At(rpi,2178,2179)),
        IdentifierTKN(At(rpi,2186,2189)),
        ReadabilityTKN(At(rpi,2190,2192)),
        IdentifierTKN(At(rpi,2193,2203)),
        PunctuationTKN(At(rpi,2203,2204)),
        IdentifierTKN(At(rpi,2211,2224)),
        ReadabilityTKN(At(rpi,2225,2227)),
        PredefinedTKN(At(rpi,2228,2234)),
        PunctuationTKN(At(rpi,2234,2235)),
        ReadabilityTKN(At(rpi,2242,2244)),
        IdentifierTKN(At(rpi,2244,2249)),
        ReadabilityTKN(At(rpi,2250,2252)),
        IdentifierTKN(At(rpi,2253,2260)),
        PunctuationTKN(At(rpi,2265,2266)),
        KeywordTKN(At(rpi,2271,2275)),
        IdentifierTKN(At(rpi,2276,2287)),
        ReadabilityTKN(At(rpi,2288,2290)),
        KeywordTKN(At(rpi,2291,2294)),
        ReadabilityTKN(At(rpi,2295,2297)),
        PunctuationTKN(At(rpi,2298,2299)),
        IdentifierTKN(At(rpi,2300,2312)),
        IdentifierTKN(At(rpi,2313,2315)),
        IdentifierTKN(At(rpi,2316,2326)),
        PunctuationTKN(At(rpi,2327,2328)),
        KeywordTKN(At(rpi,2333,2339)),
        IdentifierTKN(At(rpi,2340,2354)),
        ReadabilityTKN(At(rpi,2355,2357)),
        PunctuationTKN(At(rpi,2358,2359)),
        KeywordTKN(At(rpi,2366,2372)),
        IdentifierTKN(At(rpi,2373,2379)),
        ReadabilityTKN(At(rpi,2380,2382)),
        PunctuationTKN(At(rpi,2383,2384)),
        IdentifierTKN(At(rpi,2393,2395)),
        ReadabilityTKN(At(rpi,2396,2398)),
        IdentifierTKN(At(rpi,2399,2409)),
        PunctuationTKN(At(rpi,2409,2410)),
        IdentifierTKN(At(rpi,2419,2425)),
        ReadabilityTKN(At(rpi,2426,2428)),
        PredefinedTKN(At(rpi,2429,2435)),
        PunctuationTKN(At(rpi,2435,2436)),
        IdentifierTKN(At(rpi,2445,2457)),
        ReadabilityTKN(At(rpi,2458,2460)),
        KeywordTKN(At(rpi,2461,2465)),
        KeywordTKN(At(rpi,2466,2474)),
        IdentifierTKN(At(rpi,2475,2486)),
        PunctuationTKN(At(rpi,2493,2494)),
        KeywordTKN(At(rpi,2501,2506)),
        IdentifierTKN(At(rpi,2507,2518)),
        ReadabilityTKN(At(rpi,2519,2521)),
        IdentifierTKN(At(rpi,2522,2536)),
        PunctuationTKN(At(rpi,2536,2537)),
        IdentifierTKN(At(rpi,2537,2543)),
        KeywordTKN(At(rpi,2550,2557)),
        IdentifierTKN(At(rpi,2558,2564)),
        ReadabilityTKN(At(rpi,2565,2567)),
        PunctuationTKN(At(rpi,2568,2569)),
        PunctuationTKN(At(rpi,2570,2573)),
        PunctuationTKN(At(rpi,2574,2575)),
        PunctuationTKN(At(rpi,2580,2581)),
        KeywordTKN(At(rpi,2587,2594)),
        IdentifierTKN(At(rpi,2595,2609)),
        KeywordTKN(At(rpi,2610,2614)),
        KeywordTKN(At(rpi,2615,2622)),
        IdentifierTKN(At(rpi,2623,2634)),
        PunctuationTKN(At(rpi,2634,2635)),
        IdentifierTKN(At(rpi,2635,2642)),
        ReadabilityTKN(At(rpi,2643,2645)),
        PunctuationTKN(At(rpi,2646,2647)),
        PunctuationTKN(At(rpi,2654,2657)),
        PunctuationTKN(At(rpi,2662,2663)),
        PunctuationTKN(At(rpi,2666,2667)),
        KeywordTKN(At(rpi,2671,2678)),
        IdentifierTKN(At(rpi,2679,2684)),
        ReadabilityTKN(At(rpi,2685,2687)),
        PunctuationTKN(At(rpi,2688,2689)),
        KeywordTKN(At(rpi,2694,2700)),
        IdentifierTKN(At(rpi,2701,2706)),
        ReadabilityTKN(At(rpi,2707,2709)),
        PunctuationTKN(At(rpi,2710,2711)),
        KeywordTKN(At(rpi,2718,2724)),
        ReadabilityTKN(At(rpi,2725,2727)),
        IdentifierTKN(At(rpi,2728,2737)),
        KeywordTKN(At(rpi,2744,2750)),
        IdentifierTKN(At(rpi,2751,2757)),
        ReadabilityTKN(At(rpi,2758,2760)),
        PunctuationTKN(At(rpi,2761,2762)),
        IdentifierTKN(At(rpi,2771,2778)),
        ReadabilityTKN(At(rpi,2779,2781)),
        IdentifierTKN(At(rpi,2782,2789)),
        PunctuationTKN(At(rpi,2789,2790)),
        IdentifierTKN(At(rpi,2799,2809)),
        ReadabilityTKN(At(rpi,2810,2812)),
        IdentifierTKN(At(rpi,2813,2823)),
        PunctuationTKN(At(rpi,2830,2831)),
        KeywordTKN(At(rpi,2838,2843)),
        IdentifierTKN(At(rpi,2844,2854)),
        ReadabilityTKN(At(rpi,2855,2857)),
        IdentifierTKN(At(rpi,2858,2863)),
        PunctuationTKN(At(rpi,2863,2864)),
        IdentifierTKN(At(rpi,2864,2870)),
        KeywordTKN(At(rpi,2877,2884)),
        IdentifierTKN(At(rpi,2885,2888)),
        ReadabilityTKN(At(rpi,2890,2892)),
        PunctuationTKN(At(rpi,2893,2894)),
        PunctuationTKN(At(rpi,2894,2895)),
        PunctuationTKN(At(rpi,2900,2901)),
        PunctuationTKN(At(rpi,2904,2905)),
        KeywordTKN(At(rpi,2909,2916)),
        IdentifierTKN(At(rpi,2917,2924)),
        ReadabilityTKN(At(rpi,2925,2927)),
        PunctuationTKN(At(rpi,2928,2929)),
        KeywordTKN(At(rpi,2934,2940)),
        IdentifierTKN(At(rpi,2941,2948)),
        ReadabilityTKN(At(rpi,2949,2951)),
        PunctuationTKN(At(rpi,2952,2953)),
        KeywordTKN(At(rpi,2960,2966)),
        ReadabilityTKN(At(rpi,2967,2969)),
        IdentifierTKN(At(rpi,2970,2979)),
        KeywordTKN(At(rpi,2986,2992)),
        IdentifierTKN(At(rpi,2993,2999)),
        ReadabilityTKN(At(rpi,3000,3002)),
        PunctuationTKN(At(rpi,3003,3004)),
        IdentifierTKN(At(rpi,3013,3020)),
        ReadabilityTKN(At(rpi,3021,3023)),
        IdentifierTKN(At(rpi,3024,3031)),
        PunctuationTKN(At(rpi,3031,3032)),
        IdentifierTKN(At(rpi,3041,3047)),
        ReadabilityTKN(At(rpi,3048,3050)),
        PredefinedTKN(At(rpi,3051,3057)),
        PunctuationTKN(At(rpi,3057,3058)),
        IdentifierTKN(At(rpi,3067,3076)),
        ReadabilityTKN(At(rpi,3077,3079)),
        PredefinedTKN(At(rpi,3080,3086)),
        PunctuationTKN(At(rpi,3093,3094)),
        KeywordTKN(At(rpi,3101,3106)),
        IdentifierTKN(At(rpi,3107,3119)),
        ReadabilityTKN(At(rpi,3120,3122)),
        IdentifierTKN(At(rpi,3123,3130)),
        PunctuationTKN(At(rpi,3130,3131)),
        IdentifierTKN(At(rpi,3131,3137)),
        KeywordTKN(At(rpi,3144,3151)),
        IdentifierTKN(At(rpi,3152,3155)),
        ReadabilityTKN(At(rpi,3156,3158)),
        PunctuationTKN(At(rpi,3159,3160)),
        PunctuationTKN(At(rpi,3161,3164)),
        PunctuationTKN(At(rpi,3165,3166)),
        PunctuationTKN(At(rpi,3171,3172)),
        PunctuationTKN(At(rpi,3175,3176)),
        KeywordTKN(At(rpi,3180,3187)),
        IdentifierTKN(At(rpi,3188,3192)),
        ReadabilityTKN(At(rpi,3193,3195)),
        PunctuationTKN(At(rpi,3196,3197)),
        KeywordTKN(At(rpi,3202,3208)),
        IdentifierTKN(At(rpi,3209,3217)),
        ReadabilityTKN(At(rpi,3218,3220)),
        PunctuationTKN(At(rpi,3221,3222)),
        KeywordTKN(At(rpi,3229,3235)),
        IdentifierTKN(At(rpi,3236,3242)),
        ReadabilityTKN(At(rpi,3243,3245)),
        PunctuationTKN(At(rpi,3246,3247)),
        ReadabilityTKN(At(rpi,3248,3250)),
        IdentifierTKN(At(rpi,3250,3257)),
        PunctuationTKN(At(rpi,3257,3258)),
        PredefinedTKN(At(rpi,3259,3265)),
        PunctuationTKN(At(rpi,3266,3267)),
        KeywordTKN(At(rpi,3274,3279)),
        IdentifierTKN(At(rpi,3280,3289)),
        ReadabilityTKN(At(rpi,3290,3292)),
        IdentifierTKN(At(rpi,3293,3301)),
        PunctuationTKN(At(rpi,3301,3302)),
        IdentifierTKN(At(rpi,3302,3308)),
        KeywordTKN(At(rpi,3315,3322)),
        IdentifierTKN(At(rpi,3323,3326)),
        ReadabilityTKN(At(rpi,3327,3329)),
        PunctuationTKN(At(rpi,3330,3331)),
        PunctuationTKN(At(rpi,3331,3332)),
        PunctuationTKN(At(rpi,3337,3338)),
        KeywordTKN(At(rpi,3343,3347)),
        IdentifierTKN(At(rpi,3348,3359)),
        ReadabilityTKN(At(rpi,3360,3362)),
        KeywordTKN(At(rpi,3363,3372)),
        ReadabilityTKN(At(rpi,3373,3375)),
        KeywordTKN(At(rpi,3376,3382)),
        IdentifierTKN(At(rpi,3383,3391)),
        KeywordTKN(At(rpi,3396,3402)),
        IdentifierTKN(At(rpi,3403,3407)),
        ReadabilityTKN(At(rpi,3408,3410)),
        PunctuationTKN(At(rpi,3411,3412)),
        KeywordTKN(At(rpi,3419,3425)),
        ReadabilityTKN(At(rpi,3426,3428)),
        IdentifierTKN(At(rpi,3429,3438)),
        KeywordTKN(At(rpi,3445,3451)),
        IdentifierTKN(At(rpi,3452,3458)),
        ReadabilityTKN(At(rpi,3459,3461)),
        PunctuationTKN(At(rpi,3462,3463)),
        KeywordTKN(At(rpi,3464,3469)),
        PunctuationTKN(At(rpi,3469,3470)),
        KeywordTKN(At(rpi,3471,3475)),
        IdentifierTKN(At(rpi,3476,3487)),
        PunctuationTKN(At(rpi,3488,3489)),
        KeywordTKN(At(rpi,3496,3501)),
        IdentifierTKN(At(rpi,3502,3509)),
        ReadabilityTKN(At(rpi,3510,3512)),
        IdentifierTKN(At(rpi,3513,3517)),
        PunctuationTKN(At(rpi,3517,3518)),
        IdentifierTKN(At(rpi,3518,3524)),
        KeywordTKN(At(rpi,3531,3538)),
        IdentifierTKN(At(rpi,3539,3542)),
        ReadabilityTKN(At(rpi,3543,3545)),
        PunctuationTKN(At(rpi,3546,3547)),
        PunctuationTKN(At(rpi,3548,3551)),
        PunctuationTKN(At(rpi,3552,3553)),
        PunctuationTKN(At(rpi,3558,3559)),
        PunctuationTKN(At(rpi,3562,3563)),
        KeywordTKN(At(rpi,3567,3574)),
        IdentifierTKN(At(rpi,3575,3586)),
        ReadabilityTKN(At(rpi,3587,3589)),
        PunctuationTKN(At(rpi,3590,3591)),
        KeywordTKN(At(rpi,3596,3600)),
        IdentifierTKN(At(rpi,3601,3617)),
        ReadabilityTKN(At(rpi,3618,3620)),
        PunctuationTKN(At(rpi,3621,3622)),
        IdentifierTKN(At(rpi,3629,3638)),
        ReadabilityTKN(At(rpi,3639,3641)),
        PredefinedTKN(At(rpi,3642,3648)),
        PunctuationTKN(At(rpi,3648,3649)),
        IdentifierTKN(At(rpi,3656,3667)),
        ReadabilityTKN(At(rpi,3668,3670)),
        PredefinedTKN(At(rpi,3671,3677)),
        PunctuationTKN(At(rpi,3677,3678)),
        IdentifierTKN(At(rpi,3685,3693)),
        ReadabilityTKN(At(rpi,3694,3696)),
        PredefinedTKN(At(rpi,3697,3699)),
        PunctuationTKN(At(rpi,3699,3700)),
        PredefinedTKN(At(rpi,3700,3708)),
        PunctuationTKN(At(rpi,3708,3709)),
        PunctuationTKN(At(rpi,3709,3710)),
        IdentifierTKN(At(rpi,3717,3721)),
        ReadabilityTKN(At(rpi,3722,3724)),
        PredefinedTKN(At(rpi,3725,3729)),
        PunctuationTKN(At(rpi,3729,3730)),
        IdentifierTKN(At(rpi,3737,3741)),
        ReadabilityTKN(At(rpi,3742,3744)),
        PredefinedTKN(At(rpi,3745,3749)),
        PunctuationTKN(At(rpi,3754,3755)),
        KeywordTKN(At(rpi,3760,3766)),
        PredefinedTKN(At(rpi,3767,3775)),
        ReadabilityTKN(At(rpi,3776,3778)),
        PunctuationTKN(At(rpi,3779,3780)),
        KeywordTKN(At(rpi,3787,3793)),
        IdentifierTKN(At(rpi,3794,3800)),
        ReadabilityTKN(At(rpi,3801,3803)),
        PunctuationTKN(At(rpi,3804,3805)),
        KeywordTKN(At(rpi,3806,3810)),
        PunctuationTKN(At(rpi,3810,3811)),
        PredefinedTKN(At(rpi,3812,3818)),
        PunctuationTKN(At(rpi,3819,3820)),
        KeywordTKN(At(rpi,3827,3832)),
        IdentifierTKN(At(rpi,3833,3840)),
        ReadabilityTKN(At(rpi,3841,3843)),
        PredefinedTKN(At(rpi,3844,3852)),
        PunctuationTKN(At(rpi,3852,3853)),
        IdentifierTKN(At(rpi,3853,3859)),
        KeywordTKN(At(rpi,3866,3873)),
        IdentifierTKN(At(rpi,3874,3877)),
        ReadabilityTKN(At(rpi,3878,3880)),
        PunctuationTKN(At(rpi,3881,3882)),
        PunctuationTKN(At(rpi,3882,3883)),
        PunctuationTKN(At(rpi,3888,3889)),
        KeywordTKN(At(rpi,3890,3894)),
        PunctuationTKN(At(rpi,3895,3896)),
        KeywordTKN(At(rpi,3897,3906)),
        ReadabilityTKN(At(rpi,3907,3909)),
        QuotedStringTKN(At(rpi,3910,3943)),
        PunctuationTKN(At(rpi,3944,3945)),
        KeywordTKN(At(rpi,3951,3957)),
        IdentifierTKN(At(rpi,3958,3969)),
        ReadabilityTKN(At(rpi,3970,3972)),
        PunctuationTKN(At(rpi,3973,3974)),
        KeywordTKN(At(rpi,3980,3986)),
        IdentifierTKN(At(rpi,3987,3996)),
        KeywordTKN(At(rpi,4002,4008)),
        IdentifierTKN(At(rpi,4009,4015)),
        ReadabilityTKN(At(rpi,4016,4018)),
        PunctuationTKN(At(rpi,4019,4020)),
        IdentifierTKN(At(rpi,4021,4026)),
        PunctuationTKN(At(rpi,4026,4027)),
        IdentifierTKN(At(rpi,4028,4044)),
        PunctuationTKN(At(rpi,4045,4046)),
        KeywordTKN(At(rpi,4052,4057)),
        IdentifierTKN(At(rpi,4058,4069)),
        ReadabilityTKN(At(rpi,4070,4072)),
        IdentifierTKN(At(rpi,4073,4084)),
        PunctuationTKN(At(rpi,4084,4085)),
        IdentifierTKN(At(rpi,4085,4091)),
        KeywordTKN(At(rpi,4097,4104)),
        ReadabilityTKN(At(rpi,4105,4107)),
        IdentifierTKN(At(rpi,4107,4113)),
        ReadabilityTKN(At(rpi,4114,4116)),
        PunctuationTKN(At(rpi,4117,4118)),
        PunctuationTKN(At(rpi,4118,4119)),
        PunctuationTKN(At(rpi,4124,4125)),
        PunctuationTKN(At(rpi,4128,4129)),
        PunctuationTKN(At(rpi,4130,4131)),
        KeywordTKN(At(rpi,4132,4136)),
        PunctuationTKN(At(rpi,4137,4138)),
        KeywordTKN(At(rpi,4141,4150)),
        ReadabilityTKN(At(rpi,4151,4153)),
        PunctuationTKN(At(rpi,4154,4155)),
        MarkdownLineTKN(At(rpi,4160,4168)),
        MarkdownLineTKN(At(rpi,4173,4205)),
        MarkdownLineTKN(At(rpi,4210,4219)),
        MarkdownLineTKN(At(rpi,4224,4300)),
        MarkdownLineTKN(At(rpi,4305,4379)),
        MarkdownLineTKN(At(rpi,4384,4460)),
        MarkdownLineTKN(At(rpi,4465,4542)),
        MarkdownLineTKN(At(rpi,4547,4623)),
        PunctuationTKN(At(rpi,4626,4627)),
        PunctuationTKN(At(rpi,4628,4629)),
        CommentTKN(At(rpi,4630,4647))
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
