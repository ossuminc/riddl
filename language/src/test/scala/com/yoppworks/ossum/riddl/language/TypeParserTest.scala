package com.yoppworks.ossum.riddl.language

import com.yoppworks.ossum.riddl.language.AST._

import scala.collection.immutable.ListMap

/** Unit Tests For TypesParserTest */
class TypeParserTest extends ParsingTest {

  "TypeParser" should {
    "allow renames of 8 literal types" in {
      val cases = Map[String, Type](
        "type str = String" -> Type(
          1 -> 1,
          Identifier(1 -> 6, "str"),
          Strng(1 -> 12)
        ),
        "type num = Number" -> Type(
          1 -> 1,
          Identifier(1 -> 6, "num"),
          Number(1 -> 12)
        ),
        "type boo = Boolean" -> Type(
          1 -> 1,
          Identifier(1 -> 6, "boo"),
          Bool(1 -> 12)
        ),
        "type ident = Id()" -> Type(
          1 -> 1,
          Identifier(1 -> 6, "ident"),
          UniqueId(1 -> 14, Identifier(1 -> 14, ""))
        ),
        "type dat = Date" -> Type(
          1 -> 1,
          Identifier(1 -> 6, "dat"),
          Date(1 -> 12)
        ),
        "type tim = Time" -> Type(
          1 -> 1,
          Identifier(1 -> 6, "tim"),
          Time(1 -> 12)
        ),
        "type stamp = TimeStamp" -> Type(
          1 -> 1,
          Identifier(1 -> 6, "stamp"),
          TimeStamp(1 -> 14)
        ),
        "type url = URL" -> Type(
          1 -> 1,
          Identifier(1 -> 6, "url"),
          URL(1 -> 12)
        ),
        "type FirstName = url" -> Type(
          1 -> 1,
          Identifier(1 -> 6, "FirstName"),
          TypeRef(1 -> 18, Identifier(1 -> 18, "url"))
        )
      )
      checkDefinitions[Type, Type](cases, identity)
    }
    "allow enumerators" in {
      val input = "type enum = [ Apple Pear Peach Persimmon ]"
      val expected =
        Type(
          1 -> 1,
          Identifier(1 -> 6, "enum"),
          Enumeration(
            1 -> 13,
            List(
              Enumerator(1 -> 15, Identifier(1 -> 15, "Apple"), None),
              Enumerator(1 -> 21, Identifier(1 -> 21, "Pear"), None),
              Enumerator(1 -> 26, Identifier(1 -> 26, "Peach"), None),
              Enumerator(1 -> 32, Identifier(1 -> 32, "Persimmon"), None)
            )
          )
        )
      checkDefinition[Type, Type](input, expected, identity)
    }
    "allow alternation" in {
      val input = "type alt = ( enum or stamp or url ) "
      val expected = Type(
        1 -> 1,
        Identifier(1 -> 6, "alt"),
        Alternation(
          1 -> 12,
          List(
            TypeRef(1 -> 14, Identifier(1 -> 14, "enum")),
            TypeRef(1 -> 22, Identifier(1 -> 22, "stamp")),
            TypeRef(1 -> 31, Identifier(1 -> 31, "url"))
          )
        )
      )
      checkDefinition[Type, Type](input, expected, identity)
    }
    "allow aggregation" in {
      val input =
        """type agg = {
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
      checkDefinition[Type, Type](input, expected, identity)
    }
    "allow mappings between two types" in {
      val input = "type m1 = mapping from String to Number"
      val expected = Type(
        1 -> 1,
        Identifier(1 -> 6, "m1"),
        Mapping(
          1 -> 11,
          Strng(1 -> 24),
          Number(1 -> 34)
        )
      )
      checkDefinition[Type, Type](input, expected, identity)
    }
    "allow one or more in word style" in {
      val input = "type oneOrMoreA = many agg"
      val expected = Type(
        1 -> 1,
        Identifier(1 -> 6, "oneOrMoreA"),
        OneOrMore(1 -> 24, TypeRef(1 -> 24, Identifier(1 -> 24, "agg")))
      )
      checkDefinition[Type, Type](input, expected, identity)
    }
    "allow one or more in simple style" in {
      val input = "type oneOrMoreC = agg..."
      val expected = Type(
        1 -> 1,
        Identifier(1 -> 6, "oneOrMoreC"),
        OneOrMore(1 -> 19, TypeRef(1 -> 19, Identifier(1 -> 19, "agg")))
      )
      checkDefinition[Type, Type](input, expected, identity)
    }
    "allow one or more in regex style" in {
      val input = "type oneOrMoreB = agg+"
      val expected = Type(
        1 -> 1,
        Identifier(1 -> 6, "oneOrMoreB"),
        OneOrMore(1 -> 19, TypeRef(1 -> 19, Identifier(1 -> 19, "agg")))
      )
      checkDefinition[Type, Type](input, expected, identity)
    }
    "allow zero or more" in {
      val input = "type zeroOrMore = many optional agg"
      val expected = Type(
        1 -> 1,
        Identifier(1 -> 6, "zeroOrMore"),
        ZeroOrMore(1 -> 33, TypeRef(1 -> 33, Identifier(1 -> 33, "agg")))
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
        Optional(1 -> 26, TypeRef(1 -> 26, Identifier(1 -> 26, "agg")))
      )
      checkDefinition[Type, Type](input, expected, identity)
    }
    "allow complex nested type definitions" in {
      val input =
        """
          |domain foo {
          |  type Simple = String
          |  type Compound is {
          |    s: Simple,
          |    ns: many Number
          |  }
          |  type Choices is ( Number or Id )
          |  type Complex is {
          |    a: Simple,
          |    b: TimeStamp,
          |    c: many optional Compound,
          |    d: optional Choices
          |  }
          |}
          |""".stripMargin
      parseDomainDefinition[Type](input, _.types.last) match {
        case Left(errors) =>
          val msg = errors.map(_.toString).mkString
          fail(msg)
        case Right(typeDef) =>
          info(typeDef.toString)
          typeDef mustEqual Type(
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
