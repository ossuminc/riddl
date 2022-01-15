package com.yoppworks.ossum.riddl.language

import com.yoppworks.ossum.riddl.language.AST._
import org.scalatest.Assertion

import scala.reflect.ClassTag

class SymbolTableTest extends ParsingTest {

  "Symbol table" should {

    def captureEverythingSymbols: SymbolTable = {
      val domain = checkFile("everything", "everything.riddl")
      SymbolTable(domain)
    }

    val st = captureEverythingSymbols

    def assertRefWithParent[T <: Definition, P <: Definition: ClassTag](
      maybeRef: Option[T],
      parentName: String
    ): Assertion = {
      maybeRef mustBe defined
      val p = st.parentOf(maybeRef.get)
      p mustBe defined
      p.get mustBe a[P]
      p.get.id.value mustEqual parentName
    }

    "capture all expected symbol references and parents" in {
      st.lookup[Domain](Seq("Everything")).headOption mustBe defined

      assertRefWithParent[Type, Domain](st.lookup[Type](Seq("DoAThing")).headOption, "Everything")
      assertRefWithParent[Type, Domain](st.lookup[Type](Seq("SomeType")).headOption, "Everything")
      assertRefWithParent[Plant, Domain](st.lookup[Plant](Seq("APlant")).headOption, "Everything")
      assertRefWithParent[Pipe, Plant](st.lookup[Pipe](Seq("AChannel")).headOption, "APlant")
      assertRefWithParent[Processor, Plant](
        st.lookup[Processor](Seq("Source")).headOption,
        "APlant"
      )
      assertRefWithParent[Processor, Plant](st.lookup[Processor](Seq("Sink")).headOption, "APlant")
      assertRefWithParent[Joint, Plant](st.lookup[Joint](Seq("input")).headOption, "APlant")
      assertRefWithParent[Joint, Plant](st.lookup[Joint](Seq("output")).headOption, "APlant")

      assertRefWithParent[Context, Domain](st.lookup[Context](Seq("full")).headOption, "Everything")

      assertRefWithParent[Type, Context](st.lookup[Type](Seq("boo")).headOption, "full")

      assertRefWithParent[Entity, Context](st.lookup[Entity](Seq("Something")).headOption, "full")

      assertRefWithParent[Function, Entity](
        st.lookup[Function](Seq("whenUnderTheInfluence")).headOption,
        "Something"
      )

      assertRefWithParent[Handler, Entity](st.lookup[Handler](Seq("foo")).headOption, "Something")

      assertRefWithParent[Type, Entity](
        st.lookup[Type](Seq("somethingDate")).headOption,
        "Something"
      )
    }

    "capture expected state reference with appropriate parent" in {
      assertRefWithParent[State, Entity](st.lookup[State](Seq("someState")).headOption, "Something")
    }

    "capture expected state field references with appropriate parent" in {
      // TODO: WIP - broken case, fix outstanding
      // some interesting lookups
      // lookup of a state field does not resolve at all
      st.lookup[Definition](Seq("field")) mustNot be(empty)
    }
  }
}
