package com.yoppworks.ossum.riddl.language

import com.yoppworks.ossum.riddl.language.AST._

class SymbolTableTest extends ParsingTest {

  "Symbol table" should {
    "capture all expected symbol references" in {
      val domain = checkFile("everything", "everything.riddl")
      val st = SymbolTable(domain)

      st.lookup[Domain](Seq("Everything")).headOption mustBe defined
      st.lookup[Type](Seq("SomeType")).headOption mustBe defined
      st.lookup[Topic](Seq("AChannel")).headOption mustBe defined
      st.lookup[Command](Seq("DoThisThing")).headOption mustBe defined
      st.lookup[Event](Seq("ThingWasDone")).headOption mustBe defined
      st.lookup[Query](Seq("FindThisThing")).headOption mustBe defined
      st.lookup[Context](Seq("full")).headOption mustBe defined
      st.lookup[Type](Seq("boo")).headOption mustBe defined
      st.lookup[Entity](Seq("Something")).headOption mustBe defined
      st.lookup[Function](Seq("whenUnderTheInfluence"))
        .headOption mustBe defined
      st.lookup[Consumer](Seq("foo")).headOption mustBe defined

      st.lookup[Type](Seq("somethingDate")).headOption mustBe defined

      // some interesting lookups

      // lookup of a message field resolves to the parent message
      // rather than the field, even though field is a defn
      st.lookup[Command](Seq("thingField")).headOption mustBe defined
      st.lookup[Field](Seq("thingField")).headOption mustNot be(defined)

      // lookup of a state identifier resolves to the parent Entity
      // rather that a State, even though state is a defn
      st.lookup[Entity](Seq("someState")).headOption mustBe defined
      st.lookup[State](Seq("someState")).headOption mustNot be(defined)

      // lookup of a state field does not resolve at all
      st.lookup[Definition](Seq("field")) must be(empty)
    }
  }
}
