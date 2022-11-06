/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language

import com.reactific.riddl.language.AST.Field
import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.parsing.RiddlParserInput

/** Unit Tests For TypesParserTest */
class TypeParserTest extends ParsingTest {

  "TypeParser" should {
    "allow renames of String" in {
      val rpi = RiddlParserInput("type str = String")
      val expected =
        Type((1, 1, rpi), Identifier((1, 6, rpi), "str"), Strng((1, 12, rpi)))
      checkDefinition[Type, Type](rpi, expected, identity)
    }
    "allow renames of Number" in {
      val rpi = RiddlParserInput("type num = Number")
      val expected =
        Type((1, 1, rpi), Identifier((1, 6, rpi), "num"), Number((1, 12, rpi)))
      checkDefinition[Type, Type](rpi, expected, identity)
    }
    "allow rename of Abstract" in {
      val input = "type abs = Abstract"
      val expected = Type(1 -> 1, Identifier(1 -> 6, "abs"), Abstract(1 -> 12))
      checkDefinition[Type, Type](input, expected, identity)
    }
    "allow rename of Boolean" in {
      val input = "type boo = Boolean"
      val expected = Type(1 -> 1, Identifier(1 -> 6, "boo"), Bool(1 -> 12))
      checkDefinition[Type, Type](input, expected, identity)
    }
    "allow rename of Current" in {
      val input = "type cur = Current"
      val expected = Type(1 -> 1, Identifier(1 -> 6, "cur"), Current(1 -> 12))
      checkDefinition[Type, Type](input, expected, identity)
    }
    "allow rename of Currency(US)" in {
      val input = "type cur = Currency(US)"
      val expected =
        Type(1 -> 1, Identifier(1 -> 6, "cur"), Currency(1 -> 12, "US"))
      checkDefinition[Type, Type](input, expected, identity)
    }
    "allow rename of Length" in {
      val input = "type len = Length"
      val expected = Type(1 -> 1, Identifier(1 -> 6, "len"), Length(1 -> 12))
      checkDefinition[Type, Type](input, expected, identity)
    }
    "allow rename of Luminosity" in {
      val input = "type lum = Luminosity"
      val expected =
        Type(1 -> 1, Identifier(1 -> 6, "lum"), Luminosity(1 -> 12))
      checkDefinition[Type, Type](input, expected, identity)
    }
    "allow rename of Mass" in {
      val input = "type mas = Mass"
      val expected = Type(1 -> 1, Identifier(1 -> 6, "mas"), Mass(1 -> 12))
      checkDefinition[Type, Type](input, expected, identity)
    }
    "allow rename of Mole" in {
      val input = "type mol = Mole"
      val expected = Type(1 -> 1, Identifier(1 -> 6, "mol"), Mole(1 -> 12))
      checkDefinition[Type, Type](input, expected, identity)
    }
    "allow rename of Temperature" in {
      val input = "type tmp = Temperature"
      val expected =
        Type(1 -> 1, Identifier(1 -> 6, "tmp"), Temperature(1 -> 12))
      checkDefinition[Type, Type](input, expected, identity)
    }
    "allow renames of Id(path)" in {
      val input = "type ident = Id()"
      val expected = Type(
        1 -> 1,
        Identifier(1 -> 6, "ident"),
        UniqueId(
          1 -> 14,
          entityPath = PathIdentifier(1 -> 14, Seq.empty[String])
        )
      )
      checkDefinition[Type, Type](input, expected, identity)
    }
    "allow renames of 8 literal types" in {
      val cases = Map[String, Type](
        "type dat = Date" ->
          Type(1 -> 1, Identifier(1 -> 6, "dat"), Date(1 -> 12)),
        "type tim = Time" ->
          Type(1 -> 1, Identifier(1 -> 6, "tim"), Time(1 -> 12)),
        "type stamp = TimeStamp" ->
          Type(1 -> 1, Identifier(1 -> 6, "stamp"), TimeStamp(1 -> 14)),
        "type url = URL" ->
          Type(1 -> 1, Identifier(1 -> 6, "url"), URL(1 -> 12)),
        "type FirstName = URL" ->
          Type(1 -> 1, Identifier(1 -> 6, "FirstName"), URL(1 -> 18, None))
      )
      checkDefinitions[Type, Type](cases, identity)
    }
    "allow enumerators" in {
      val input = "type enum = any of { Apple Pear Peach Persimmon }"
      val expected = Type(
        1 -> 1,
        Identifier(1 -> 6, "enum"),
        Enumeration(
          1 -> 13,
          List(
            Enumerator(1 -> 22, Identifier(1 -> 22, "Apple"), None),
            Enumerator(1 -> 28, Identifier(1 -> 28, "Pear"), None),
            Enumerator(1 -> 33, Identifier(1 -> 33, "Peach"), None),
            Enumerator(1 -> 39, Identifier(1 -> 39, "Persimmon"), None)
          )
        )
      )
      checkDefinition[Type, Type](input, expected, identity)
    }
    "allow alternation" in {
      val input = "type alt = one of { enum or stamp or url }"
      val expected = Type(
        1 -> 1,
        Identifier(1 -> 6, "alt"),
        Alternation(
          1 -> 12,
          List(
            AliasedTypeExpression(
              1 -> 21,
              PathIdentifier(1 -> 21, Seq("enum"))
            ),
            AliasedTypeExpression(
              1 -> 29,
              PathIdentifier(1 -> 29, Seq("stamp"))
            ),
            AliasedTypeExpression(1 -> 38, PathIdentifier(1 -> 38, Seq("url")))
          )
        )
      )
      checkDefinition[Type, Type](input, expected, identity)
    }
    "allow alternation of a lone type reference" in {
      val rpi = RiddlParserInput("""domain Blah is {
                                   |type Foo = String
                                   |type alt = one of { type Foo }
                                   |}
                                   |""".stripMargin)
      val expected = Alternation(
        (3, 12, rpi),
        List(AliasedTypeExpression(
          (3, 21, rpi),
          PathIdentifier((3, 26, rpi), Seq("Foo"))
        ))
      )
      parseDomainDefinition[Type](rpi, _.types.last) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right((Type(_, _, typeExp, _, _), _)) => typeExp mustBe expected
      }
    }
    "allow aggregation" in {
      val rip = RiddlParserInput("""type agg = {
                                   |  key: Number,
                                   |  id: Id(),
                                   |  time: TimeStamp
                                   |}
                                   |""".stripMargin)
      val expected = Type(
        (1, 1, rip),
        Identifier((1, 6, rip), "agg"),
        Aggregation(
          (1, 12, rip),
          Seq(
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
                PathIdentifier((3, 7, rip), Seq.empty[String])
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
    "allow command, event, query, and result message aggregations" in {
      for { mk <- Seq("command", "event", "query", "result") } {
        val prefix = s"type mkt = $mk {"
        val rip = RiddlParserInput(prefix + """
                                              |  key: Number,
                                              |  id: Id(),
                                              |  time: TimeStamp
                                              |}
                                              |""".stripMargin)
        val expected = Type(
          (1, 1, rip),
          Identifier((1, 6, rip), "mkt"),
          MessageType(
            (1, 12, rip),
            mk match {
              case "command" => CommandKind
              case "event"   => EventKind
              case "query"   => QueryKind
              case "result"  => ResultKind
            },
            Seq(
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
                  PathIdentifier((3, 7, rip), Seq.empty[String])
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
    "allow mappings between two types" in {
      val rip = RiddlParserInput("type m1 = mapping from String to Number")
      val expected = Type(
        (1, 1, rip),
        Identifier((1, 6, rip), "m1"),
        Mapping((1, 11, rip), Strng((1, 24, rip)), Number((1, 34, rip)))
      )
      checkDefinition[Type, Type](rip, expected, identity)
    }
    "allow range of values" in {
      val rip = RiddlParserInput("type r1 = range(21,  42)")
      val expected = Type(
        (1, 1, rip),
        Identifier((1, 6, rip), "r1"),
        RangeType((1, 11, rip), 21, 42)
      )
      checkDefinition[Type, Type](rip, expected, identity)
    }

    "allow one or more in word style" in {
      val rip = RiddlParserInput("type oneOrMoreA = many agg")
      val expected = Type(
        (1, 1, rip),
        Identifier((1, 6, rip), "oneOrMoreA"),
        OneOrMore(
          (1, 24, rip),
          AliasedTypeExpression(
            (1, 24, rip),
            PathIdentifier((1, 24, rip), Seq("agg"))
          )
        )
      )
      checkDefinition[Type, Type](rip, expected, identity)
    }

    "allow one or more in regex style" in {
      val rip = RiddlParserInput("type oneOrMoreB = agg+")
      val expected = Type(
        (1, 1, rip),
        Identifier((1, 6, rip), "oneOrMoreB"),
        OneOrMore(
          (1, 19, rip),
          AliasedTypeExpression(
            (1, 19, rip),
            PathIdentifier((1, 19, rip), Seq("agg"))
          )
        )
      )
      checkDefinition[Type, Type](rip, expected, identity)
    }

    "allow zero or more" in {
      val rip = RiddlParserInput("type zeroOrMore = many optional agg")
      val expected = Type(
        (1, 1, rip),
        Identifier((1, 6, rip), "zeroOrMore"),
        ZeroOrMore(
          (1, 33, rip),
          AliasedTypeExpression(
            (1, 33, rip),
            PathIdentifier((1, 33, rip), Seq("agg"))
          )
        )
      )
      checkDefinition[Type, Type](rip, expected, identity)
    }

    "allow optionality" in {
      val rip = RiddlParserInput("type optional = optional agg")
      val expected = Type(
        (1, 1, rip),
        Identifier((1, 6, rip), "optional"),
        Optional(
          (1, 26, rip),
          AliasedTypeExpression(
            (1, 26, rip),
            PathIdentifier((1, 26, rip), Seq("agg"))
          )
        )
      )
      checkDefinition[Type, Type](rip, expected, identity)
    }

    "allow messages defined with more natural syntax" in {
      val rip = RiddlParserInput("command foo is { a: Integer }")
      val expected = Type(
        (1, 1, rip),
        Identifier((1, 9, rip), "foo"),
        MessageType(
          (1, 16, rip),
          CommandKind,
          Seq(Field(
            (1, 18, rip),
            Identifier((1, 18, rip), "a"),
            Integer((1, 21, rip))
          ))
        )
      )
      checkDefinition[Type, Type](rip, expected, identity)
    }

    "allow complex nested type definitions" in {
      val rip = RiddlParserInput("""
                                   |domain foo is {
                                   |  type Simple = String
                                   |  type Compound is {
                                   |    s: Simple,
                                   |    ns: many Number
                                   |  }
                                   |  type Choices is one of { Number or Id }
                                   |  type Complex is {
                                   |    a: Simple,
                                   |    b: TimeStamp,
                                   |    c: many optional Compound,
                                   |    d: optional Choices
                                   |  }
                                   |}
                                   |""".stripMargin)
      parseDomainDefinition[Type](rip, _.types.last) match {
        case Left(errors)          => fail(errors.format)
        case Right((typeDef, rpi)) =>
          // info(typeDef.toString)
          typeDef mustEqual Type(
            (9, 3, rpi),
            Identifier((9, 8, rpi), "Complex"),
            Aggregation(
              (9, 19, rpi),
              Seq(
                Field(
                  (10, 5, rpi),
                  Identifier((10, 5, rpi), "a"),
                  AliasedTypeExpression(
                    (10, 8, rpi),
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
                      PathIdentifier((12, 22, rpi), Seq("Compound"))
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
                      PathIdentifier((13, 17, rpi), Seq("Choices"))
                    )
                  )
                )
              )
            ),
            None
          )
          succeed
      }
    }
  }
}
