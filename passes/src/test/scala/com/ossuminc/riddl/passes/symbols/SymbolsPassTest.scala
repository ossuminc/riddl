package com.ossuminc.riddl.passes.symbols

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.language.{At, CommonOptions, ParsingTest}
import com.ossuminc.riddl.passes.{Pass, PassInput, PassesOutput}
import org.scalatest.Assertion

import scala.reflect.ClassTag

/** Unit Tests For SymbolsPassTest */
class SymbolsPassTest extends ParsingTest {

  val st: SymbolsOutput = {
    val root = checkFile("everything", "everything.riddl")
    val input: PassInput = PassInput(root, CommonOptions())
    val outputs = PassesOutput()
    Pass.runSymbols(input, outputs)
  }

  def assertRefWithParent[T <: Definition: ClassTag, P <: Definition: ClassTag](
    names: Seq[String],
    parentName: String
  ): Assertion = {
    val lookupResult = st.lookup[T](names)
    lookupResult.headOption match {
      case None => fail(s"Symbol '${names.mkString(".")}' not found")
      case Some(definition) =>
        val p = st.parentOf(definition)
        if p.isEmpty then fail(s"Symbol '${names.mkString(".")}' has no parent")
        p.get mustBe a[P]
        p.get.id.value mustEqual parentName
    }
  }

  "SymbolsPass" must {
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
