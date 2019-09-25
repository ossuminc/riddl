package com.yoppworks.ossum.riddl.parser

import com.yoppworks.ossum.riddl.parser.AST._

/** Unit Tests For TypesParserTest */
class TypesParserTest extends ParsingTest {

  "TypesParser" should {
    "allow renames of 8 literal types" in {
      val cases = Map[String, TypeDef](
        "type str = String" → TypeDef(33, Identifier("str"), String),
        "type num = Number" → TypeDef(33, Identifier("num"), Number),
        "type boo = Boolean" → TypeDef(33, Identifier("boo"), Boolean),
        "type ident  = Id" -> TypeDef(33, Identifier("ident"), Id),
        "type dat = Date" -> TypeDef(33, Identifier("dat"), Date),
        "type tim = Time" -> TypeDef(33, Identifier("tim"), Time),
        "type stamp = TimeStamp" -> TypeDef(33, Identifier("stamp"), TimeStamp),
        "type url = URL" -> TypeDef(33, Identifier("url"), URL),
        "type FirstName = String" -> TypeDef(
          33,
          Identifier("FirstName"),
          String
        )
      )
      checkDef[TypeDef](cases, _.contexts.head.types.head)
    }
    "allow enumerations" in {
      val cases: List[(String, TypeDef)] = List(
        "type enum = any [ Apple Pear Peach Persimmon ]" ->
          TypeDef(
            33,
            Identifier("enum"),
            Enumeration(
              List(
                Identifier("Apple"),
                Identifier("Pear"),
                Identifier("Peach"),
                Identifier("Persimmon")
              )
            )
          ),
        "type alt = choose enum or stamp or url " ->
          TypeDef(
            33,
            Identifier("alt"),
            Alternation(
              List(
                TypeRef(Identifier("enum")),
                TypeRef(Identifier("stamp")),
                TypeRef(Identifier("url"))
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
            33,
            Identifier("agg"),
            Aggregation(
              Map(
                Identifier("key") → Number,
                Identifier("id") → Id,
                Identifier("time") → TimeStamp
              )
            )
          ),
        "type oneOrMore = type agg+" ->
          TypeDef(
            33,
            Identifier("oneOrMore"),
            OneOrMore(TypeRef(Identifier("agg")))
          ),
        "type zeroOrMore = type agg*" ->
          TypeDef(
            33,
            Identifier("zeroOrMore"),
            ZeroOrMore(TypeRef(Identifier("agg")))
          ),
        "type optional = type agg?" ->
          TypeDef(
            33,
            Identifier("optional"),
            Optional(TypeRef(Identifier("agg")))
          )
      )
    }
  }
}
