/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.{Field, *}
import org.scalatest.TestData

/** Unit Tests For TypesParserTest */
class TypeParserTest extends NoJVMParsingTest {

  "PredefTypes" should {
    //  private def predefinedTypes[u: P]: P[TypeExpression] = {
    //    P(
    //      stringType | currencyType | urlType | integerPredefTypes | realPredefTypes | timePredefTypes |
    //        decimalType | otherPredefTypes
    //    )./
    //  }
    "support all the predefined types" in { td =>
      val input = RiddlParserInput(
        """
          |type ref is reference to entity A.B.C.D
          |type str is String(10,20)
          |type AED = Currency(AED)
          |type AMD = Currency(AMD)
          |type ANG = Currency(ANG)
          |type AOA = Currency(AOA)
          |type ARS = Currency(ARS)
          |type AUD = Currency(AUD)
          |type AWG = Currency(AWG)
          |type AZN = Currency(AZN)
          |type BAM = Currency(BAM)
          |type BBD = Currency(BBD)
          |type BDT = Currency(BDT)
          |type BGN = Currency(BGN)
          |type BHD = Currency(BHD)
          |type BIF = Currency(BIF)
          |type BMD = Currency(BMD)
          |type BND = Currency(BND)
          |type BOB = Currency(BOB)
          |type BOV = Currency(BOV)
          |type BRL = Currency(BRL)
          |type BSD = Currency(BSD)
          |type BTN = Currency(BTN)
          |type BWP = Currency(BWP)
          |type BYN = Currency(BYN)
          |type BZD = Currency(BZD)
          |type CAD = Currency(CAD)
          |type CDF = Currency(CDF)
          |type CHE = Currency(CHE)
          |type CHF = Currency(CHF)
          |type CHW = Currency(CHW)
          |type CLF = Currency(CLF)
          |type CLP = Currency(CLP)
          |type CNY = Currency(CNY)
          |type COP = Currency(COP)
          |type COU = Currency(COU)
          |type CRC = Currency(CRC)
          |type CUC = Currency(CUC)
          |type CUP = Currency(CUP)
          |type CVE = Currency(CVE)
          |type CZK = Currency(CZK)
          |type DJF = Currency(DJF)
          |type DKK = Currency(DKK)
          |type DOP = Currency(DOP)
          |type EGP = Currency(EGP)
          |type ERN = Currency(ERN)
          |type ETB = Currency(ETB)
          |type EUR = Currency(EUR)
          |type FJD = Currency(FJD)
          |type FKP = Currency(FKP)
          |type GBP = Currency(GBP)
          |type GEL = Currency(GEL)
          |type GHS = Currency(GHS)
          |type GIP = Currency(GIP)
          |type GMD = Currency(GMD)
          |type GNF = Currency(GNF)
          |type GTQ = Currency(GTQ)
          |type GYD = Currency(GYD)
          |type HKD = Currency(HKD)
          |type HNL = Currency(HNL)
          |type HRK = Currency(HRK)
          |type HTG = Currency(HTG)
          |type HUF = Currency(HUF)
          |type IDR = Currency(IDR)
          |type ILS = Currency(ILS)
          |type INR = Currency(INR)
          |type IQD = Currency(IQD)
          |type IRR = Currency(IRR)
          |type ISK = Currency(ISK)
          |type JMD = Currency(JMD)
          |type JOD = Currency(JOD)
          |type JPY = Currency(JPY)
          |type KES = Currency(KES)
          |type KGS = Currency(KGS)
          |type KHR = Currency(KHR)
          |type KMF = Currency(KMF)
          |type KPW = Currency(KPW)
          |type KRW = Currency(KRW)
          |type KWD = Currency(KWD)
          |type KYD = Currency(KYD)
          |type KZT = Currency(KZT)
          |type LAK = Currency(LAK)
          |type LBP = Currency(LBP)
          |type LKR = Currency(LKR)
          |type LRD = Currency(LRD)
          |type LSL = Currency(LSL)
          |type LYD = Currency(LYD)
          |type MAD = Currency(MAD)
          |type MDL = Currency(MDL)
          |type MGA = Currency(MGA)
          |type MKD = Currency(MKD)
          |type MMK = Currency(MMK)
          |type MNT = Currency(MNT)
          |type MOP = Currency(MOP)
          |type MRU = Currency(MRU)
          |type MUR = Currency(MUR)
          |type MVR = Currency(MVR)
          |type MWK = Currency(MWK)
          |type MXN = Currency(MXN)
          |type MXV = Currency(MXV)
          |type MYR = Currency(MYR)
          |type MZN = Currency(MZN)
          |type NAD = Currency(NAD)
          |type NGN = Currency(NGN)
          |type NIO = Currency(NIO)
          |type NOK = Currency(NOK)
          |type NPR = Currency(NPR)
          |type NZD = Currency(NZD)
          |type OMR = Currency(OMR)
          |type PEN = Currency(PEN)
          |type PGK = Currency(PGK)
          |type PHP = Currency(PHP)
          |type PKR = Currency(PKR)
          |type PLN = Currency(PLN)
          |type PYG = Currency(PYG)
          |type QAR = Currency(QAR)
          |type RON = Currency(RON)
          |type RSD = Currency(RSD)
          |type RUB = Currency(RUB)
          |type RWF = Currency(RWF)
          |type SAR = Currency(SAR)
          |type SBD = Currency(SBD)
          |type SCR = Currency(SCR)
          |type SDG = Currency(SDG)
          |type SEK = Currency(SEK)
          |type SGD = Currency(SGD)
          |type SHP = Currency(SHP)
          |type SLE = Currency(SLE)
          |type SOS = Currency(SOS)
          |type SRD = Currency(SRD)
          |type STN = Currency(STN)
          |type SVC = Currency(SVC)
          |type SYP = Currency(SYP)
          |type SZL = Currency(SZL)
          |type THB = Currency(THB)
          |type TJS = Currency(TJS)
          |type TMT = Currency(TMT)
          |type TND = Currency(TND)
          |type TOP = Currency(TOP)
          |type TRY = Currency(TRY)
          |type TTD = Currency(TTD)
          |type TWD = Currency(TWD)
          |type TZS = Currency(TZS)
          |type UAH = Currency(UAH)
          |type UGX = Currency(UGX)
          |type USD = Currency(USD)
          |type USN = Currency(USN)
          |type UYI = Currency(UYI)
          |type UYU = Currency(UYU)
          |type UZS = Currency(UZS)
          |type VED = Currency(VED)
          |type VEF = Currency(VEF)
          |type VND = Currency(VND)
          |type VUV = Currency(VUV)
          |type WST = Currency(WST)
          |type XAF = Currency(XAF)
          |type XCD = Currency(XCD)
          |type XDR = Currency(XDR)
          |type XOF = Currency(XOF)
          |type XPF = Currency(XPF)
          |type XSU = Currency(XSU)
          |type XUA = Currency(XUA)
          |type YER = Currency(YER)
          |type ZAR = Currency(ZAR)
          |type ZMW = Currency(ZMW)
          |type ZWL = Currency(ZWL)
          |type current = Current
          |type length = Length
          |type luminosity = Luminosity
          |type mass = Mass
          |type mole = Mole
          |type number = Number
          |type real = Real
          |type temp = Temperature
          |type url = URL("https://examle.com/foo")
          |type bool = Boolean
          |type int = Integer
          |type nat = Natural
          |type whole = Whole
          |type duration = Duration
          |type dateTime = DateTime
          |type date = Date
          |type timesmap = TimeStamp
          |type time = Time
          |type abstract = Abstract
          |type loc = Location
          |type nada = Nothing
          |type uuid = UUID
          |type userId = UserId
          |""".stripMargin,
        td
      )
      parseInContext[Type](input, _.types.last) match {
        case Left(messages)        => fail(messages.format)
        case Right(typ: Type, rpi) => succeed
      }
    }
  }
  "TypeParser" should {
    "allow renames of String" in { (td: TestData) =>
      val rpi = RiddlParserInput("type str = String", td)
      val expected =
        Type((1, 1, rpi), Identifier((1, 6, rpi), "str"), String_((1, 12, rpi)))
      checkDefinition[Type, Type](rpi, expected, identity)
    }
    "allow renames of Number" in { (td: TestData) =>
      val rpi = RiddlParserInput("type num = Number", td)
      val expected =
        Type((1, 1, rpi), Identifier((1, 6, rpi), "num"), Number((1, 12, rpi)))
      checkDefinition[Type, Type](rpi, expected, identity)
    }
    "allow rename of Abstract" in { (td: TestData) =>
      val input = RiddlParserInput("type abs = Abstract", td)
      val expected = Type(1 -> 1, Identifier(1 -> 6, "abs"), Abstract(1 -> 12))
      checkDefinition[Type, Type](input, expected, identity)
    }
    "allow rename of Boolean" in { (td: TestData) =>
      val input = RiddlParserInput("type boo = Boolean", td)
      val expected = Type(1 -> 1, Identifier(1 -> 6, "boo"), Bool(1 -> 12))
      checkDefinition[Type, Type](input, expected, identity)
    }
    "allow rename of Current" in { (td: TestData) =>
      val input = RiddlParserInput("type cur = Current", td)
      val expected = Type(1 -> 1, Identifier(1 -> 6, "cur"), Current(1 -> 12))
      checkDefinition[Type, Type](input, expected, identity)
    }
    "allow rename of Currency(USD)" in { (td: TestData) =>
      val input = RiddlParserInput("type cur = Currency(USD)", td)
      val expected =
        Type(1 -> 1, Identifier(1 -> 6, "cur"), Currency(1 -> 12, "USD"))
      checkDefinition[Type, Type](input, expected, identity)
    }
    "allow rename of Length" in { (td: TestData) =>
      val input = RiddlParserInput("type len = Length", td)
      val expected = Type(1 -> 1, Identifier(1 -> 6, "len"), Length(1 -> 12))
      checkDefinition[Type, Type](input, expected, identity)
    }
    "allow rename of Luminosity" in { (td: TestData) =>
      val input = RiddlParserInput("type lum = Luminosity", td)
      val expected =
        Type(1 -> 1, Identifier(1 -> 6, "lum"), Luminosity(1 -> 12))
      checkDefinition[Type, Type](input, expected, identity)
    }
    "allow rename of Mass" in { (td: TestData) =>
      val input = RiddlParserInput("type mas = Mass", td)
      val expected = Type(1 -> 1, Identifier(1 -> 6, "mas"), Mass(1 -> 12))
      checkDefinition[Type, Type](input, expected, identity)
    }
    "allow rename of Mole" in { (td: TestData) =>
      val input = RiddlParserInput("type mol = Mole", td)
      val expected = Type(1 -> 1, Identifier(1 -> 6, "mol"), Mole(1 -> 12))
      checkDefinition[Type, Type](input, expected, identity)
    }
    "allow rename of Temperature" in { (td: TestData) =>
      val input = RiddlParserInput("type tmp = Temperature", td)
      val expected =
        Type(1 -> 1, Identifier(1 -> 6, "tmp"), Temperature(1 -> 12))
      checkDefinition[Type, Type](input, expected, identity)
    }
    "allow renames of Id(path)" in { (td: TestData) =>
      val input = RiddlParserInput("type ident = Id(entity foo)", td)
      val expected = Type(
        1 -> 1,
        Identifier(1 -> 6, "ident"),
        UniqueId(
          1 -> 14,
          entityPath = PathIdentifier(1 -> 24, Seq("foo"))
        )
      )
      checkDefinition[Type, Type](input, expected, identity)
    }
    "allow renames of 8 literal types" in { (td: TestData) =>
      val cases = Map[String, Type](
        "type dat = Date" ->
          Type(1 -> 1, Identifier(1 -> 6, "dat"), Date(1 -> 12)),
        "type tim = Time" ->
          Type(1 -> 1, Identifier(1 -> 6, "tim"), Time(1 -> 12)),
        "type stamp = TimeStamp" ->
          Type(1 -> 1, Identifier(1 -> 6, "stamp"), TimeStamp(1 -> 14)),
        "type url = URL" ->
          Type(1 -> 1, Identifier(1 -> 6, "url"), URI(1 -> 12)),
        "type FirstName = URL" ->
          Type(1 -> 1, Identifier(1 -> 6, "FirstName"), URI(1 -> 18, None))
      )
      checkDefinitions[Type, Type](cases, identity)
    }
    "allow enumerators" in { (td: TestData) =>
      val input = RiddlParserInput("type enum = any of { Apple Pear Peach Persimmon }", td)
      val expected = Type(
        1 -> 1,
        Identifier(1 -> 6, "enum"),
        Enumeration(
          1 -> 13,
          Contents(
            Enumerator(1 -> 22, Identifier(1 -> 22, "Apple"), None),
            Enumerator(1 -> 28, Identifier(1 -> 28, "Pear"), None),
            Enumerator(1 -> 33, Identifier(1 -> 33, "Peach"), None),
            Enumerator(1 -> 39, Identifier(1 -> 39, "Persimmon"), None)
          )
        )
      )
      checkDefinition[Type, Type](input, expected, identity)
    }
    "allow alternation" in { (td: TestData) =>
      val input = RiddlParserInput("type alt = one of { type enum or type stamp or type url }", td)
      val expected = Type(
        1 -> 1,
        Identifier(1 -> 6, "alt"),
        Alternation(
          1 -> 12,
          Contents(
            AliasedTypeExpression(
              1 -> 21,
              "type",
              PathIdentifier(1 -> 26, Seq("enum"))
            ),
            AliasedTypeExpression(
              1 -> 34,
              "type",
              PathIdentifier(1 -> 39, Seq("stamp"))
            ),
            AliasedTypeExpression(1 -> 48, "type", PathIdentifier(1 -> 53, Seq("url")))
          )
        )
      )
      checkDefinition[Type, Type](input, expected, identity)
    }
    "allow alternation of a lone type reference" in { (td: TestData) =>
      val rpi = RiddlParserInput(
        """domain Blah is {
        |type Foo = String
        |type alt = one of { type Foo }
        |}
        |""".stripMargin,
        td
      )
      val expected = Alternation(
        (3, 12, rpi),
        Contents(
          AliasedTypeExpression(
            (3, 21, rpi),
            "type",
            PathIdentifier((3, 26, rpi), Seq("Foo"))
          )
        )
      )
      parseDomainDefinition[Type](rpi, _.types.last) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right((Type(_, _, typeExp, _), _)) => typeExp must be(expected)
      }
    }
    "allow aggregation" in { (td: TestData) =>
      val rip = RiddlParserInput(
        """type agg = {
          |  key: Number,
          |  id: Id(entity foo),
          |  time: TimeStamp
          |}
          |""".stripMargin,
        td
      )
      val expected = Type(
        (1, 1, rip),
        Identifier((1, 6, rip), "agg"),
        Aggregation(
          (1, 12, rip),
          Contents(
            Field(
              (2, 3, rip),
              Identifier((2, 3, rip), "key"),
              Number((2, 8, rip))
            ),
            Field(
              (3, 3, rip),
              Identifier((3, 3, rip), "id"),
              UniqueId(
                (3, 7, rip),
                PathIdentifier((3, 17, rip), Seq("foo"))
              )
            ),
            Field(
              (4, 3, rip),
              Identifier((4, 3, rip), "time"),
              TimeStamp((4, 9, rip))
            )
          )
        )
      )
      checkDefinition[Type, Type](rip, expected, identity)
    }
    "allow methods in aggregates" in { (td: TestData) =>
      val rip = RiddlParserInput(
        """record agg = {
          |  key: Number,
          |  calc(key: Number): Number,
          |}
          |""".stripMargin,
        td
      )
      val expected = Type(
        (1, 1, rip),
        Identifier((1, 6, rip), "agg"),
        Aggregation(
          (1, 12, rip),
          Contents(
            Field(
              (2, 3, rip),
              Identifier((2, 3, rip), "key"),
              Number((2, 8, rip))
            ),
            Method(
              (3, 3, rip),
              Identifier((3, 3, rip), "calc"),
              Number((3, 22, rip)),
              Seq(MethodArgument((3, 8, rip), "key", Number((3, 13, rip))))
            )
          )
        )
      )
    }
    "allow command, event, query, and result message aggregations" in { (td: TestData) =>
      for mk <- Seq("command", "event", "query", "result") do {
        val prefix = s"type mkt = $mk {"
        val rip = RiddlParserInput(
          prefix +
            """
            |  key: Number,
            |  id: Id(entity foo),
            |  time: TimeStamp
            |}
            |""".stripMargin,
          td
        )
        val expected = Type(
          (1, 1, rip),
          Identifier((1, 6, rip), "mkt"),
          AggregateUseCaseTypeExpression(
            (1, 12, rip),
            mk match {
              case "command" => CommandCase
              case "event"   => EventCase
              case "query"   => QueryCase
              case "result"  => ResultCase
            },
            Contents(
              Field(
                (2, 3, rip),
                Identifier((2, 3, rip), "key"),
                Number((2, 8, rip))
              ),
              Field(
                (3, 3, rip),
                Identifier((3, 3, rip), "id"),
                UniqueId(
                  (3, 7, rip),
                  PathIdentifier((3, 17, rip), Seq("foo"))
                )
              ),
              Field(
                (4, 3, rip),
                Identifier((4, 3, rip), "time"),
                TimeStamp((4, 9, rip))
              )
            )
          )
        )
        checkDefinition[Type, Type](rip, expected, identity)
      }
    }
    "allow mappings between two types" in { (td: TestData) =>
      val rip = RiddlParserInput("type m1 = mapping from String to Number", td)
      val expected = Type(
        (1, 1, rip),
        Identifier((1, 6, rip), "m1"),
        Mapping((1, 11, rip), String_((1, 24, rip)), Number((1, 34, rip)))
      )
      checkDefinition[Type, Type](rip, expected, identity)
    }
    "allow graphs of types" in { (td: TestData) =>
      val rip = RiddlParserInput("type g1 = graph of String", td)
      val expected = Type(
        (1, 1, rip),
        Identifier((1, 6, rip), "g1"),
        Graph((1, 11, rip), String_((1, 20, rip)))
      )
      checkDefinition[Type, Type](rip, expected, identity)
    }
    "allow tables of types" in { (td: TestData) =>
      val rip = RiddlParserInput("type t1 = table of String of [5,10]", td)
      val expected = Type(
        (1, 1, rip),
        Identifier((1, 6, rip), "t1"),
        Table((1, 11, rip), String_((1, 20, rip)), Seq(5L, 10L))
      )
      checkDefinition[Type, Type](rip, expected, identity)
    }
    "allow range of values" in { (td: TestData) =>
      val rip = RiddlParserInput("type r1 = range(21,  42)", td)
      val expected = Type(
        (1, 1, rip),
        Identifier((1, 6, rip), "r1"),
        RangeType((1, 11, rip), 21, 42)
      )
      checkDefinition[Type, Type](rip, expected, identity)
    }

    "allow one or more in regex style" in { (td: TestData) =>
      val rip = RiddlParserInput("type oneOrMoreB = agg+", td)
      val expected = Type(
        (1, 1, rip),
        Identifier((1, 6, rip), "oneOrMoreB"),
        OneOrMore(
          (1, 19, rip),
          AliasedTypeExpression(
            (1, 19, rip),
            "type",
            PathIdentifier((1, 19, rip), Seq("agg"))
          )
        )
      )
      checkDefinition[Type, Type](rip, expected, identity)
    }

    "allow zero or more" in { (td: TestData) =>
      val rip = RiddlParserInput("type zeroOrMore = many optional agg", td)
      val expected = Type(
        (1, 1, rip),
        Identifier((1, 6, rip), "zeroOrMore"),
        ZeroOrMore(
          (1, 33, rip),
          AliasedTypeExpression(
            (1, 33, rip),
            "type",
            PathIdentifier((1, 33, rip), Seq("agg"))
          )
        )
      )
      checkDefinition[Type, Type](rip, expected, identity)
    }

    "allow optionality" in { (td: TestData) =>
      val rip = RiddlParserInput("type optional = optional agg", td)
      val expected = Type(
        (1, 1, rip),
        Identifier((1, 6, rip), "optional"),
        Optional(
          (1, 26, rip),
          AliasedTypeExpression(
            (1, 26, rip),
            "type",
            PathIdentifier((1, 26, rip), Seq("agg"))
          )
        )
      )
      checkDefinition[Type, Type](rip, expected, identity)
    }

    "allow messages defined with more natural syntax" in { (td: TestData) =>
      val rip = RiddlParserInput("command foo is { a: Integer }", td)
      val expected = Type(
        (1, 1, rip),
        Identifier((1, 9, rip), "foo"),
        AggregateUseCaseTypeExpression(
          (1, 16, rip),
          CommandCase,
          Contents(
            Field(
              (1, 18, rip),
              Identifier((1, 18, rip), "a"),
              Integer((1, 21, rip))
            )
          )
        )
      )
      checkDefinition[Type, Type](rip, expected, identity)
    }

    "allow complex nested type definitions" in { (td: TestData) =>
      val rip = RiddlParserInput(
        """
          |domain foo is {
          |  type Simple = String
          |  record Compound is {
          |    s: Simple,
          |    ns: many Number
          |  }
          |  type Choices is one of { Number or Id }
          |  type Complex is {
          |    a: Simple,
          |    b: TimeStamp,
          |    c: many optional record Compound,
          |    d: optional Choices
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseDomainDefinition[Type](rip, _.types.last) match {
        case Left(errors)          => fail(errors.format)
        case Right((typeDef, rpi)) =>
          // info(typeDef.toString)
          typeDef mustEqual Type(
            (9, 3, rpi),
            Identifier((9, 8, rpi), "Complex"),
            Aggregation(
              (9, 19, rpi),
              Contents(
                Field(
                  (10, 5, rpi),
                  Identifier((10, 5, rpi), "a"),
                  AliasedTypeExpression(
                    (10, 8, rpi),
                    "type",
                    PathIdentifier((10, 8, rpi), Seq("Simple"))
                  )
                ),
                Field(
                  (11, 5, rpi),
                  Identifier((11, 5, rpi), "b"),
                  TimeStamp((11, 8, rpi))
                ),
                Field(
                  (12, 5, rpi),
                  Identifier((12, 5, rpi), "c"),
                  ZeroOrMore(
                    (12, 22, rpi),
                    AliasedTypeExpression(
                      (12, 22, rpi),
                      "record",
                      PathIdentifier((12, 29, rpi), Seq("Compound"))
                    )
                  )
                ),
                Field(
                  (13, 5, rpi),
                  Identifier((13, 5, rpi), "d"),
                  Optional(
                    (13, 17, rpi),
                    AliasedTypeExpression(
                      (13, 17, rpi),
                      "type",
                      PathIdentifier((13, 17, rpi), Seq("Choices"))
                    )
                  )
                )
              )
            )
          )
          succeed
      }
    }
  }
}
