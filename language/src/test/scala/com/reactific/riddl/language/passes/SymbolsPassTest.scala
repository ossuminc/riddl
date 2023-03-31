package com.reactific.riddl.language.passes

import com.reactific.riddl.language.passes.symbols.SymbolsOutput
import com.reactific.riddl.language.{CommonOptions, ParsingTest}
import com.reactific.riddl.language.AST.*
import org.scalatest.Assertion

import scala.reflect.ClassTag

/** Unit Tests For SymbolsPassTest */
class SymbolsPassTest extends ParsingTest {


  def captureEverythingSymbols: SymbolsOutput = {
    val root = checkFile("everything", "everything.riddl")
    val input: ParserOutput = ParserOutput(root, CommonOptions())
    Pass.runSymbols(input)
  }

  "SymbolsPass" must {
    val st = captureEverythingSymbols

    def assertRefWithParent[T <: Definition : ClassTag, P <: Definition : ClassTag](
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
      assertRefWithParent[Context, Domain](Seq("APlant"), "Everything")
      assertRefWithParent[Streamlet, Context](Seq("Source"), "APlant")
      assertRefWithParent[Streamlet, Context](Seq("Sink"), "APlant")
      assertRefWithParent[Context, Domain](Seq("full"), "Everything")
      assertRefWithParent[Type, Context](Seq("boo"), "full")
      assertRefWithParent[Entity, Context](Seq("Something"), "full")
      assertRefWithParent[Function, Entity](
        Seq("whenUnderTheInfluence"),
        "Something"
      )
      assertRefWithParent[Handler, State](Seq("foo"), "someState")
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
