package com.yoppworks.ossum.riddl.parser

import com.yoppworks.ossum.riddl.parser.AST._

import scala.collection.immutable.ListMap

/** Unit Tests For TypesParserTest */
class TypesParserTest extends ParsingTest {

  "TypesParser" should {
    "allow renames of 8 literal types" in {
      val cases = Map[String, TypeDef](
        "type str = String" -> TypeDef(
          (1, 1),
          Identifier((1, 6), "str"),
          Strng((1, 12))
        ),
        "type num = Number" -> TypeDef(
          (1, 1),
          Identifier((1, 6), "num"),
          Number((1, 12))
        ),
        "type boo = Boolean" -> TypeDef(
          (1, 1),
          Identifier((1, 6), "boo"),
          Bool((1, 12))
        ),
        "type ident = Id" -> TypeDef(
          (1, 1),
          Identifier((1, 6), "ident"),
          Id((1, 14))
        ),
        "type dat = Date" -> TypeDef(
          (1, 1),
          Identifier((1, 6), "dat"),
          Date((1, 12))
        ),
        "type tim = Time" -> TypeDef(
          (1, 1),
          Identifier((1, 6), "tim"),
          Time((1, 12))
        ),
        "type stamp = TimeStamp" -> TypeDef(
          (1, 1),
          Identifier((1, 6), "stamp"),
          TimeStamp((1, 14))
        ),
        "type url = URL" -> TypeDef(
          (1, 1),
          Identifier((1, 6), "url"),
          URL((1, 12))
        ),
        "type FirstName = String" -> TypeDef(
          (1, 1),
          Identifier((1, 6), "FirstName"),
          Strng((1, 18))
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
                Identifier((2, 3), "key") -> Number(2, 8),
                Identifier((3, 3), "id") -> Id((3, 7)),
                Identifier((4, 3), "time") -> TimeStamp((4, 9))
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
  }
}
