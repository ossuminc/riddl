/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.{Contents, *}
import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.At
import com.ossuminc.riddl.utils.PlatformContext
import org.scalatest.TestData

/** Unit Tests For TypesParserTest */
abstract class TypeParserTest(using PlatformContext) extends AbstractParsingTest {

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
        case Right(_: Type, _) => succeed
      }
    }
  }
  "TypeParser" should {
    "allow renames of String" in { (td: TestData) =>
      val rpi = RiddlParserInput("type str = String", td)
      val expected =
        Type(At(rpi, 0, 17), Identifier(At(rpi, 5, 9), "str"), String_(At(rpi, 11, 17)))
      checkDefinition[Type, Type](rpi, expected, identity)
    }
    "allow renames of Number" in { (td: TestData) =>
      val rpi = RiddlParserInput("type num = Number", td)
      val expected =
        Type(At(rpi, 0, 17), Identifier(At(rpi, 5, 9), "num"), Number(At(rpi, 11, 17)))
      checkDefinition[Type, Type](rpi, expected, identity)
    }
    "allow rename of Abstract" in { (td: TestData) =>
      val rpi = RiddlParserInput("type abs = Abstract", td)
      val expected = Type(At(rpi, 0, 18), Identifier(At(rpi, 5, 9), "abs"), Abstract(At(rpi, 11, 18)))
      checkDefinition[Type, Type](rpi, expected, identity)
    }
    "allow rename of Boolean" in { (td: TestData) =>
      val rpi = RiddlParserInput("type boo = Boolean", td)
      val expected = Type(At(rpi, 0, 18), Identifier(At(rpi, 5, 9), "boo"), Bool(At(rpi, 11, 18)))
      checkDefinition[Type, Type](rpi, expected, identity)
    }
    "allow rename of Current" in { (td: TestData) =>
      val rpi = RiddlParserInput("type cur = Current", td)
      val expected = Type(At(rpi, 0, 18), Identifier(At(rpi, 5, 9), "cur"), Current(At(rpi, 11, 18)))
      checkDefinition[Type, Type](rpi, expected, identity)
    }
    "allow rename of Currency(USD)" in { (td: TestData) =>
      val rpi = RiddlParserInput("type cur = Currency(USD)", td)
      val expected =
        Type(At(rpi, 0, 24), Identifier(At(rpi, 5, 9), "cur"), Currency(At(rpi, 11, 24), "USD"))
      checkDefinition[Type, Type](rpi, expected, identity)
    }
    "allow rename of Length" in { (td: TestData) =>
      val rpi = RiddlParserInput("type len = Length", td)
      val expected = Type(At(rpi, 0, 17), Identifier(At(rpi, 5, 9), "len"), Length(At(rpi, 11, 17)))
      checkDefinition[Type, Type](rpi, expected, identity)
    }
    "allow rename of Luminosity" in { (td: TestData) =>
      val rpi = RiddlParserInput("type lum = Luminosity", td)
      val expected =
        Type(At(rpi, 0, 21), Identifier(At(rpi, 5, 9), "lum"), Luminosity(At(rpi, 11, 21)))
      checkDefinition[Type, Type](rpi, expected, identity)
    }
    "allow rename of Mass" in { (td: TestData) =>
      val rpi = RiddlParserInput("type mas = Mass", td)
      val expected = Type(At(rpi, 0, 15), Identifier(At(rpi, 5, 9), "mas"), Mass(At(rpi, 11, 15)))
      checkDefinition[Type, Type](rpi, expected, identity)
    }
    "allow rename of Mole" in { (td: TestData) =>
      val rpi = RiddlParserInput("type mol = Mole", td)
      val expected = Type(At(rpi, 0, 15), Identifier(At(rpi, 5, 9), "mol"), Mole(At(rpi, 11, 15)))
      checkDefinition[Type, Type](rpi, expected, identity)
    }
    "allow rename of Temperature" in { (td: TestData) =>
      val rpi = RiddlParserInput("type tmp = Temperature", td)
      val expected = Type(At(rpi, 0, 22), Identifier(At(rpi, 5, 9), "tmp"), Temperature(At(rpi, 11, 12)))
      checkDefinition[Type, Type](rpi, expected, identity)
    }
    "allow renames of Id(path)" in { (td: TestData) =>
      val rpi = RiddlParserInput("type ident = Id(entity foo)", td)
      val expected = Type(
        At(rpi, 0, 27),
        Identifier(At(rpi, 5, 11), "ident"),
        UniqueId(
          At(rpi, 13, 27),
          entityPath = PathIdentifier(At(rpi, 23, 26), Seq("foo"))
        )
      )
      checkDefinition[Type, Type](rpi, expected, identity)
    }
    "allow renames of 8 literal types" in { (_: TestData) =>
      val mt = RiddlParserInput.empty
      val cases = Map[String, Type](
        "type dat = Date" ->
          Type(At(mt, 0, 15), Identifier(At(mt, 5, 8), "dat"), Date(At(mt, 11, 15))),
        "type tim = Time" ->
          Type(At(mt, 0, 15), Identifier(At(mt, 5, 9), "tim"), Time(At(mt, 11, 15))),
        "type stamp = TimeStamp" ->
          Type(At(mt, 0, 22), Identifier(At(mt, 5, 10), "stamp"), TimeStamp(At(mt, 13, 23))),
        "type url = URL" ->
          Type(At(mt, 0, 14), Identifier(At(mt, 5, 8), "url"), URI(At(mt, 11, 14))),
        "type FirstName = URL" ->
          Type(At(mt, 0, 20), Identifier(At(mt, 5, 15), "FirstName"), URI(At(mt, 17, 20)))
      )
      checkDefinitions[Type, Type](cases, identity)
    }
    "allow enumerators" in { (td: TestData) =>
      val rpi = RiddlParserInput("type enum = any of { Apple Pear Peach Persimmon }", td)
      val expected = Type(
        At(rpi, 0, 49),
        Identifier(At(rpi, 5, 10), "enum"),
        Enumeration(
          At(rpi, 12, 49),
          Contents(
            Enumerator(At(rpi, 21, 27), Identifier(At(rpi, 21, 27), "Apple"), None),
            Enumerator(At(rpi, 27, 32), Identifier(At(rpi, 27, 32), "Pear"), None),
            Enumerator(At(rpi, 32, 38), Identifier(At(rpi, 32, 38), "Peach"), None),
            Enumerator(At(rpi, 38, 48), Identifier(At(rpi, 38, 48), "Persimmon"), None)
          )
        )
      )
      checkDefinition[Type, Type](rpi, expected, identity)
    }
    "allow alternation" in { (td: TestData) =>
      val rpi = RiddlParserInput("type alt = one of { type enum or type stamp or type url }", td)
      val expected = Type(
        At(rpi, 0, 57),
        Identifier(At(rpi, 5, 9), "alt"),
        Alternation(
          At(rpi, 11, 57),
          Contents(
            AliasedTypeExpression(
              At(rpi, 20, 30),
              "type",
              PathIdentifier(At(rpi, 25, 30), Seq("enum"))
            ),
            AliasedTypeExpression(
              At(rpi, 33, 44),
              "type",
              PathIdentifier(At(rpi, 38, 44), Seq("stamp"))
            ),
            AliasedTypeExpression(At(rpi, 47, 56), "type", PathIdentifier(At(rpi, 52, 56), Seq("url")))
          )
        )
      )
      checkDefinition[Type, Type](rpi, expected, identity)
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
        At(rpi, 46, 66),
        Contents(
          AliasedTypeExpression(
            At(rpi, 55, 64),
            "type",
            PathIdentifier(At(rpi, 60, 64), Seq("Foo"))
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
      val rpi = RiddlParserInput(
        """type agg = {
          |  key: Number,
          |  id: Id(entity foo),
          |  time: TimeStamp
          |}
          |""".stripMargin,
        td
      )
      val expected = Type(
        At(rpi, 0, 70),
        Identifier(At(rpi, 5, 9), "agg"),
        Aggregation(
          At(rpi, 11, 70),
          Contents(
            Field(
              At(rpi, 15, 26),
              Identifier(At(rpi, 15, 18), "key"),
              Number(At(rpi, 20, 26))
            ),
            Field(
              At(rpi, 30, 48),
              Identifier(At(rpi, 30, 32), "id"),
              UniqueId(
                At(rpi, 34, 48),
                PathIdentifier(At(rpi, 44, 47), Seq("foo"))
              )
            ),
            Field(
              At(rpi, 52, 68),
              Identifier(At(rpi, 52, 56), "time"),
              TimeStamp(At(rpi, 58, 68))
            )
          )
        )
      )
      checkDefinition[Type, Type](rpi, expected, identity)
    }
    "allow methods in aggregates" in { (td: TestData) =>
      val rpi = RiddlParserInput(
        """record agg = {
          |  key: Number,
          |  calc(key: Number): Number
          |}
          |""".stripMargin,
        td
      )
      val expected = Type(
        At(rpi, 0, 60),
        Identifier(At(rpi, 7, 10), "agg"),
        AggregateUseCaseTypeExpression(
          At(rpi, 13, 60),
          AggregateUseCase.RecordCase,
          Contents(
            Field(
              At(rpi, 17, 28),
              Identifier(At(rpi, 17, 20), "key"),
              Number(At(rpi, 22, 28))
            ),
            Method(
              At(rpi, 32, 57),
              Identifier(At(rpi, 32, 36), "calc"),
              Number(At(rpi, 51, 58)),
              Seq(MethodArgument(At(rpi, 37, 48), "key", Number(At(rpi, 42, 48))))
            )
          )
        )
      )
      checkDefinition[Type, Type](rpi, expected, identity)
    }
    "allow command, event, query, and result message aggregations" in { (td: TestData) =>
      for mk <- Seq("command", "event", "query", "result") do {
        val prefix = s"type mkt = $mk {"
        val rpi = RiddlParserInput(
          prefix +
            """
            |  key: Number,
            |  id: Id(entity foo),
            |  time: TimeStamp
            |}
            |""".stripMargin,
          td
        )
        val l = mk.length
        val expected = Type(
          At(rpi, 0, 71+l),
          Identifier(At(rpi, 5, 9), "mkt"),
          AggregateUseCaseTypeExpression(
            At(rpi, 11, 71+l),
            mk match {
              case "command" => AggregateUseCase.CommandCase
              case "event"   => AggregateUseCase.EventCase
              case "query"   => AggregateUseCase.QueryCase
              case "result"  => AggregateUseCase.ResultCase
            },
            Contents(
              Field(
                At(rpi, 16+l, 27+l),
                Identifier(At(rpi, 16+l, 19+l), "key"),
                Number(At(rpi, 21+l, 27+l))
              ),
              Field(
                At(rpi, 31+l, 49+l),
                Identifier(At(rpi, 31+l, 33+l), "id"),
                UniqueId(
                  At(rpi, 35+l, 49+l),
                  PathIdentifier(At(rpi, 45+l, 49+l), Seq("foo"))
                )
              ),
              Field(
                At(rpi, 53+l, 69+l),
                Identifier(At(rpi, 53+l, 57+l), "time"),
                TimeStamp(At(rpi, 59+l, 69+l))
              )
            )
          )
        )
        checkDefinition[Type, Type](rpi, expected, identity)
      }
    }
    "allow mappings between two types" in { (td: TestData) =>
      val rpi = RiddlParserInput("type m1 = mapping from String to Number", td)
      val expected = Type(
        At(rpi, 0, 39),
        Identifier(At(rpi, 5, 8), "m1"),
        Mapping(At(rpi, 10, 39), String_(At(rpi, 23, 30)), Number(At(rpi, 33, 39)))
      )
      checkDefinition[Type, Type](rpi, expected, identity)
    }
    "allow graphs of types" in { (td: TestData) =>
      val rpi = RiddlParserInput("type g1 = graph of String", td)
      val expected = Type(
        At(rpi, 0, 25),
        Identifier(At(rpi, 5, 8), "g1"),
        Graph(At(rpi, 10, 25), String_(At(rpi, 19, 25)))
      )
      checkDefinition[Type, Type](rpi, expected, identity)
    }
    "allow tables of types" in { (td: TestData) =>
      val rpi = RiddlParserInput("type t1 = table of String of [5,10]", td)
      val expected = Type(
        At(rpi, 0, 35),
        Identifier(At(rpi, 5, 8), "t1"),
        Table(At(rpi, 10, 35), String_(At(rpi, 19, 26)), Seq(5L, 10L))
      )
      checkDefinition[Type, Type](rpi, expected, identity)
    }
    "allow range of values" in { (td: TestData) =>
      val rpi = RiddlParserInput("type r1 = range(21,  42)", td)
      val expected = Type(
        At(rpi, 0, 24),
        Identifier(At(rpi, 5, 8), "r1"),
        RangeType(At(rpi, 10, 24), 21, 42)
      )
      checkDefinition[Type, Type](rpi, expected, identity)
    }

    "allow one or more in regex style" in { (td: TestData) =>
      val rpi = RiddlParserInput("type oneOrMoreB = agg+", td)
      val expected = Type(
        At(rpi, 0, 22),
        Identifier(At(rpi, 5, 16), "oneOrMoreB"),
        OneOrMore(
          At(rpi, 18, 22),
          AliasedTypeExpression(
            At(rpi, 18, 21),
            "type",
            PathIdentifier(At(rpi, 18, 21), Seq("agg"))
          )
        )
      )
      checkDefinition[Type, Type](rpi, expected, identity)
    }

    "allow zero or more" in { (td: TestData) =>
      val rpi = RiddlParserInput("type zeroOrMore = many optional agg", td)
      val expected = Type(
        At(rpi, 0, 35),
        Identifier(At(rpi, 5, 16), "zeroOrMore"),
        ZeroOrMore(
          At(rpi, 18, 35),
          AliasedTypeExpression(
            At(rpi, 32, 35),
            "type",
            PathIdentifier(At(rpi, 32, 35), Seq("agg"))
          )
        )
      )
      checkDefinition[Type, Type](rpi, expected, identity)
    }

    "allow optionality" in { (td: TestData) =>
      val rpi = RiddlParserInput("type optional = optional agg", td)
      val expected = Type(
        At(rpi, 0, 28),
        Identifier(At(rpi, 5, 14), "optional"),
        Optional(
          At(rpi, 16, 28),
          AliasedTypeExpression(
            At(rpi, 25, 28),
            "type",
            PathIdentifier(At(rpi, 25, 28), Seq("agg"))
          )
        )
      )
      checkDefinition[Type, Type](rpi, expected, identity)
    }

    "allow messages defined with more natural syntax" in { (td: TestData) =>
      val rpi = RiddlParserInput("command foo is { a: Integer }", td)
      val expected = Type(
        At(rpi, 0, 29),
        Identifier(At(rpi, 8, 12), "foo"),
        AggregateUseCaseTypeExpression(
          At(rpi, 15, 29),
          AggregateUseCase.CommandCase,
          Contents(
            Field(
              At(rpi, 17, 28),
              Identifier(At(rpi, 17, 18), "a"),
              Integer(At(rpi, 20, 28))
            )
          )
        )
      )
      checkDefinition[Type, Type](rpi, expected, identity)
    }

    "allow complex nested type definitions" in { (td: TestData) =>
      val rpi = RiddlParserInput(
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
      parseDomainDefinition[Type](rpi, _.types.last) match {
        case Left(errors)          => fail(errors.format)
        case Right((typeDef, rpi)) =>
          // info(typeDef.toString)
          typeDef mustEqual Type(
            At(rpi, 146, 263),
            Identifier(At(rpi, 151, 159), "Complex"),
            Aggregation(
              At(rpi, 162, 263),
              Contents(
                Field(
                  At(rpi, 168, 177),
                  Identifier(At(rpi, 168, 169), "a"),
                  AliasedTypeExpression(
                    At(rpi, 171, 177),
                    "type",
                    PathIdentifier(At(rpi, 171, 177), Seq("Simple"))
                  )
                ),
                Field(
                  At(rpi, 183, 195),
                  Identifier(At(rpi, 183, 184), "b"),
                  TimeStamp(At(rpi, 186, 195))
                ),
                Field(
                  At(rpi, 201, 233),
                  Identifier(At(rpi, 201, 202), "c"),
                  ZeroOrMore(
                    At(rpi, 204, 233),
                    AliasedTypeExpression(
                      At(rpi, 218, 233),
                      "record",
                      PathIdentifier(At(rpi, 225, 233), Seq("Compound"))
                    )
                  )
                ),
                Field(
                  At(rpi, 239, 261),
                  Identifier(At(rpi, 239, 240), "d"),
                  Optional(
                    At(rpi, 242, 261),
                    AliasedTypeExpression(
                      At(rpi, 251, 261),
                      "type",
                      PathIdentifier(At(rpi, 251, 261), Seq("Choices"))
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
