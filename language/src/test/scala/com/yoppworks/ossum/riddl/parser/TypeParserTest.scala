package com.yoppworks.ossum.riddl.parser

import com.yoppworks.ossum.riddl.parser.AST._

import scala.collection.immutable.ListMap

/** Unit Tests For TypesParserTest */
class TypeParserTest extends ParsingTest {

  "TypeParser" should {
    "allow renames of 8 literal types" in {
      val cases = Map[String, TypeDef](
        "type str = String" -> TypeDef(
          (1, 1),
          Identifier((1, 6), "str"),
          TypeRef((1, 12), Identifier((1, 12), "String"))
        ),
        "type num = Number" -> TypeDef(
          (1, 1),
          Identifier((1, 6), "num"),
          TypeRef((1, 12), Identifier((1, 12), "Number"))
        ),
        "type boo = Boolean" -> TypeDef(
          (1, 1),
          Identifier((1, 6), "boo"),
          TypeRef((1, 12), Identifier((1, 12), "Boolean"))
        ),
        "type ident = Id" -> TypeDef(
          (1, 1),
          Identifier((1, 6), "ident"),
          TypeRef((1, 14), Identifier((1, 14), "Id"))
        ),
        "type dat = Date" -> TypeDef(
          (1, 1),
          Identifier((1, 6), "dat"),
          TypeRef((1, 12), Identifier((1, 12), "Date"))
        ),
        "type tim = Time" -> TypeDef(
          (1, 1),
          Identifier((1, 6), "tim"),
          TypeRef((1, 12), Identifier((1, 12), "Time"))
        ),
        "type stamp = TimeStamp" -> TypeDef(
          (1, 1),
          Identifier((1, 6), "stamp"),
          TypeRef((1, 14), Identifier((1, 14), "TimeStamp"))
        ),
        "type url = URL" -> TypeDef(
          (1, 1),
          Identifier((1, 6), "url"),
          TypeRef((1, 12), Identifier((1, 12), "URL"))
        ),
        "type FirstName = String" -> TypeDef(
          (1, 1),
          Identifier((1, 6), "FirstName"),
          TypeRef((1, 18), Identifier((1, 18), "String"))
        )
      )
      checkDefinitions[TypeDef, TypeDef](cases, identity)
    }
    "allow various type definitions" in {
      val cases: Map[String, TypeDef] = Map(
        "type enum = any [ Apple Pear Peach Persimmon ]" ->
          TypeDef(
            (1, 1),
            Identifier((1, 6), "enum"),
            Enumeration(
              (1, 13),
              List(
                Identifier((1, 19), "Apple"),
                Identifier((1, 25), "Pear"),
                Identifier((1, 30), "Peach"),
                Identifier((1, 36), "Persimmon")
              )
            )
          ),
        "type alt = choose enum or stamp or url " ->
          TypeDef(
            (1, 1),
            Identifier((1, 6), "alt"),
            Alternation(
              (1, 12),
              List(
                TypeRef((1, 19), Identifier((1, 19), "enum")),
                TypeRef((1, 27), Identifier((1, 27), "stamp")),
                TypeRef((1, 36), Identifier((1, 36), "url"))
              )
            )
          ),
        """type agg = combine {
          |  key: Number,
          |  id: Id,
          |  time: TimeStamp
          |}
          |""".stripMargin ->
          TypeDef(
            (1, 1),
            Identifier((1, 6), "agg"),
            Aggregation(
              (1, 12),
              ListMap(
                Identifier((2, 3), "key") ->
                  TypeRef((2, 8), Identifier((2, 8), "Number")),
                Identifier((3, 3), "id") ->
                  TypeRef((3, 7), Identifier((3, 7), "Id")),
                Identifier((4, 3), "time") ->
                  TypeRef((4, 9), Identifier((4, 9), "TimeStamp"))
              )
            )
          ),
        "type oneOrMore = agg+" ->
          TypeDef(
            (1, 1),
            Identifier((1, 6), "oneOrMore"),
            OneOrMore((1, 18), Identifier((1, 18), "agg"))
          ),
        "type zeroOrMore = agg*" ->
          TypeDef(
            (1, 1),
            Identifier((1, 6), "zeroOrMore"),
            ZeroOrMore((1, 19), Identifier((1, 19), "agg"))
          ),
        "type optional = agg?" ->
          TypeDef(
            (1, 1),
            Identifier((1, 6), "optional"),
            Optional((1, 17), Identifier((1, 17), "agg"))
          )
      )
      checkDefinitions[TypeDef, TypeDef](cases, identity)
    }
    "allow complex nested type definitions" in {
      val input =
        """
          |domain foo {
          |  type Simple = String
          |  type Compound is combine {
          |    s: Simple,
          |    ns: Number+
          |  }
          |  type Choices is choose Number or Id
          |  type Complex is combine {
          |    a: Simple,
          |    b: TimeStamp,
          |    c: Compound*,
          |    d: Choices?
          |  }
          |}
          |""".stripMargin
      parseDomainDefinition[TypeDef](input, _.types.last) match {
        case Left(msg) => fail(msg)
        case Right(typeDef) =>
          println(typeDef)
          typeDef mustEqual TypeDef(
            (9, 3),
            Identifier((9, 8), "Complex"),
            Aggregation(
              (9, 19),
              ListMap(
                Identifier((10, 5), "a") ->
                  TypeRef((10, 8), Identifier((10, 8), "Simple")),
                Identifier((11, 5), "b") ->
                  TypeRef((11, 8), Identifier((11, 8), "TimeStamp")),
                Identifier((12, 5), "c") ->
                  ZeroOrMore((12, 8), Identifier((12, 8), "Compound")),
                Identifier((13, 5), "d") ->
                  Optional((13, 8), Identifier((13, 8), "Choices"))
              )
            ),
            None
          )
          succeed
      }
    }
  }
}
