package com.reactific.riddl.language

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.testkit.ParsingTest
import org.scalatest.Assertion

import scala.reflect.ClassTag

class SymbolTableTest extends ParsingTest {

  "Symbol table" should {

    def captureEverythingSymbols: SymbolTable = {
      val domain = checkFile("everything", "everything.riddl")
      SymbolTable(domain)
    }

    val st = captureEverythingSymbols

    def assertRefWithParent[T <: Definition: ClassTag, P <: Definition: ClassTag](
      names: Seq[String],
      parentName: String
    ): Assertion = {
      val lookupResult = st.lookup[T](names)
      lookupResult.headOption match {
        case None => fail(s"Symbol '${names.mkString(".")}' not found")
        case Some(definition) =>
          val p = st.parentOf(definition)
          if (p.isEmpty) fail(s"Symbol '${names.mkString(".")}' has no parent")
          p.get mustBe a[P]
          p.get.id.value mustEqual parentName
      }
    }

    "capture all expected symbol references and parents" in {
      st.lookup[Domain](Seq("Everything")).headOption mustBe defined

      assertRefWithParent[Type, Domain](Seq("DoAThing"), "Everything")
      assertRefWithParent[Type, Domain](Seq("SomeType"), "Everything")
      assertRefWithParent[Plant, Domain](Seq("APlant"), "Everything")
      assertRefWithParent[Pipe, Plant](Seq("AChannel"), "APlant")
      assertRefWithParent[Processor, Plant](Seq("Source"), "APlant")
      assertRefWithParent[Processor, Plant](Seq("Sink"), "APlant")
      assertRefWithParent[OutletJoint, Plant](Seq("input"), "APlant")
      assertRefWithParent[InletJoint, Plant](Seq("output"), "APlant")
      assertRefWithParent[Context, Domain](Seq("full"), "Everything")
      assertRefWithParent[Type, Context](Seq("boo"), "full")
      assertRefWithParent[Entity, Context](Seq("Something"), "full")
      assertRefWithParent[Function, Entity](Seq("whenUnderTheInfluence"), "Something")
      assertRefWithParent[Handler, Entity](Seq("foo"), "Something")
      assertRefWithParent[Type, Entity](Seq("somethingDate"), "Something")
    }

    "capture expected state reference with appropriate parent" in {
      assertRefWithParent[State, Entity](Seq("someState"), "Something")
    }

    "capture expected state field references with appropriate parent" in {
      st.lookup[Definition](Seq("field")) mustNot be(empty)
    }
  }
}
