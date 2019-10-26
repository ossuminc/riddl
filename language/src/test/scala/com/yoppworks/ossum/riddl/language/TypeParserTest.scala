package com.yoppworks.ossum.riddl.language

import com.yoppworks.ossum.riddl.language.AST._

import scala.collection.immutable.ListMap

/** Unit Tests For TypesParserTest */
class TypeParserTest extends ParsingTest {

  "TypeParser" should {
    "allow renames of 8 literal types" in {
      val cases = Map[String, TypeDef](
        "type str = String" -> TypeDef(
          1 -> 1,
          Identifier(1 -> 6, "str"),
          Strng(1 -> 12)
        ),
        "type num = Number" -> TypeDef(
          1 -> 1,
          Identifier(1 -> 6, "num"),
          Number(1 -> 12)
        ),
        "type boo = Boolean" -> TypeDef(
          1 -> 1,
          Identifier(1 -> 6, "boo"),
          Bool(1 -> 12)
        ),
        "type ident = Id()" -> TypeDef(
          1 -> 1,
          Identifier(1 -> 6, "ident"),
          UniqueId(1 -> 14, Identifier(1 -> 14, ""))
        ),
        "type dat = Date" -> TypeDef(
          1 -> 1,
          Identifier(1 -> 6, "dat"),
          Date(1 -> 12)
        ),
        "type tim = Time" -> TypeDef(
          1 -> 1,
          Identifier(1 -> 6, "tim"),
          Time(1 -> 12)
        ),
        "type stamp = TimeStamp" -> TypeDef(
          1 -> 1,
          Identifier(1 -> 6, "stamp"),
          TimeStamp(1 -> 14)
        ),
        "type url = URL" -> TypeDef(
          1 -> 1,
          Identifier(1 -> 6, "url"),
          URL(1 -> 12)
        ),
        "type FirstName = url" -> TypeDef(
          1 -> 1,
          Identifier(1 -> 6, "FirstName"),
          TypeRef(1 -> 18, Identifier(1 -> 18, "url"))
        )
      )
      checkDefinitions[TypeDef, TypeDef](cases, identity)
    }
    "allow enumerators" in {
      val input = "type enum = any { Apple Pear Peach Persimmon }"
      val expected =
        TypeDef(
          1 -> 1,
          Identifier(1 -> 6, "enum"),
          Enumeration(
            1 -> 13,
            List(
              Identifier(1 -> 19, "Apple"),
              Identifier(1 -> 25, "Pear"),
              Identifier(1 -> 30, "Peach"),
              Identifier(1 -> 36, "Persimmon")
            )
          )
        )
      checkDefinition[TypeDef, TypeDef](input, expected, identity)
    }
    "allow alternation" in {
      val input = "type alt = choose { enum or stamp or url } "
      val expected = TypeDef(
        1 -> 1,
        Identifier(1 -> 6, "alt"),
        Alternation(
          1 -> 12,
          List(
            TypeRef(1 -> 21, Identifier(1 -> 21, "enum")),
            TypeRef(1 -> 29, Identifier(1 -> 29, "stamp")),
            TypeRef(1 -> 38, Identifier(1 -> 38, "url"))
          )
        )
      )
      checkDefinition[TypeDef, TypeDef](input, expected, identity)
    }
    "allow aggregation" in {
      val input =
        """type agg = combine {
          |  key: Number,
          |  id: Id(),
          |  time: TimeStamp
          |}
          |""".stripMargin
      val expected = TypeDef(
        1 -> 1,
        Identifier(1 -> 6, "agg"),
        Aggregation(
          1 -> 12,
          ListMap(
            Identifier(2 -> 3, "key") ->
              Number(2 -> 8),
            Identifier(3 -> 3, "id") ->
              UniqueId(3 -> 7, Identifier(3 -> 7, "")),
            Identifier(4 -> 3, "time") ->
              TimeStamp(4 -> 9)
          )
        )
      )
      checkDefinition[TypeDef, TypeDef](input, expected, identity)
    }
    "allow mappings between two types" in {
      val input = "type m1 = mapping from String to Number"
      val expected = TypeDef(
        1 -> 1,
        Identifier(1 -> 6, "m1"),
        Mapping(
          1 -> 11,
          Strng(1 -> 24),
          Number(1 -> 34)
        )
      )
      checkDefinition[TypeDef, TypeDef](input, expected, identity)
    }
    "allow one or more in word style" in {
      val input = "type oneOrMoreA = many agg"
      val expected = TypeDef(
        1 -> 1,
        Identifier(1 -> 6, "oneOrMoreA"),
        OneOrMore(1 -> 24, TypeRef(1 -> 24, Identifier(1 -> 24, "agg")))
      )
      checkDefinition[TypeDef, TypeDef](input, expected, identity)
    }
    "allow one or more in simple style" in {
      val input = "type oneOrMoreC = agg..."
      val expected = TypeDef(
        1 -> 1,
        Identifier(1 -> 6, "oneOrMoreC"),
        OneOrMore(1 -> 19, TypeRef(1 -> 19, Identifier(1 -> 19, "agg")))
      )
      checkDefinition[TypeDef, TypeDef](input, expected, identity)
    }
    "allow one or more in regex style" in {
      val input = "type oneOrMoreB = agg+"
      val expected = TypeDef(
        1 -> 1,
        Identifier(1 -> 6, "oneOrMoreB"),
        OneOrMore(1 -> 19, TypeRef(1 -> 19, Identifier(1 -> 19, "agg")))
      )
      checkDefinition[TypeDef, TypeDef](input, expected, identity)
    }
    "allow zero or more" in {
      val input = "type zeroOrMore = many optional agg"
      val expected = TypeDef(
        1 -> 1,
        Identifier(1 -> 6, "zeroOrMore"),
        ZeroOrMore(1 -> 33, TypeRef(1 -> 33, Identifier(1 -> 33, "agg")))
      )
      // TypeDef((1:1),Identifier((1:6),zeroOrMore),ZeroOrMore((1:33),TypeRef((1:33),Identifier((1:33),agg))),None)
      // TypeDef((1:1),Identifier((1:6),zeroOrMore),ZeroOrMore((1:19),TypeRef((1:33),Identifier((1:33),agg))),None)
      checkDefinition[TypeDef, TypeDef](input, expected, identity)
    }
    "allow optionality" in {
      val input = "type optional = optional agg"
      val expected = TypeDef(
        1 -> 1,
        Identifier(1 -> 6, "optional"),
        Optional(1 -> 26, TypeRef(1 -> 26, Identifier(1 -> 26, "agg")))
      )
      checkDefinition[TypeDef, TypeDef](input, expected, identity)
    }
    "allow complex nested type definitions" in {
      val input =
        """
          |domain foo {
          |  type Simple = String
          |  type Compound is combine {
          |    s: Simple,
          |    ns: many Number
          |  }
          |  type Choices is choose { Number or Id }
          |  type Complex is combine {
          |    a: Simple,
          |    b: TimeStamp,
          |    c: many optional Compound,
          |    d: optional Choices
          |  }
          |}
          |""".stripMargin
      parseDomainDefinition[TypeDef](input, _.types.last) match {
        case Left(msg) => fail(msg)
        case Right(typeDef) =>
          info(typeDef.toString)
          typeDef mustEqual TypeDef(
            9 -> 3,
            Identifier(9 -> 8, "Complex"),
            Aggregation(
              9 -> 19,
              ListMap(
                Identifier(10 -> 5, "a") ->
                  TypeRef(10 -> 8, Identifier(10 -> 8, "Simple")),
                Identifier(11 -> 5, "b") ->
                  TimeStamp(11 -> 8),
                Identifier(12 -> 5, "c") ->
                  ZeroOrMore(
                    12 -> 22,
                    TypeRef(12 -> 22, Identifier(12 -> 22, "Compound"))
                  ),
                Identifier(13 -> 5, "d") ->
                  Optional(
                    13 -> 17,
                    TypeRef(13 -> 17, Identifier(13 -> 17, "Choices"))
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
