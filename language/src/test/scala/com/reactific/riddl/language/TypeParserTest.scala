package com.reactific.riddl.language

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.parsing.RiddlParserInput

/** Unit Tests For TypesParserTest */
class TypeParserTest extends ParsingTest {

  "TypeParser" should {
    "allow renames of String" in {
      val input = "type str = String"
      val expected = Type(1 -> 1, Identifier(1 -> 6, "str"), Strng(1 -> 12))
      checkDefinition[Type, Type](input, expected, identity)
    }
    "allow renames of Number" in {
      val input = "type num = Number"
      val expected = Type(1 -> 1, Identifier(1 -> 6, "num"), Number(1 -> 12))
      checkDefinition[Type, Type](input, expected, identity)
    }
    "allow rename of Boolean" in {
      val input = "type boo = Boolean"
      val expected = Type(1 -> 1, Identifier(1 -> 6, "boo"), Bool(1 -> 12))
      checkDefinition[Type, Type](input, expected, identity)
    }
    "allow renames of Id(path)" in {
      val input = "type ident = Id()"
      val expected = Type(
        1 -> 1,
        Identifier(1 -> 6, "ident"),
        UniqueId(1 -> 14, entityPath = PathIdentifier(1 -> 14, Seq.empty[String]))
      )
      checkDefinition[Type, Type](input, expected, identity)
    }
    "allow renames of 8 literal types" in {
      val cases = Map[String, Type](
        "type dat = Date" -> Type(1 -> 1, Identifier(1 -> 6, "dat"), Date(1 -> 12)),
        "type tim = Time" -> Type(1 -> 1, Identifier(1 -> 6, "tim"), Time(1 -> 12)),
        "type stamp = TimeStamp" -> Type(1 -> 1, Identifier(1 -> 6, "stamp"), TimeStamp(1 -> 14)),
        "type url = URL" -> Type(1 -> 1, Identifier(1 -> 6, "url"), URL(1 -> 12)),
        "type FirstName = url" -> Type(
          1 -> 1,
          Identifier(1 -> 6, "FirstName"),
          TypeRef(1 -> 18, PathIdentifier(1 -> 18, Seq("url")))
        )
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
            TypeRef(1 -> 21, PathIdentifier(1 -> 21, Seq("enum"))),
            TypeRef(1 -> 29, PathIdentifier(1 -> 29, Seq("stamp"))),
            TypeRef(1 -> 38, PathIdentifier(1 -> 38, Seq("url")))
          )
        )
      )
      checkDefinition[Type, Type](input, expected, identity)
    }
    "allow alternation of a lone type reference" in {
      val input = RiddlParserInput("""domain Blah is {
                                     |type Foo = String
                                     |type alt = one of { type Foo }
                                     |}
                                     |""".stripMargin)
      val expected =
        Alternation(3 -> 12, List(TypeRef(3 -> 21, PathIdentifier(3 -> 26, Seq("Foo")))))
      parseDomainDefinition[Type](input, _.types.last) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right(Type(_, _, typeExp, _, _)) => typeExp mustBe expected
      }
    }
    "allow aggregation" in {
      val input = """type agg = {
                    |  key: Number,
                    |  id: Id(),
                    |  time: TimeStamp
                    |}
                    |""".stripMargin
      val expected = Type(
        1 -> 1,
        Identifier(1 -> 6, "agg"),
        Aggregation(
          1 -> 12,
          Seq(
            Field(2 -> 3, Identifier(2 -> 3, "key"), Number(2 -> 8)),
            Field(
              3 -> 3,
              Identifier(3 -> 3, "id"),
              UniqueId(3 -> 7, PathIdentifier(3 -> 7, Seq.empty[String]))
            ),
            Field(4 -> 3, Identifier(4 -> 3, "time"), TimeStamp(4 -> 9))
          )
        )
      )
      checkDefinition[Type, Type](input, expected, identity)
    }
    "allow command, event, query, and result message aggregations" in {
      for { mk <- Seq("command", "event", "query", "result") } {
        val prefix = s"type mkt = $mk {"
        val input = prefix + """
                               |  key: Number,
                               |  id: Id(),
                               |  time: TimeStamp
                               |}
                               |""".stripMargin
        val expected = Type(
          1 -> 1,
          Identifier(1 -> 6, "mkt"),
          MessageType(
            1 -> 12,
            mk match {
              case "command" => CommandKind
              case "event"   => EventKind
              case "query"   => QueryKind
              case "result"  => ResultKind
            },
            Seq(
              Field(
                1 -> 12,
                Identifier(1 -> 12, "sender"),
                ReferenceType(
                  1 -> 12,
                  EntityRef(1 -> 12, PathIdentifier(1 -> 12, Seq.empty[String]))
                )
              ),
              Field(2 -> 3, Identifier(2 -> 3, "key"), Number(2 -> 8)),
              Field(
                3 -> 3,
                Identifier(3 -> 3, "id"),
                UniqueId(3 -> 7, PathIdentifier(3 -> 7, Seq.empty[String]))
              ),
              Field(4 -> 3, Identifier(4 -> 3, "time"), TimeStamp(4 -> 9))
            )
          )
        )
        checkDefinition[Type, Type](input, expected, identity)
      }
    }
    "allow mappings between two types" in {
      val input = "type m1 = mapping from String to Number"
      val expected =
        Type(1 -> 1, Identifier(1 -> 6, "m1"), Mapping(1 -> 11, Strng(1 -> 24), Number(1 -> 34)))
      checkDefinition[Type, Type](input, expected, identity)
    }
    "allow range of values" in {
      val input = "type r1 = range(21,  42)"
      val expected = Type(
        1 -> 1,
        Identifier(1 -> 6, "r1"),
        RangeType(1 -> 11, LiteralInteger(1 -> 17, BigInt(21)), LiteralInteger(1 -> 22, BigInt(42)))
      )
      checkDefinition[Type, Type](input, expected, identity)

    }
    "allow one or more in word style" in {
      val input = "type oneOrMoreA = many agg"
      val expected = Type(
        1 -> 1,
        Identifier(1 -> 6, "oneOrMoreA"),
        OneOrMore(1 -> 24, TypeRef(1 -> 24, PathIdentifier(1 -> 24, Seq("agg"))))
      )
      checkDefinition[Type, Type](input, expected, identity)
    }
    "allow one or more in regex style" in {
      val input = "type oneOrMoreB = agg+"
      val expected = Type(
        1 -> 1,
        Identifier(1 -> 6, "oneOrMoreB"),
        OneOrMore(1 -> 19, TypeRef(1 -> 19, PathIdentifier(1 -> 19, Seq("agg"))))
      )
      checkDefinition[Type, Type](input, expected, identity)
    }
    "allow zero or more" in {
      val input = "type zeroOrMore = many optional agg"
      val expected = Type(
        1 -> 1,
        Identifier(1 -> 6, "zeroOrMore"),
        ZeroOrMore(1 -> 33, TypeRef(1 -> 33, PathIdentifier(1 -> 33, Seq("agg"))))
      )
      // TypeDef((1:1),Identifier((1:6),zeroOrMore),ZeroOrMore((1:33),TypeRef((1:33),Identifier((1:33),agg))),None)
      // TypeDef((1:1),Identifier((1:6),zeroOrMore),ZeroOrMore((1:19),TypeRef((1:33),Identifier((1:33),agg))),None)
      checkDefinition[Type, Type](input, expected, identity)
    }
    "allow optionality" in {
      val input = "type optional = optional agg"
      val expected = Type(
        1 -> 1,
        Identifier(1 -> 6, "optional"),
        Optional(1 -> 26, TypeRef(1 -> 26, PathIdentifier(1 -> 26, Seq("agg"))))
      )
      checkDefinition[Type, Type](input, expected, identity)
    }
    "allow complex nested type definitions" in {
      val input = """
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
                    |""".stripMargin
      parseDomainDefinition[Type](RiddlParserInput(input), _.types.last) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right(typeDef) =>
          info(typeDef.toString)
          typeDef mustEqual Type(
            9 -> 3,
            Identifier(9 -> 8, "Complex"),
            Aggregation(
              9 -> 19,
              Seq(
                Field(
                  10 -> 5,
                  Identifier(10 -> 5, "a"),
                  TypeRef(10 -> 8, PathIdentifier(10 -> 8, Seq("Simple")))
                ),
                Field(11 -> 5, Identifier(11 -> 5, "b"), TimeStamp(11 -> 8)),
                Field(
                  12 -> 5,
                  Identifier(12 -> 5, "c"),
                  ZeroOrMore(12 -> 22, TypeRef(12 -> 22, PathIdentifier(12 -> 22, Seq("Compound"))))
                ),
                Field(
                  13 -> 5,
                  Identifier(13 -> 5, "d"),
                  Optional(13 -> 17, TypeRef(13 -> 17, PathIdentifier(13 -> 17, Seq("Choices"))))
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
