/*
 * Copyright 2019-2026 Ossum, Inc.
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
        AST.Token.Identifier(At(rpi, 61, 70)),
        AST.Token.Punctuation(At(rpi, 70, 71)),
        AST.Token.Identifier(At(rpi, 71, 79)),
        AST.Token.Punctuation(At(rpi, 79, 80)),
        AST.Token.Keyword(At(rpi, 81, 85)),
        AST.Token.Punctuation(At(rpi, 86, 87)),
        AST.Token.Keyword(At(rpi, 92, 101)),
        AST.Token.Readability(At(rpi, 102, 104)),
        AST.Token.Punctuation(At(rpi, 105, 106)),
        AST.Token.QuotedString(At(rpi, 113, 147)),
        AST.Token.Punctuation(At(rpi, 152, 153)),
        AST.Token.Punctuation(At(rpi, 156, 157)),
        AST.Token.Keyword(At(rpi, 161, 165)),
        AST.Token.Identifier(At(rpi, 166, 173)),
        AST.Token.Readability(At(rpi, 174, 176)),
        AST.Token.Predefined(At(rpi, 177, 179)),
        AST.Token.Punctuation(At(rpi, 179, 180)),
        AST.Token.Identifier(At(rpi, 180, 191)),
        AST.Token.Punctuation(At(rpi, 191, 192)),
        AST.Token.Identifier(At(rpi, 192, 198)),
        AST.Token.Punctuation(At(rpi, 198, 199)),
        AST.Token.Identifier(At(rpi, 199, 204)),
        AST.Token.Punctuation(At(rpi, 204, 205)),
        AST.Token.Keyword(At(rpi, 206, 210)),
        AST.Token.Punctuation(At(rpi, 211, 212)),
        AST.Token.Keyword(At(rpi, 217, 226)),
        AST.Token.Readability(At(rpi, 227, 229)),
        AST.Token.Punctuation(At(rpi, 230, 231)),
        AST.Token.MarkdownLine(At(rpi, 238, 246)),
        AST.Token.MarkdownLine(At(rpi, 253, 294)),
        AST.Token.MarkdownLine(At(rpi, 301, 307)),
        AST.Token.MarkdownLine(At(rpi, 314, 374)),
        AST.Token.Punctuation(At(rpi, 379, 380)),
        AST.Token.Punctuation(At(rpi, 383, 384)),
        AST.Token.Keyword(At(rpi, 388, 392)),
        AST.Token.Identifier(At(rpi, 393, 398)),
        AST.Token.Punctuation(At(rpi, 399, 400)),
        AST.Token.Predefined(At(rpi, 401, 408)),
        AST.Token.Keyword(At(rpi, 412, 419)),
        AST.Token.Identifier(At(rpi, 420, 429)),
        AST.Token.Readability(At(rpi, 430, 432)),
        AST.Token.Punctuation(At(rpi, 433, 434)),
        AST.Token.Keyword(At(rpi, 439, 445)),
        AST.Token.Identifier(At(rpi, 446, 454)),
        AST.Token.Readability(At(rpi, 455, 457)),
        AST.Token.Punctuation(At(rpi, 458, 459)),
        AST.Token.Keyword(At(rpi, 466, 471)),
        AST.Token.Identifier(At(rpi, 472, 476)),
        AST.Token.Readability(At(rpi, 477, 479)),
        AST.Token.Identifier(At(rpi, 480, 491)),
        AST.Token.Punctuation(At(rpi, 491, 492)),
        AST.Token.Identifier(At(rpi, 492, 497)),
        AST.Token.Keyword(At(rpi, 504, 511)),
        AST.Token.Identifier(At(rpi, 512, 517)),
        AST.Token.Readability(At(rpi, 518, 520)),
        AST.Token.Punctuation(At(rpi, 521, 522)),
        AST.Token.Punctuation(At(rpi, 523, 526)),
        AST.Token.Punctuation(At(rpi, 527, 528)),
        AST.Token.Punctuation(At(rpi, 533, 534)),
        AST.Token.Punctuation(At(rpi, 537, 538)),
        AST.Token.Keyword(At(rpi, 543, 550)),
        AST.Token.Identifier(At(rpi, 551, 558)),
        AST.Token.Readability(At(rpi, 559, 561)),
        AST.Token.Punctuation(At(rpi, 562, 563)),
        AST.Token.Keyword(At(rpi, 568, 572)),
        AST.Token.Identifier(At(rpi, 573, 583)),
        AST.Token.Readability(At(rpi, 584, 586)),
        AST.Token.Punctuation(At(rpi, 587, 588)),
        AST.Token.Identifier(At(rpi, 589, 590)),
        AST.Token.Punctuation(At(rpi, 590, 591)),
        AST.Token.Predefined(At(rpi, 592, 598)),
        AST.Token.Punctuation(At(rpi, 598, 599)),
        AST.Token.Identifier(At(rpi, 600, 601)),
        AST.Token.Punctuation(At(rpi, 601, 602)),
        AST.Token.Predefined(At(rpi, 603, 609)),
        AST.Token.Punctuation(At(rpi, 609, 610)),
        AST.Token.Identifier(At(rpi, 611, 612)),
        AST.Token.Punctuation(At(rpi, 612, 613)),
        AST.Token.Predefined(At(rpi, 614, 620)),
        AST.Token.Punctuation(At(rpi, 620, 621)),
        AST.Token.Identifier(At(rpi, 622, 623)),
        AST.Token.Punctuation(At(rpi, 623, 624)),
        AST.Token.Predefined(At(rpi, 625, 631)),
        AST.Token.Punctuation(At(rpi, 631, 632)),
        AST.Token.Keyword(At(rpi, 637, 641)),
        AST.Token.Identifier(At(rpi, 642, 655)),
        AST.Token.Readability(At(rpi, 656, 658)),
        AST.Token.Punctuation(At(rpi, 659, 660)),
        AST.Token.Identifier(At(rpi, 667, 674)),
        AST.Token.Punctuation(At(rpi, 674, 675)),
        AST.Token.Keyword(At(rpi, 677, 681)),
        AST.Token.Identifier(At(rpi, 682, 692)),
        AST.Token.Punctuation(At(rpi, 697, 698)),
        AST.Token.Keyword(At(rpi, 703, 709)),
        AST.Token.Identifier(At(rpi, 710, 721)),
        AST.Token.Readability(At(rpi, 722, 724)),
        AST.Token.Punctuation(At(rpi, 725, 726)),
        AST.Token.Keyword(At(rpi, 732, 738)),
        AST.Token.Identifier(At(rpi, 739, 745)),
        AST.Token.Readability(At(rpi, 746, 748)),
        AST.Token.Punctuation(At(rpi, 749, 750)),
        AST.Token.Keyword(At(rpi, 751, 756)),
        AST.Token.Punctuation(At(rpi, 756, 757)),
        AST.Token.Keyword(At(rpi, 758, 762)),
        AST.Token.Identifier(At(rpi, 763, 776)),
        AST.Token.Punctuation(At(rpi, 777, 778)),
        AST.Token.Keyword(At(rpi, 784, 789)),
        AST.Token.Identifier(At(rpi, 790, 800)),
        AST.Token.Readability(At(rpi, 801, 803)),
        AST.Token.Identifier(At(rpi, 804, 815)),
        AST.Token.Punctuation(At(rpi, 815, 816)),
        AST.Token.Identifier(At(rpi, 816, 822)),
        AST.Token.Keyword(At(rpi, 828, 835)),
        AST.Token.Identifier(At(rpi, 836, 841)),
        AST.Token.Readability(At(rpi, 842, 844)),
        AST.Token.Punctuation(At(rpi, 845, 846)),
        AST.Token.Punctuation(At(rpi, 847, 850)),
        AST.Token.Punctuation(At(rpi, 851, 852)),
        AST.Token.Punctuation(At(rpi, 857, 858)),
        AST.Token.Keyword(At(rpi, 859, 863)),
        AST.Token.Punctuation(At(rpi, 864, 865)),
        AST.Token.Keyword(At(rpi, 871, 877)),
        AST.Token.Readability(At(rpi, 878, 880)),
        AST.Token.Identifier(At(rpi, 881, 885)),
        AST.Token.Punctuation(At(rpi, 885, 886)),
        AST.Token.QuotedString(At(rpi, 886, 894)),
        AST.Token.Punctuation(At(rpi, 894, 895)),
        AST.Token.Keyword(At(rpi, 901, 910)),
        AST.Token.Readability(At(rpi, 911, 913)),
        AST.Token.Punctuation(At(rpi, 914, 915)),
        AST.Token.MarkdownLine(At(rpi, 923, 931)),
        AST.Token.MarkdownLine(At(rpi, 939, 962)),
        AST.Token.MarkdownLine(At(rpi, 970, 980)),
        AST.Token.MarkdownLine(At(rpi, 988, 1059)),
        AST.Token.MarkdownLine(At(rpi, 1067, 1125)),
        AST.Token.MarkdownLine(At(rpi, 1133, 1139)),
        AST.Token.MarkdownLine(At(rpi, 1147, 1165)),
        AST.Token.Punctuation(At(rpi, 1171, 1172)),
        AST.Token.Punctuation(At(rpi, 1176, 1177)),
        AST.Token.Punctuation(At(rpi, 1179, 1180)),
        AST.Token.Keyword(At(rpi, 1181, 1185)),
        AST.Token.Punctuation(At(rpi, 1186, 1187)),
        AST.Token.Keyword(At(rpi, 1190, 1199)),
        AST.Token.Readability(At(rpi, 1200, 1202)),
        AST.Token.Punctuation(At(rpi, 1203, 1204)),
        AST.Token.MarkdownLine(At(rpi, 1211, 1219)),
        AST.Token.MarkdownLine(At(rpi, 1226, 1264)),
        AST.Token.MarkdownLine(At(rpi, 1271, 1281)),
        AST.Token.MarkdownLine(At(rpi, 1288, 1359)),
        AST.Token.MarkdownLine(At(rpi, 1366, 1437)),
        AST.Token.MarkdownLine(At(rpi, 1444, 1509)),
        AST.Token.MarkdownLine(At(rpi, 1516, 1587)),
        AST.Token.MarkdownLine(At(rpi, 1594, 1662)),
        AST.Token.MarkdownLine(At(rpi, 1669, 1723)),
        AST.Token.MarkdownLine(At(rpi, 1730, 1765)),
        AST.Token.MarkdownLine(At(rpi, 1772, 1799)),
        AST.Token.MarkdownLine(At(rpi, 1806, 1846)),
        AST.Token.MarkdownLine(At(rpi, 1853, 1900)),
        AST.Token.MarkdownLine(At(rpi, 1907, 1947)),
        AST.Token.MarkdownLine(At(rpi, 1954, 1957)),
        AST.Token.Punctuation(At(rpi, 1962, 1963)),
        AST.Token.Punctuation(At(rpi, 1966, 1967)),
        AST.Token.Keyword(At(rpi, 1971, 1978)),
        AST.Token.Identifier(At(rpi, 1979, 1986)),
        AST.Token.Readability(At(rpi, 1987, 1989)),
        AST.Token.Punctuation(At(rpi, 1990, 1991)),
        AST.Token.Keyword(At(rpi, 1996, 2000)),
        AST.Token.Identifier(At(rpi, 2001, 2013)),
        AST.Token.Readability(At(rpi, 2014, 2016)),
        AST.Token.Punctuation(At(rpi, 2017, 2018)),
        AST.Token.Keyword(At(rpi, 2025, 2029)),
        AST.Token.Readability(At(rpi, 2030, 2032)),
        AST.Token.Predefined(At(rpi, 2033, 2042)),
        AST.Token.Punctuation(At(rpi, 2042, 2043)),
        AST.Token.Identifier(At(rpi, 2050, 2053)),
        AST.Token.Punctuation(At(rpi, 2053, 2054)),
        AST.Token.Identifier(At(rpi, 2056, 2066)),
        AST.Token.Punctuation(At(rpi, 2066, 2067)),
        AST.Token.Identifier(At(rpi, 2074, 2087)),
        AST.Token.Readability(At(rpi, 2088, 2091)),
        AST.Token.Predefined(At(rpi, 2092, 2098)),
        AST.Token.Punctuation(At(rpi, 2098, 2099)),
        AST.Token.Identifier(At(rpi, 2106, 2115)),
        AST.Token.Punctuation(At(rpi, 2116, 2117)),
        AST.Token.Identifier(At(rpi, 2118, 2125)),
        AST.Token.Punctuation(At(rpi, 2130, 2131)),
        AST.Token.Keyword(At(rpi, 2136, 2140)),
        AST.Token.Identifier(At(rpi, 2141, 2151)),
        AST.Token.Readability(At(rpi, 2152, 2154)),
        AST.Token.Punctuation(At(rpi, 2155, 2156)),
        AST.Token.Keyword(At(rpi, 2163, 2167)),
        AST.Token.Readability(At(rpi, 2168, 2170)),
        AST.Token.Predefined(At(rpi, 2171, 2180)),
        AST.Token.Punctuation(At(rpi, 2180, 2181)),
        AST.Token.Identifier(At(rpi, 2188, 2191)),
        AST.Token.Readability(At(rpi, 2192, 2194)),
        AST.Token.Identifier(At(rpi, 2195, 2205)),
        AST.Token.Punctuation(At(rpi, 2205, 2206)),
        AST.Token.Identifier(At(rpi, 2213, 2226)),
        AST.Token.Readability(At(rpi, 2227, 2229)),
        AST.Token.Predefined(At(rpi, 2230, 2236)),
        AST.Token.Punctuation(At(rpi, 2236, 2237)),
        AST.Token.Identifier(At(rpi, 2244, 2251)),
        AST.Token.Readability(At(rpi, 2252, 2254)),
        AST.Token.Identifier(At(rpi, 2255, 2262)),
        AST.Token.Punctuation(At(rpi, 2267, 2268)),
        AST.Token.Keyword(At(rpi, 2273, 2277)),
        AST.Token.Identifier(At(rpi, 2278, 2289)),
        AST.Token.Readability(At(rpi, 2290, 2292)),
        AST.Token.Keyword(At(rpi, 2293, 2296)),
        AST.Token.Readability(At(rpi, 2297, 2299)),
        AST.Token.Punctuation(At(rpi, 2300, 2301)),
        AST.Token.Identifier(At(rpi, 2302, 2314)),
        AST.Token.Identifier(At(rpi, 2315, 2317)),
        AST.Token.Identifier(At(rpi, 2318, 2328)),
        AST.Token.Punctuation(At(rpi, 2329, 2330)),
        AST.Token.Keyword(At(rpi, 2335, 2341)),
        AST.Token.Identifier(At(rpi, 2342, 2356)),
        AST.Token.Readability(At(rpi, 2357, 2359)),
        AST.Token.Punctuation(At(rpi, 2360, 2361)),
        AST.Token.Keyword(At(rpi, 2368, 2374)),
        AST.Token.Identifier(At(rpi, 2375, 2381)),
        AST.Token.Readability(At(rpi, 2382, 2384)),
        AST.Token.Punctuation(At(rpi, 2385, 2386)),
        AST.Token.Identifier(At(rpi, 2395, 2397)),
        AST.Token.Readability(At(rpi, 2398, 2400)),
        AST.Token.Identifier(At(rpi, 2401, 2411)),
        AST.Token.Punctuation(At(rpi, 2411, 2412)),
        AST.Token.Identifier(At(rpi, 2421, 2427)),
        AST.Token.Readability(At(rpi, 2428, 2430)),
        AST.Token.Predefined(At(rpi, 2431, 2437)),
        AST.Token.Punctuation(At(rpi, 2437, 2438)),
        AST.Token.Identifier(At(rpi, 2447, 2459)),
        AST.Token.Readability(At(rpi, 2460, 2462)),
        AST.Token.Keyword(At(rpi, 2463, 2467)),
        AST.Token.Keyword(At(rpi, 2468, 2476)),
        AST.Token.Identifier(At(rpi, 2477, 2488)),
        AST.Token.Punctuation(At(rpi, 2495, 2496)),
        AST.Token.Keyword(At(rpi, 2503, 2508)),
        AST.Token.Identifier(At(rpi, 2509, 2520)),
        AST.Token.Readability(At(rpi, 2521, 2523)),
        AST.Token.Identifier(At(rpi, 2524, 2538)),
        AST.Token.Punctuation(At(rpi, 2538, 2539)),
        AST.Token.Identifier(At(rpi, 2539, 2545)),
        AST.Token.Keyword(At(rpi, 2552, 2559)),
        AST.Token.Identifier(At(rpi, 2560, 2566)),
        AST.Token.Readability(At(rpi, 2567, 2569)),
        AST.Token.Punctuation(At(rpi, 2570, 2571)),
        AST.Token.Punctuation(At(rpi, 2572, 2575)),
        AST.Token.Punctuation(At(rpi, 2576, 2577)),
        AST.Token.Punctuation(At(rpi, 2582, 2583)),
        AST.Token.Keyword(At(rpi, 2589, 2596)),
        AST.Token.Identifier(At(rpi, 2597, 2611)),
        AST.Token.Keyword(At(rpi, 2612, 2616)),
        AST.Token.Keyword(At(rpi, 2617, 2624)),
        AST.Token.Identifier(At(rpi, 2625, 2636)),
        AST.Token.Punctuation(At(rpi, 2636, 2637)),
        AST.Token.Identifier(At(rpi, 2637, 2645)),
        AST.Token.Readability(At(rpi, 2646, 2648)),
        AST.Token.Punctuation(At(rpi, 2649, 2650)),
        AST.Token.Punctuation(At(rpi, 2657, 2660)),
        AST.Token.Punctuation(At(rpi, 2665, 2666)),
        AST.Token.Punctuation(At(rpi, 2669, 2670)),
        AST.Token.Keyword(At(rpi, 2674, 2681)),
        AST.Token.Identifier(At(rpi, 2682, 2688)),
        AST.Token.Readability(At(rpi, 2689, 2691)),
        AST.Token.Punctuation(At(rpi, 2692, 2693)),
        AST.Token.Keyword(At(rpi, 2698, 2704)),
        AST.Token.Identifier(At(rpi, 2705, 2710)),
        AST.Token.Readability(At(rpi, 2711, 2713)),
        AST.Token.Punctuation(At(rpi, 2714, 2715)),
        AST.Token.Keyword(At(rpi, 2722, 2728)),
        AST.Token.Identifier(At(rpi, 2729, 2735)),
        AST.Token.Readability(At(rpi, 2736, 2738)),
        AST.Token.Punctuation(At(rpi, 2739, 2740)),
        AST.Token.Identifier(At(rpi, 2749, 2756)),
        AST.Token.Readability(At(rpi, 2757, 2759)),
        AST.Token.Identifier(At(rpi, 2760, 2767)),
        AST.Token.Punctuation(At(rpi, 2767, 2768)),
        AST.Token.Identifier(At(rpi, 2777, 2787)),
        AST.Token.Readability(At(rpi, 2788, 2790)),
        AST.Token.Identifier(At(rpi, 2791, 2801)),
        AST.Token.Punctuation(At(rpi, 2808, 2809)),
        AST.Token.Keyword(At(rpi, 2816, 2821)),
        AST.Token.Identifier(At(rpi, 2822, 2832)),
        AST.Token.Readability(At(rpi, 2833, 2835)),
        AST.Token.Identifier(At(rpi, 2836, 2841)),
        AST.Token.Punctuation(At(rpi, 2841, 2842)),
        AST.Token.Identifier(At(rpi, 2842, 2848)),
        AST.Token.Keyword(At(rpi, 2855, 2862)),
        AST.Token.Identifier(At(rpi, 2863, 2866)),
        AST.Token.Readability(At(rpi, 2868, 2870)),
        AST.Token.Punctuation(At(rpi, 2871, 2872)),
        AST.Token.Punctuation(At(rpi, 2872, 2873)),
        AST.Token.Punctuation(At(rpi, 2878, 2879)),
        AST.Token.Keyword(At(rpi, 2880, 2884)),
        AST.Token.Punctuation(At(rpi, 2885, 2886)),
        AST.Token.Keyword(At(rpi, 2893, 2899)),
        AST.Token.Readability(At(rpi, 2900, 2902)),
        AST.Token.Identifier(At(rpi, 2903, 2912)),
        AST.Token.Punctuation(At(rpi, 2917, 2918)),
        AST.Token.Punctuation(At(rpi, 2921, 2922)),
        AST.Token.Keyword(At(rpi, 2926, 2933)),
        AST.Token.Identifier(At(rpi, 2934, 2942)),
        AST.Token.Readability(At(rpi, 2943, 2945)),
        AST.Token.Punctuation(At(rpi, 2946, 2947)),
        AST.Token.Keyword(At(rpi, 2952, 2958)),
        AST.Token.Identifier(At(rpi, 2959, 2966)),
        AST.Token.Readability(At(rpi, 2967, 2969)),
        AST.Token.Punctuation(At(rpi, 2970, 2971)),
        AST.Token.Keyword(At(rpi, 2978, 2984)),
        AST.Token.Identifier(At(rpi, 2985, 2991)),
        AST.Token.Readability(At(rpi, 2992, 2994)),
        AST.Token.Punctuation(At(rpi, 2995, 2996)),
        AST.Token.Identifier(At(rpi, 3005, 3012)),
        AST.Token.Readability(At(rpi, 3013, 3015)),
        AST.Token.Identifier(At(rpi, 3016, 3023)),
        AST.Token.Punctuation(At(rpi, 3023, 3024)),
        AST.Token.Identifier(At(rpi, 3033, 3039)),
        AST.Token.Readability(At(rpi, 3040, 3042)),
        AST.Token.Predefined(At(rpi, 3043, 3049)),
        AST.Token.Punctuation(At(rpi, 3049, 3050)),
        AST.Token.Identifier(At(rpi, 3059, 3068)),
        AST.Token.Readability(At(rpi, 3069, 3071)),
        AST.Token.Predefined(At(rpi, 3072, 3078)),
        AST.Token.Punctuation(At(rpi, 3085, 3086)),
        AST.Token.Keyword(At(rpi, 3093, 3098)),
        AST.Token.Identifier(At(rpi, 3099, 3111)),
        AST.Token.Readability(At(rpi, 3112, 3114)),
        AST.Token.Identifier(At(rpi, 3115, 3122)),
        AST.Token.Punctuation(At(rpi, 3122, 3123)),
        AST.Token.Identifier(At(rpi, 3123, 3129)),
        AST.Token.Keyword(At(rpi, 3136, 3143)),
        AST.Token.Identifier(At(rpi, 3144, 3147)),
        AST.Token.Readability(At(rpi, 3148, 3150)),
        AST.Token.Punctuation(At(rpi, 3151, 3152)),
        AST.Token.Punctuation(At(rpi, 3153, 3156)),
        AST.Token.Punctuation(At(rpi, 3157, 3158)),
        AST.Token.Punctuation(At(rpi, 3163, 3164)),
        AST.Token.Keyword(At(rpi, 3165, 3169)),
        AST.Token.Punctuation(At(rpi, 3170, 3171)),
        AST.Token.Keyword(At(rpi, 3177, 3183)),
        AST.Token.Readability(At(rpi, 3184, 3186)),
        AST.Token.Identifier(At(rpi, 3187, 3196)),
        AST.Token.Punctuation(At(rpi, 3201, 3202)),
        AST.Token.Punctuation(At(rpi, 3205, 3206)),
        AST.Token.Keyword(At(rpi, 3210, 3217)),
        AST.Token.Identifier(At(rpi, 3218, 3223)),
        AST.Token.Readability(At(rpi, 3224, 3226)),
        AST.Token.Punctuation(At(rpi, 3227, 3228)),
        AST.Token.Keyword(At(rpi, 3233, 3239)),
        AST.Token.Identifier(At(rpi, 3240, 3248)),
        AST.Token.Readability(At(rpi, 3249, 3251)),
        AST.Token.Punctuation(At(rpi, 3252, 3253)),
        AST.Token.Keyword(At(rpi, 3260, 3266)),
        AST.Token.Identifier(At(rpi, 3267, 3273)),
        AST.Token.Readability(At(rpi, 3274, 3276)),
        AST.Token.Punctuation(At(rpi, 3277, 3278)),
        AST.Token.Identifier(At(rpi, 3279, 3288)),
        AST.Token.Punctuation(At(rpi, 3288, 3289)),
        AST.Token.Predefined(At(rpi, 3290, 3296)),
        AST.Token.Punctuation(At(rpi, 3297, 3298)),
        AST.Token.Keyword(At(rpi, 3305, 3310)),
        AST.Token.Identifier(At(rpi, 3311, 3320)),
        AST.Token.Readability(At(rpi, 3321, 3323)),
        AST.Token.Identifier(At(rpi, 3324, 3332)),
        AST.Token.Punctuation(At(rpi, 3332, 3333)),
        AST.Token.Identifier(At(rpi, 3333, 3339)),
        AST.Token.Keyword(At(rpi, 3346, 3353)),
        AST.Token.Identifier(At(rpi, 3354, 3357)),
        AST.Token.Readability(At(rpi, 3358, 3360)),
        AST.Token.Punctuation(At(rpi, 3361, 3362)),
        AST.Token.Punctuation(At(rpi, 3362, 3363)),
        AST.Token.Punctuation(At(rpi, 3368, 3369)),
        AST.Token.Keyword(At(rpi, 3374, 3378)),
        AST.Token.Identifier(At(rpi, 3379, 3390)),
        AST.Token.Readability(At(rpi, 3391, 3393)),
        AST.Token.Keyword(At(rpi, 3394, 3403)),
        AST.Token.Readability(At(rpi, 3404, 3406)),
        AST.Token.Keyword(At(rpi, 3407, 3413)),
        AST.Token.Identifier(At(rpi, 3414, 3422)),
        AST.Token.Keyword(At(rpi, 3427, 3433)),
        AST.Token.Identifier(At(rpi, 3434, 3438)),
        AST.Token.Readability(At(rpi, 3439, 3441)),
        AST.Token.Punctuation(At(rpi, 3442, 3443)),
        AST.Token.Keyword(At(rpi, 3450, 3456)),
        AST.Token.Identifier(At(rpi, 3457, 3463)),
        AST.Token.Readability(At(rpi, 3464, 3466)),
        AST.Token.Punctuation(At(rpi, 3467, 3468)),
        AST.Token.Keyword(At(rpi, 3469, 3474)),
        AST.Token.Punctuation(At(rpi, 3474, 3475)),
        AST.Token.Keyword(At(rpi, 3476, 3480)),
        AST.Token.Identifier(At(rpi, 3481, 3492)),
        AST.Token.Punctuation(At(rpi, 3493, 3494)),
        AST.Token.Keyword(At(rpi, 3501, 3506)),
        AST.Token.Identifier(At(rpi, 3507, 3514)),
        AST.Token.Readability(At(rpi, 3515, 3517)),
        AST.Token.Identifier(At(rpi, 3518, 3522)),
        AST.Token.Punctuation(At(rpi, 3522, 3523)),
        AST.Token.Identifier(At(rpi, 3523, 3529)),
        AST.Token.Keyword(At(rpi, 3536, 3543)),
        AST.Token.Identifier(At(rpi, 3544, 3547)),
        AST.Token.Readability(At(rpi, 3548, 3550)),
        AST.Token.Punctuation(At(rpi, 3551, 3552)),
        AST.Token.Punctuation(At(rpi, 3553, 3556)),
        AST.Token.Punctuation(At(rpi, 3557, 3558)),
        AST.Token.Punctuation(At(rpi, 3563, 3564)),
        AST.Token.Keyword(At(rpi, 3565, 3569)),
        AST.Token.Punctuation(At(rpi, 3570, 3571)),
        AST.Token.Keyword(At(rpi, 3578, 3584)),
        AST.Token.Readability(At(rpi, 3585, 3587)),
        AST.Token.Identifier(At(rpi, 3588, 3597)),
        AST.Token.Punctuation(At(rpi, 3602, 3603)),
        AST.Token.Punctuation(At(rpi, 3606, 3607)),
        AST.Token.Keyword(At(rpi, 3611, 3618)),
        AST.Token.Identifier(At(rpi, 3619, 3631)),
        AST.Token.Readability(At(rpi, 3632, 3634)),
        AST.Token.Punctuation(At(rpi, 3635, 3636)),
        AST.Token.Keyword(At(rpi, 3641, 3645)),
        AST.Token.Identifier(At(rpi, 3646, 3662)),
        AST.Token.Readability(At(rpi, 3663, 3665)),
        AST.Token.Punctuation(At(rpi, 3666, 3667)),
        AST.Token.Identifier(At(rpi, 3674, 3683)),
        AST.Token.Readability(At(rpi, 3684, 3686)),
        AST.Token.Predefined(At(rpi, 3687, 3693)),
        AST.Token.Punctuation(At(rpi, 3693, 3694)),
        AST.Token.Identifier(At(rpi, 3701, 3712)),
        AST.Token.Readability(At(rpi, 3713, 3715)),
        AST.Token.Predefined(At(rpi, 3716, 3722)),
        AST.Token.Punctuation(At(rpi, 3722, 3723)),
        AST.Token.Identifier(At(rpi, 3730, 3738)),
        AST.Token.Readability(At(rpi, 3739, 3741)),
        AST.Token.Predefined(At(rpi, 3742, 3744)),
        AST.Token.Punctuation(At(rpi, 3744, 3745)),
        AST.Token.Predefined(At(rpi, 3745, 3753)),
        AST.Token.Punctuation(At(rpi, 3753, 3754)),
        AST.Token.Punctuation(At(rpi, 3754, 3755)),
        AST.Token.Identifier(At(rpi, 3762, 3766)),
        AST.Token.Readability(At(rpi, 3767, 3769)),
        AST.Token.Predefined(At(rpi, 3770, 3774)),
        AST.Token.Punctuation(At(rpi, 3774, 3775)),
        AST.Token.Identifier(At(rpi, 3782, 3786)),
        AST.Token.Readability(At(rpi, 3787, 3789)),
        AST.Token.Predefined(At(rpi, 3790, 3794)),
        AST.Token.Punctuation(At(rpi, 3799, 3800)),
        AST.Token.Keyword(At(rpi, 3805, 3811)),
        AST.Token.Predefined(At(rpi, 3812, 3820)),
        AST.Token.Readability(At(rpi, 3821, 3823)),
        AST.Token.Punctuation(At(rpi, 3824, 3825)),
        AST.Token.Keyword(At(rpi, 3832, 3838)),
        AST.Token.Identifier(At(rpi, 3839, 3845)),
        AST.Token.Readability(At(rpi, 3846, 3848)),
        AST.Token.Punctuation(At(rpi, 3849, 3850)),
        AST.Token.Keyword(At(rpi, 3851, 3855)),
        AST.Token.Punctuation(At(rpi, 3855, 3856)),
        AST.Token.Predefined(At(rpi, 3857, 3863)),
        AST.Token.Punctuation(At(rpi, 3864, 3865)),
        AST.Token.Keyword(At(rpi, 3872, 3877)),
        AST.Token.Identifier(At(rpi, 3878, 3885)),
        AST.Token.Readability(At(rpi, 3886, 3888)),
        AST.Token.Predefined(At(rpi, 3889, 3897)),
        AST.Token.Punctuation(At(rpi, 3897, 3898)),
        AST.Token.Identifier(At(rpi, 3898, 3904)),
        AST.Token.Keyword(At(rpi, 3911, 3918)),
        AST.Token.Identifier(At(rpi, 3919, 3922)),
        AST.Token.Readability(At(rpi, 3923, 3925)),
        AST.Token.Punctuation(At(rpi, 3926, 3927)),
        AST.Token.Punctuation(At(rpi, 3927, 3928)),
        AST.Token.Punctuation(At(rpi, 3933, 3934)),
        AST.Token.Keyword(At(rpi, 3935, 3939)),
        AST.Token.Punctuation(At(rpi, 3940, 3941)),
        AST.Token.Keyword(At(rpi, 3942, 3951)),
        AST.Token.Readability(At(rpi, 3952, 3954)),
        AST.Token.QuotedString(At(rpi, 3955, 3988)),
        AST.Token.Punctuation(At(rpi, 3989, 3990)),
        AST.Token.Keyword(At(rpi, 3996, 4002)),
        AST.Token.Identifier(At(rpi, 4003, 4014)),
        AST.Token.Readability(At(rpi, 4015, 4017)),
        AST.Token.Punctuation(At(rpi, 4018, 4019)),
        AST.Token.Keyword(At(rpi, 4025, 4031)),
        AST.Token.Identifier(At(rpi, 4032, 4038)),
        AST.Token.Readability(At(rpi, 4039, 4041)),
        AST.Token.Punctuation(At(rpi, 4042, 4043)),
        AST.Token.Identifier(At(rpi, 4044, 4049)),
        AST.Token.Punctuation(At(rpi, 4049, 4050)),
        AST.Token.Identifier(At(rpi, 4051, 4067)),
        AST.Token.Punctuation(At(rpi, 4068, 4069)),
        AST.Token.Keyword(At(rpi, 4075, 4080)),
        AST.Token.Identifier(At(rpi, 4081, 4092)),
        AST.Token.Readability(At(rpi, 4093, 4095)),
        AST.Token.Identifier(At(rpi, 4096, 4107)),
        AST.Token.Punctuation(At(rpi, 4107, 4108)),
        AST.Token.Identifier(At(rpi, 4108, 4114)),
        AST.Token.Keyword(At(rpi, 4120, 4127)),
        AST.Token.Identifier(At(rpi, 4128, 4136)),
        AST.Token.Readability(At(rpi, 4137, 4139)),
        AST.Token.Punctuation(At(rpi, 4140, 4141)),
        AST.Token.Punctuation(At(rpi, 4141, 4142)),
        AST.Token.Punctuation(At(rpi, 4147, 4148)),
        AST.Token.Keyword(At(rpi, 4149, 4153)),
        AST.Token.Punctuation(At(rpi, 4154, 4155)),
        AST.Token.Keyword(At(rpi, 4161, 4167)),
        AST.Token.Identifier(At(rpi, 4168, 4177)),
        AST.Token.Punctuation(At(rpi, 4181, 4182)),
        AST.Token.Punctuation(At(rpi, 4185, 4186)),
        AST.Token.Punctuation(At(rpi, 4187, 4188)),
        AST.Token.Keyword(At(rpi, 4189, 4193)),
        AST.Token.Punctuation(At(rpi, 4194, 4195)),
        AST.Token.Keyword(At(rpi, 4198, 4207)),
        AST.Token.Readability(At(rpi, 4208, 4210))
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
