/*
 * Copyright 2019-2025 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.Token.{Comment, Readability}
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.language.{AST, At, AST as rpi}
import com.ossuminc.riddl.language.AST.{Token, *}
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
        AST.Token.Keyword(At(rpi, 0, 6)),
        AST.Token.Identifier(At(rpi, 7, 18)),
        AST.Token.Readability(At(rpi, 19, 21)),
        AST.Token.Punctuation(At(rpi, 22, 23)),
        AST.Token.Keyword(At(rpi, 27, 31)),
        AST.Token.Identifier(At(rpi, 32, 42)),
        AST.Token.Readability(At(rpi, 43, 45)),
        AST.Token.Predefined(At(rpi, 46, 48)),
        AST.Token.Punctuation(At(rpi, 48, 49)),
        AST.Token.Identifier(At(rpi, 49, 60)),
        AST.Token.Punctuation(At(rpi, 60, 61)),
        AST.Token.Identifier(At(rpi, 61, 69)),
        AST.Token.Punctuation(At(rpi, 69, 70)),
        AST.Token.Identifier(At(rpi, 70, 78)),
        AST.Token.Punctuation(At(rpi, 78, 79)),
        AST.Token.Keyword(At(rpi, 80, 84)),
        AST.Token.Punctuation(At(rpi, 85, 86)),
        AST.Token.Keyword(At(rpi, 91, 100)),
        AST.Token.Readability(At(rpi, 101, 103)),
        AST.Token.Punctuation(At(rpi, 104, 105)),
        AST.Token.QuotedString(At(rpi, 112, 146)),
        AST.Token.Punctuation(At(rpi, 151, 152)),
        AST.Token.Punctuation(At(rpi, 155, 156)),
        AST.Token.Keyword(At(rpi, 160, 164)),
        AST.Token.Identifier(At(rpi, 165, 172)),
        AST.Token.Readability(At(rpi, 173, 175)),
        AST.Token.Predefined(At(rpi, 176, 178)),
        AST.Token.Punctuation(At(rpi, 178, 179)),
        AST.Token.Identifier(At(rpi, 179, 190)),
        AST.Token.Punctuation(At(rpi, 190, 191)),
        AST.Token.Identifier(At(rpi, 191, 196)),
        AST.Token.Punctuation(At(rpi, 196, 197)),
        AST.Token.Identifier(At(rpi, 197, 202)),
        AST.Token.Punctuation(At(rpi, 202, 203)),
        AST.Token.Keyword(At(rpi, 204, 208)),
        AST.Token.Punctuation(At(rpi, 209, 210)),
        AST.Token.Keyword(At(rpi, 215, 224)),
        AST.Token.Readability(At(rpi, 225, 227)),
        AST.Token.Punctuation(At(rpi, 228, 229)),
        AST.Token.MarkdownLine(At(rpi, 236, 244)),
        AST.Token.MarkdownLine(At(rpi, 251, 292)),
        AST.Token.MarkdownLine(At(rpi, 299, 305)),
        AST.Token.MarkdownLine(At(rpi, 312, 372)),
        AST.Token.Punctuation(At(rpi, 377, 378)),
        AST.Token.Punctuation(At(rpi, 381, 382)),
        AST.Token.Keyword(At(rpi, 386, 390)),
        AST.Token.Identifier(At(rpi, 391, 396)),
        AST.Token.Punctuation(At(rpi, 397, 398)),
        AST.Token.Predefined(At(rpi, 399, 406)),
        AST.Token.Keyword(At(rpi, 410, 417)),
        AST.Token.Identifier(At(rpi, 418, 426)),
        AST.Token.Readability(At(rpi, 427, 429)),
        AST.Token.Punctuation(At(rpi, 430, 431)),
        AST.Token.Keyword(At(rpi, 436, 442)),
        AST.Token.Identifier(At(rpi, 443, 451)),
        AST.Token.Readability(At(rpi, 452, 454)),
        AST.Token.Punctuation(At(rpi, 455, 456)),
        AST.Token.Keyword(At(rpi, 463, 468)),
        AST.Token.Identifier(At(rpi, 469, 473)),
        AST.Token.Readability(At(rpi, 474, 476)),
        AST.Token.Identifier(At(rpi, 477, 488)),
        AST.Token.Punctuation(At(rpi, 488, 489)),
        AST.Token.Identifier(At(rpi, 489, 494)),
        AST.Token.Keyword(At(rpi, 501, 508)),
        AST.Token.Identifier(At(rpi, 509, 514)),
        AST.Token.Readability(At(rpi, 515, 517)),
        AST.Token.Punctuation(At(rpi, 518, 519)),
        AST.Token.Punctuation(At(rpi, 520, 523)),
        AST.Token.Punctuation(At(rpi, 524, 525)),
        AST.Token.Punctuation(At(rpi, 530, 531)),
        AST.Token.Punctuation(At(rpi, 534, 535)),
        AST.Token.Keyword(At(rpi, 540, 547)),
        AST.Token.Identifier(At(rpi, 548, 555)),
        AST.Token.Readability(At(rpi, 556, 558)),
        AST.Token.Punctuation(At(rpi, 559, 560)),
        AST.Token.Keyword(At(rpi, 565, 569)),
        AST.Token.Identifier(At(rpi, 570, 580)),
        AST.Token.Readability(At(rpi, 581, 583)),
        AST.Token.Punctuation(At(rpi, 584, 585)),
        AST.Token.Identifier(At(rpi, 586, 587)),
        AST.Token.Punctuation(At(rpi, 587, 588)),
        AST.Token.Predefined(At(rpi, 589, 595)),
        AST.Token.Punctuation(At(rpi, 595, 596)),
        AST.Token.Identifier(At(rpi, 597, 598)),
        AST.Token.Punctuation(At(rpi, 598, 599)),
        AST.Token.Predefined(At(rpi, 600, 606)),
        AST.Token.Punctuation(At(rpi, 606, 607)),
        AST.Token.Identifier(At(rpi, 608, 609)),
        AST.Token.Punctuation(At(rpi, 609, 610)),
        AST.Token.Predefined(At(rpi, 611, 617)),
        AST.Token.Punctuation(At(rpi, 617, 618)),
        AST.Token.Identifier(At(rpi, 619, 620)),
        AST.Token.Punctuation(At(rpi, 620, 621)),
        AST.Token.Predefined(At(rpi, 622, 628)),
        AST.Token.Punctuation(At(rpi, 628, 629)),
        AST.Token.Keyword(At(rpi, 634, 638)),
        AST.Token.Identifier(At(rpi, 639, 652)),
        AST.Token.Readability(At(rpi, 653, 655)),
        AST.Token.Punctuation(At(rpi, 656, 657)),
        AST.Token.Identifier(At(rpi, 664, 671)),
        AST.Token.Punctuation(At(rpi, 671, 672)),
        AST.Token.Keyword(At(rpi, 674, 678)),
        AST.Token.Identifier(At(rpi, 679, 689)),
        AST.Token.Punctuation(At(rpi, 694, 695)),
        AST.Token.Keyword(At(rpi, 700, 706)),
        AST.Token.Identifier(At(rpi, 707, 718)),
        AST.Token.Readability(At(rpi, 719, 721)),
        AST.Token.Punctuation(At(rpi, 722, 723)),
        AST.Token.Keyword(At(rpi, 729, 735)),
        AST.Token.Identifier(At(rpi, 736, 742)),
        AST.Token.Readability(At(rpi, 743, 745)),
        AST.Token.Punctuation(At(rpi, 746, 747)),
        AST.Token.Keyword(At(rpi, 748, 753)),
        AST.Token.Punctuation(At(rpi, 753, 754)),
        AST.Token.Keyword(At(rpi, 755, 759)),
        AST.Token.Identifier(At(rpi, 760, 773)),
        AST.Token.Punctuation(At(rpi, 774, 775)),
        AST.Token.Keyword(At(rpi, 781, 786)),
        AST.Token.Identifier(At(rpi, 787, 797)),
        AST.Token.Readability(At(rpi, 798, 800)),
        AST.Token.Identifier(At(rpi, 801, 812)),
        AST.Token.Punctuation(At(rpi, 812, 813)),
        AST.Token.Identifier(At(rpi, 813, 819)),
        AST.Token.Keyword(At(rpi, 825, 832)),
        AST.Token.Identifier(At(rpi, 833, 838)),
        AST.Token.Readability(At(rpi, 839, 841)),
        AST.Token.Punctuation(At(rpi, 842, 843)),
        AST.Token.Punctuation(At(rpi, 844, 847)),
        AST.Token.Punctuation(At(rpi, 848, 849)),
        AST.Token.Punctuation(At(rpi, 854, 855)),
        AST.Token.Keyword(At(rpi, 856, 860)),
        AST.Token.Punctuation(At(rpi, 861, 862)),
        AST.Token.Keyword(At(rpi, 868, 874)),
        AST.Token.Readability(At(rpi, 875, 877)),
        AST.Token.Identifier(At(rpi, 878, 882)),
        AST.Token.Punctuation(At(rpi, 882, 883)),
        AST.Token.QuotedString(At(rpi, 883, 891)),
        AST.Token.Punctuation(At(rpi, 891, 892)),
        AST.Token.Keyword(At(rpi, 898, 907)),
        AST.Token.Readability(At(rpi, 908, 910)),
        AST.Token.Punctuation(At(rpi, 911, 912)),
        AST.Token.MarkdownLine(At(rpi, 920, 928)),
        AST.Token.MarkdownLine(At(rpi, 936, 959)),
        AST.Token.MarkdownLine(At(rpi, 967, 977)),
        AST.Token.MarkdownLine(At(rpi, 985, 1056)),
        AST.Token.MarkdownLine(At(rpi, 1064, 1122)),
        AST.Token.MarkdownLine(At(rpi, 1130, 1136)),
        AST.Token.MarkdownLine(At(rpi, 1144, 1162)),
        AST.Token.Punctuation(At(rpi, 1168, 1169)),
        AST.Token.Punctuation(At(rpi, 1173, 1174)),
        AST.Token.Punctuation(At(rpi, 1176, 1177)),
        AST.Token.Keyword(At(rpi, 1178, 1182)),
        AST.Token.Punctuation(At(rpi, 1183, 1184)),
        AST.Token.Keyword(At(rpi, 1187, 1196)),
        AST.Token.Readability(At(rpi, 1197, 1199)),
        AST.Token.Punctuation(At(rpi, 1200, 1201)),
        AST.Token.MarkdownLine(At(rpi, 1208, 1216)),
        AST.Token.MarkdownLine(At(rpi, 1223, 1261)),
        AST.Token.MarkdownLine(At(rpi, 1268, 1278)),
        AST.Token.MarkdownLine(At(rpi, 1285, 1356)),
        AST.Token.MarkdownLine(At(rpi, 1363, 1434)),
        AST.Token.MarkdownLine(At(rpi, 1441, 1506)),
        AST.Token.MarkdownLine(At(rpi, 1513, 1584)),
        AST.Token.MarkdownLine(At(rpi, 1591, 1659)),
        AST.Token.MarkdownLine(At(rpi, 1666, 1720)),
        AST.Token.MarkdownLine(At(rpi, 1727, 1762)),
        AST.Token.MarkdownLine(At(rpi, 1769, 1796)),
        AST.Token.MarkdownLine(At(rpi, 1803, 1843)),
        AST.Token.MarkdownLine(At(rpi, 1850, 1897)),
        AST.Token.MarkdownLine(At(rpi, 1904, 1944)),
        AST.Token.MarkdownLine(At(rpi, 1951, 1954)),
        AST.Token.Punctuation(At(rpi, 1959, 1960)),
        AST.Token.Punctuation(At(rpi, 1963, 1964)),
        AST.Token.Keyword(At(rpi, 1968, 1975)),
        AST.Token.Identifier(At(rpi, 1976, 1983)),
        AST.Token.Readability(At(rpi, 1984, 1986)),
        AST.Token.Punctuation(At(rpi, 1987, 1988)),
        AST.Token.Keyword(At(rpi, 1993, 1997)),
        AST.Token.Identifier(At(rpi, 1998, 2010)),
        AST.Token.Readability(At(rpi, 2011, 2013)),
        AST.Token.Punctuation(At(rpi, 2014, 2015)),
        AST.Token.Keyword(At(rpi, 2022, 2026)),
        AST.Token.Readability(At(rpi, 2027, 2029)),
        AST.Token.Predefined(At(rpi, 2030, 2039)),
        AST.Token.Punctuation(At(rpi, 2039, 2040)),
        AST.Token.Identifier(At(rpi, 2047, 2050)),
        AST.Token.Punctuation(At(rpi, 2050, 2051)),
        AST.Token.Identifier(At(rpi, 2053, 2063)),
        AST.Token.Punctuation(At(rpi, 2063, 2064)),
        AST.Token.Identifier(At(rpi, 2071, 2084)),
        AST.Token.Readability(At(rpi, 2085, 2088)),
        AST.Token.Predefined(At(rpi, 2089, 2095)),
        AST.Token.Punctuation(At(rpi, 2095, 2096)),
        AST.Token.Identifier(At(rpi, 2103, 2112)),
        AST.Token.Punctuation(At(rpi, 2113, 2114)),
        AST.Token.Identifier(At(rpi, 2115, 2122)),
        AST.Token.Punctuation(At(rpi, 2127, 2128)),
        AST.Token.Keyword(At(rpi, 2133, 2137)),
        AST.Token.Identifier(At(rpi, 2138, 2148)),
        AST.Token.Readability(At(rpi, 2149, 2151)),
        AST.Token.Punctuation(At(rpi, 2152, 2153)),
        AST.Token.Keyword(At(rpi, 2160, 2164)),
        AST.Token.Readability(At(rpi, 2165, 2167)),
        AST.Token.Predefined(At(rpi, 2168, 2177)),
        AST.Token.Punctuation(At(rpi, 2177, 2178)),
        AST.Token.Identifier(At(rpi, 2185, 2188)),
        AST.Token.Readability(At(rpi, 2189, 2191)),
        AST.Token.Identifier(At(rpi, 2192, 2202)),
        AST.Token.Punctuation(At(rpi, 2202, 2203)),
        AST.Token.Identifier(At(rpi, 2210, 2223)),
        AST.Token.Readability(At(rpi, 2224, 2226)),
        AST.Token.Predefined(At(rpi, 2227, 2233)),
        AST.Token.Punctuation(At(rpi, 2233, 2234)),
        AST.Token.Identifier(At(rpi, 2241, 2248)),
        AST.Token.Readability(At(rpi, 2249, 2251)),
        AST.Token.Identifier(At(rpi, 2252, 2259)),
        AST.Token.Punctuation(At(rpi, 2264, 2265)),
        AST.Token.Keyword(At(rpi, 2270, 2274)),
        AST.Token.Identifier(At(rpi, 2275, 2286)),
        AST.Token.Readability(At(rpi, 2287, 2289)),
        AST.Token.Keyword(At(rpi, 2290, 2293)),
        AST.Token.Readability(At(rpi, 2294, 2296)),
        AST.Token.Punctuation(At(rpi, 2297, 2298)),
        AST.Token.Identifier(At(rpi, 2299, 2311)),
        AST.Token.Identifier(At(rpi, 2312, 2314)),
        AST.Token.Identifier(At(rpi, 2315, 2325)),
        AST.Token.Punctuation(At(rpi, 2326, 2327)),
        AST.Token.Keyword(At(rpi, 2332, 2338)),
        AST.Token.Identifier(At(rpi, 2339, 2353)),
        AST.Token.Readability(At(rpi, 2354, 2356)),
        AST.Token.Punctuation(At(rpi, 2357, 2358)),
        AST.Token.Keyword(At(rpi, 2365, 2371)),
        AST.Token.Identifier(At(rpi, 2372, 2378)),
        AST.Token.Readability(At(rpi, 2379, 2381)),
        AST.Token.Punctuation(At(rpi, 2382, 2383)),
        AST.Token.Identifier(At(rpi, 2392, 2394)),
        AST.Token.Readability(At(rpi, 2395, 2397)),
        AST.Token.Identifier(At(rpi, 2398, 2408)),
        AST.Token.Punctuation(At(rpi, 2408, 2409)),
        AST.Token.Identifier(At(rpi, 2418, 2424)),
        AST.Token.Readability(At(rpi, 2425, 2427)),
        AST.Token.Predefined(At(rpi, 2428, 2434)),
        AST.Token.Punctuation(At(rpi, 2434, 2435)),
        AST.Token.Identifier(At(rpi, 2444, 2456)),
        AST.Token.Readability(At(rpi, 2457, 2459)),
        AST.Token.Keyword(At(rpi, 2460, 2464)),
        AST.Token.Keyword(At(rpi, 2465, 2473)),
        AST.Token.Identifier(At(rpi, 2474, 2485)),
        AST.Token.Punctuation(At(rpi, 2492, 2493)),
        AST.Token.Keyword(At(rpi, 2500, 2505)),
        AST.Token.Identifier(At(rpi, 2506, 2517)),
        AST.Token.Readability(At(rpi, 2518, 2520)),
        AST.Token.Identifier(At(rpi, 2521, 2535)),
        AST.Token.Punctuation(At(rpi, 2535, 2536)),
        AST.Token.Identifier(At(rpi, 2536, 2542)),
        AST.Token.Keyword(At(rpi, 2549, 2556)),
        AST.Token.Identifier(At(rpi, 2557, 2563)),
        AST.Token.Readability(At(rpi, 2564, 2566)),
        AST.Token.Punctuation(At(rpi, 2567, 2568)),
        AST.Token.Punctuation(At(rpi, 2569, 2572)),
        AST.Token.Punctuation(At(rpi, 2573, 2574)),
        AST.Token.Punctuation(At(rpi, 2579, 2580)),
        AST.Token.Keyword(At(rpi, 2586, 2593)),
        AST.Token.Identifier(At(rpi, 2594, 2608)),
        AST.Token.Keyword(At(rpi, 2609, 2613)),
        AST.Token.Keyword(At(rpi, 2614, 2621)),
        AST.Token.Identifier(At(rpi, 2622, 2633)),
        AST.Token.Punctuation(At(rpi, 2633, 2634)),
        AST.Token.Identifier(At(rpi, 2634, 2641)),
        AST.Token.Readability(At(rpi, 2642, 2644)),
        AST.Token.Punctuation(At(rpi, 2645, 2646)),
        AST.Token.Punctuation(At(rpi, 2653, 2656)),
        AST.Token.Punctuation(At(rpi, 2661, 2662)),
        AST.Token.Punctuation(At(rpi, 2665, 2666)),
        AST.Token.Keyword(At(rpi, 2670, 2677)),
        AST.Token.Identifier(At(rpi, 2678, 2683)),
        AST.Token.Readability(At(rpi, 2684, 2686)),
        AST.Token.Punctuation(At(rpi, 2687, 2688)),
        AST.Token.Keyword(At(rpi, 2693, 2699)),
        AST.Token.Identifier(At(rpi, 2700, 2705)),
        AST.Token.Readability(At(rpi, 2706, 2708)),
        AST.Token.Punctuation(At(rpi, 2709, 2710)),
        AST.Token.Keyword(At(rpi, 2717, 2723)),
        AST.Token.Identifier(At(rpi, 2724, 2730)),
        AST.Token.Readability(At(rpi, 2731, 2733)),
        AST.Token.Punctuation(At(rpi, 2734, 2735)),
        AST.Token.Identifier(At(rpi, 2744, 2751)),
        AST.Token.Readability(At(rpi, 2752, 2754)),
        AST.Token.Identifier(At(rpi, 2755, 2762)),
        AST.Token.Punctuation(At(rpi, 2762, 2763)),
        AST.Token.Identifier(At(rpi, 2772, 2782)),
        AST.Token.Readability(At(rpi, 2783, 2785)),
        AST.Token.Identifier(At(rpi, 2786, 2796)),
        AST.Token.Punctuation(At(rpi, 2803, 2804)),
        AST.Token.Keyword(At(rpi, 2811, 2816)),
        AST.Token.Identifier(At(rpi, 2817, 2827)),
        AST.Token.Readability(At(rpi, 2828, 2830)),
        AST.Token.Identifier(At(rpi, 2831, 2836)),
        AST.Token.Punctuation(At(rpi, 2836, 2837)),
        AST.Token.Identifier(At(rpi, 2837, 2843)),
        AST.Token.Keyword(At(rpi, 2850, 2857)),
        AST.Token.Identifier(At(rpi, 2858, 2861)),
        AST.Token.Readability(At(rpi, 2863, 2865)),
        AST.Token.Punctuation(At(rpi, 2866, 2867)),
        AST.Token.Punctuation(At(rpi, 2867, 2868)),
        AST.Token.Punctuation(At(rpi, 2873, 2874)),
        AST.Token.Keyword(At(rpi, 2875, 2879)),
        AST.Token.Punctuation(At(rpi, 2880, 2881)),
        AST.Token.Keyword(At(rpi, 2888, 2894)),
        AST.Token.Readability(At(rpi, 2895, 2897)),
        AST.Token.Identifier(At(rpi, 2898, 2907)),
        AST.Token.Punctuation(At(rpi, 2912, 2913)),
        AST.Token.Punctuation(At(rpi, 2916, 2917)),
        AST.Token.Keyword(At(rpi, 2921, 2928)),
        AST.Token.Identifier(At(rpi, 2929, 2936)),
        AST.Token.Readability(At(rpi, 2937, 2939)),
        AST.Token.Punctuation(At(rpi, 2940, 2941)),
        AST.Token.Keyword(At(rpi, 2946, 2952)),
        AST.Token.Identifier(At(rpi, 2953, 2960)),
        AST.Token.Readability(At(rpi, 2961, 2963)),
        AST.Token.Punctuation(At(rpi, 2964, 2965)),
        AST.Token.Keyword(At(rpi, 2972, 2978)),
        AST.Token.Identifier(At(rpi, 2979, 2985)),
        AST.Token.Readability(At(rpi, 2986, 2988)),
        AST.Token.Punctuation(At(rpi, 2989, 2990)),
        AST.Token.Identifier(At(rpi, 2999, 3006)),
        AST.Token.Readability(At(rpi, 3007, 3009)),
        AST.Token.Identifier(At(rpi, 3010, 3017)),
        AST.Token.Punctuation(At(rpi, 3017, 3018)),
        AST.Token.Identifier(At(rpi, 3027, 3033)),
        AST.Token.Readability(At(rpi, 3034, 3036)),
        AST.Token.Predefined(At(rpi, 3037, 3043)),
        AST.Token.Punctuation(At(rpi, 3043, 3044)),
        AST.Token.Identifier(At(rpi, 3053, 3062)),
        AST.Token.Readability(At(rpi, 3063, 3065)),
        AST.Token.Predefined(At(rpi, 3066, 3072)),
        AST.Token.Punctuation(At(rpi, 3079, 3080)),
        AST.Token.Keyword(At(rpi, 3087, 3092)),
        AST.Token.Identifier(At(rpi, 3093, 3105)),
        AST.Token.Readability(At(rpi, 3106, 3108)),
        AST.Token.Identifier(At(rpi, 3109, 3116)),
        AST.Token.Punctuation(At(rpi, 3116, 3117)),
        AST.Token.Identifier(At(rpi, 3117, 3123)),
        AST.Token.Keyword(At(rpi, 3130, 3137)),
        AST.Token.Identifier(At(rpi, 3138, 3141)),
        AST.Token.Readability(At(rpi, 3142, 3144)),
        AST.Token.Punctuation(At(rpi, 3145, 3146)),
        AST.Token.Punctuation(At(rpi, 3147, 3150)),
        AST.Token.Punctuation(At(rpi, 3151, 3152)),
        AST.Token.Punctuation(At(rpi, 3157, 3158)),
        AST.Token.Keyword(At(rpi, 3159, 3163)),
        AST.Token.Punctuation(At(rpi, 3164, 3165)),
        AST.Token.Keyword(At(rpi, 3171, 3177)),
        AST.Token.Readability(At(rpi, 3178, 3180)),
        AST.Token.Identifier(At(rpi, 3181, 3190)),
        AST.Token.Punctuation(At(rpi, 3195, 3196)),
        AST.Token.Punctuation(At(rpi, 3199, 3200)),
        AST.Token.Keyword(At(rpi, 3204, 3211)),
        AST.Token.Identifier(At(rpi, 3212, 3216)),
        AST.Token.Readability(At(rpi, 3217, 3219)),
        AST.Token.Punctuation(At(rpi, 3220, 3221)),
        AST.Token.Keyword(At(rpi, 3226, 3232)),
        AST.Token.Identifier(At(rpi, 3233, 3241)),
        AST.Token.Readability(At(rpi, 3242, 3244)),
        AST.Token.Punctuation(At(rpi, 3245, 3246)),
        AST.Token.Keyword(At(rpi, 3253, 3259)),
        AST.Token.Identifier(At(rpi, 3260, 3266)),
        AST.Token.Readability(At(rpi, 3267, 3269)),
        AST.Token.Punctuation(At(rpi, 3270, 3271)),
        AST.Token.Identifier(At(rpi, 3272, 3281)),
        AST.Token.Punctuation(At(rpi, 3281, 3282)),
        AST.Token.Predefined(At(rpi, 3283, 3289)),
        AST.Token.Punctuation(At(rpi, 3290, 3291)),
        AST.Token.Keyword(At(rpi, 3298, 3303)),
        AST.Token.Identifier(At(rpi, 3304, 3313)),
        AST.Token.Readability(At(rpi, 3314, 3316)),
        AST.Token.Identifier(At(rpi, 3317, 3325)),
        AST.Token.Punctuation(At(rpi, 3325, 3326)),
        AST.Token.Identifier(At(rpi, 3326, 3332)),
        AST.Token.Keyword(At(rpi, 3339, 3346)),
        AST.Token.Identifier(At(rpi, 3347, 3350)),
        AST.Token.Readability(At(rpi, 3351, 3353)),
        AST.Token.Punctuation(At(rpi, 3354, 3355)),
        AST.Token.Punctuation(At(rpi, 3355, 3356)),
        AST.Token.Punctuation(At(rpi, 3361, 3362)),
        AST.Token.Keyword(At(rpi, 3367, 3371)),
        AST.Token.Identifier(At(rpi, 3372, 3383)),
        AST.Token.Readability(At(rpi, 3384, 3386)),
        AST.Token.Keyword(At(rpi, 3387, 3396)),
        AST.Token.Readability(At(rpi, 3397, 3399)),
        AST.Token.Keyword(At(rpi, 3400, 3406)),
        AST.Token.Identifier(At(rpi, 3407, 3415)),
        AST.Token.Keyword(At(rpi, 3420, 3426)),
        AST.Token.Identifier(At(rpi, 3427, 3431)),
        AST.Token.Readability(At(rpi, 3432, 3434)),
        AST.Token.Punctuation(At(rpi, 3435, 3436)),
        AST.Token.Keyword(At(rpi, 3443, 3449)),
        AST.Token.Identifier(At(rpi, 3450, 3456)),
        AST.Token.Readability(At(rpi, 3457, 3459)),
        AST.Token.Punctuation(At(rpi, 3460, 3461)),
        AST.Token.Keyword(At(rpi, 3462, 3467)),
        AST.Token.Punctuation(At(rpi, 3467, 3468)),
        AST.Token.Keyword(At(rpi, 3469, 3473)),
        AST.Token.Identifier(At(rpi, 3474, 3485)),
        AST.Token.Punctuation(At(rpi, 3486, 3487)),
        AST.Token.Keyword(At(rpi, 3494, 3499)),
        AST.Token.Identifier(At(rpi, 3500, 3507)),
        AST.Token.Readability(At(rpi, 3508, 3510)),
        AST.Token.Identifier(At(rpi, 3511, 3515)),
        AST.Token.Punctuation(At(rpi, 3515, 3516)),
        AST.Token.Identifier(At(rpi, 3516, 3522)),
        AST.Token.Keyword(At(rpi, 3529, 3536)),
        AST.Token.Identifier(At(rpi, 3537, 3540)),
        AST.Token.Readability(At(rpi, 3541, 3543)),
        AST.Token.Punctuation(At(rpi, 3544, 3545)),
        AST.Token.Punctuation(At(rpi, 3546, 3549)),
        AST.Token.Punctuation(At(rpi, 3550, 3551)),
        AST.Token.Punctuation(At(rpi, 3556, 3557)),
        AST.Token.Keyword(At(rpi, 3558, 3562)),
        AST.Token.Punctuation(At(rpi, 3563, 3564)),
        AST.Token.Keyword(At(rpi, 3571, 3577)),
        AST.Token.Readability(At(rpi, 3578, 3580)),
        AST.Token.Identifier(At(rpi, 3581, 3590)),
        AST.Token.Punctuation(At(rpi, 3595, 3596)),
        AST.Token.Punctuation(At(rpi, 3599, 3600)),
        AST.Token.Keyword(At(rpi, 3604, 3611)),
        AST.Token.Identifier(At(rpi, 3612, 3623)),
        AST.Token.Readability(At(rpi, 3624, 3626)),
        AST.Token.Punctuation(At(rpi, 3627, 3628)),
        AST.Token.Keyword(At(rpi, 3633, 3637)),
        AST.Token.Identifier(At(rpi, 3638, 3654)),
        AST.Token.Readability(At(rpi, 3655, 3657)),
        AST.Token.Punctuation(At(rpi, 3658, 3659)),
        AST.Token.Identifier(At(rpi, 3666, 3675)),
        AST.Token.Readability(At(rpi, 3676, 3678)),
        AST.Token.Predefined(At(rpi, 3679, 3685)),
        AST.Token.Punctuation(At(rpi, 3685, 3686)),
        AST.Token.Identifier(At(rpi, 3693, 3704)),
        AST.Token.Readability(At(rpi, 3705, 3707)),
        AST.Token.Predefined(At(rpi, 3708, 3714)),
        AST.Token.Punctuation(At(rpi, 3714, 3715)),
        AST.Token.Identifier(At(rpi, 3722, 3730)),
        AST.Token.Readability(At(rpi, 3731, 3733)),
        AST.Token.Predefined(At(rpi, 3734, 3736)),
        AST.Token.Punctuation(At(rpi, 3736, 3737)),
        AST.Token.Predefined(At(rpi, 3737, 3745)),
        AST.Token.Punctuation(At(rpi, 3745, 3746)),
        AST.Token.Punctuation(At(rpi, 3746, 3747)),
        AST.Token.Identifier(At(rpi, 3754, 3758)),
        AST.Token.Readability(At(rpi, 3759, 3761)),
        AST.Token.Predefined(At(rpi, 3762, 3766)),
        AST.Token.Punctuation(At(rpi, 3766, 3767)),
        AST.Token.Identifier(At(rpi, 3774, 3778)),
        AST.Token.Readability(At(rpi, 3779, 3781)),
        AST.Token.Predefined(At(rpi, 3782, 3786)),
        AST.Token.Punctuation(At(rpi, 3791, 3792)),
        AST.Token.Keyword(At(rpi, 3797, 3803)),
        AST.Token.Predefined(At(rpi, 3804, 3812)),
        AST.Token.Readability(At(rpi, 3813, 3815)),
        AST.Token.Punctuation(At(rpi, 3816, 3817)),
        AST.Token.Keyword(At(rpi, 3824, 3830)),
        AST.Token.Identifier(At(rpi, 3831, 3837)),
        AST.Token.Readability(At(rpi, 3838, 3840)),
        AST.Token.Punctuation(At(rpi, 3841, 3842)),
        AST.Token.Keyword(At(rpi, 3843, 3847)),
        AST.Token.Punctuation(At(rpi, 3847, 3848)),
        AST.Token.Predefined(At(rpi, 3849, 3855)),
        AST.Token.Punctuation(At(rpi, 3856, 3857)),
        AST.Token.Keyword(At(rpi, 3864, 3869)),
        AST.Token.Identifier(At(rpi, 3870, 3877)),
        AST.Token.Readability(At(rpi, 3878, 3880)),
        AST.Token.Predefined(At(rpi, 3881, 3889)),
        AST.Token.Punctuation(At(rpi, 3889, 3890)),
        AST.Token.Identifier(At(rpi, 3890, 3896)),
        AST.Token.Keyword(At(rpi, 3903, 3910)),
        AST.Token.Identifier(At(rpi, 3911, 3914)),
        AST.Token.Readability(At(rpi, 3915, 3917)),
        AST.Token.Punctuation(At(rpi, 3918, 3919)),
        AST.Token.Punctuation(At(rpi, 3919, 3920)),
        AST.Token.Punctuation(At(rpi, 3925, 3926)),
        AST.Token.Keyword(At(rpi, 3927, 3931)),
        AST.Token.Punctuation(At(rpi, 3932, 3933)),
        AST.Token.Keyword(At(rpi, 3934, 3943)),
        AST.Token.Readability(At(rpi, 3944, 3946)),
        AST.Token.QuotedString(At(rpi, 3947, 3980)),
        AST.Token.Punctuation(At(rpi, 3981, 3982)),
        AST.Token.Keyword(At(rpi, 3988, 3994)),
        AST.Token.Identifier(At(rpi, 3995, 4006)),
        AST.Token.Readability(At(rpi, 4007, 4009)),
        AST.Token.Punctuation(At(rpi, 4010, 4011)),
        AST.Token.Keyword(At(rpi, 4017, 4023)),
        AST.Token.Identifier(At(rpi, 4024, 4030)),
        AST.Token.Readability(At(rpi, 4031, 4033)),
        AST.Token.Punctuation(At(rpi, 4034, 4035)),
        AST.Token.Identifier(At(rpi, 4036, 4041)),
        AST.Token.Punctuation(At(rpi, 4041, 4042)),
        AST.Token.Identifier(At(rpi, 4043, 4059)),
        AST.Token.Punctuation(At(rpi, 4060, 4061)),
        AST.Token.Keyword(At(rpi, 4067, 4072)),
        AST.Token.Identifier(At(rpi, 4073, 4084)),
        AST.Token.Readability(At(rpi, 4085, 4087)),
        AST.Token.Identifier(At(rpi, 4088, 4099)),
        AST.Token.Punctuation(At(rpi, 4099, 4100)),
        AST.Token.Identifier(At(rpi, 4100, 4106)),
        AST.Token.Keyword(At(rpi, 4112, 4119)),
        AST.Token.Identifier(At(rpi, 4120, 4128)),
        AST.Token.Readability(At(rpi, 4129, 4131)),
        AST.Token.Punctuation(At(rpi, 4132, 4133)),
        AST.Token.Punctuation(At(rpi, 4133, 4134)),
        AST.Token.Punctuation(At(rpi, 4139, 4140)),
        AST.Token.Keyword(At(rpi, 4141, 4145)),
        AST.Token.Punctuation(At(rpi, 4146, 4147)),
        AST.Token.Keyword(At(rpi, 4153, 4159)),
        AST.Token.Identifier(At(rpi, 4160, 4169)),
        AST.Token.Punctuation(At(rpi, 4173, 4174)),
        AST.Token.Punctuation(At(rpi, 4177, 4178)),
        AST.Token.Punctuation(At(rpi, 4179, 4180)),
        AST.Token.Keyword(At(rpi, 4181, 4185)),
        AST.Token.Punctuation(At(rpi, 4186, 4187)),
        AST.Token.Keyword(At(rpi, 4190, 4199)),
        AST.Token.Readability(At(rpi, 4200, 4202))
      )
      val result = pc.withOptions(pc.options.copy(showTimes = true)) { _ =>
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

  "handle everything.riddl, a more complete example" in { (td: TestData) =>
    implicit val ec: ExecutionContext = pc.ec
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
          tokens.length must be(404)
          val tasStr = tokens.toString
          tokens.head must be(AST.Token.Keyword(At(rpi, 0, 6)))
          tasStr must include("LiteralCode")
          tasStr must not include ("Other")
      end match
    }
    Await.result(future, 30)
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
        |    state someState of Something.someData
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
        |    type ItHappened is event { aField: String }
        |    record otherThingData is { aField: String }
        |    state otherThingState of SomeOtherThing.otherThingData
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
        list.length must be(404)
        list.head mustBe ("Keyword(context)")
        list(1) mustBe ("Identifier(full)")
    end match
  }
}
