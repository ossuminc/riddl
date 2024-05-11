package com.ossuminc.riddl.passes.symbols

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.parsing.ParsingTest
import com.ossuminc.riddl.language.{At,CommonOptions}
import com.ossuminc.riddl.passes.{Pass, PassInput, PassesOutput}
import org.scalatest.Assertion

import scala.reflect.ClassTag

/** Unit Tests For SymbolsPassTest */
class SymbolsPassTest extends ParsingTest {

  val (root: Root, st: SymbolsOutput) = {
    val root = checkFile("everything", "everything.riddl")
    val input: PassInput = PassInput(root, CommonOptions())
    val outputs = PassesOutput()
    root -> Pass.runSymbols(input, outputs)
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

  "SymbolsOutput" must {
    "return empty list for non-existent namedValue" in {
      val nv: NamedValue = Domain(At(), Identifier(At(), "not-in-root"))
      st.parentsOf(nv) must be(Seq.empty[Definition])
    }
    "lookupSymbol(id: PathNames) should fail if no names" in {
      val xcption = intercept[IllegalArgumentException] { st.lookupSymbol[Context](Seq.empty[String]) }
      xcption.isInstanceOf[IllegalArgumentException] must be(true) 
    }
    "lookupSymbol() should return None for non-matching type" in {
      val list = st.lookupSymbol[Context](Seq("Everything"))
      list must not be(empty)
      list.head._1.isInstanceOf[Domain] must be(true)
      list.head._2 must be(None)
    }
    "lookupParentage(id: PathNames) should fail on no names" in {
      val xcption = intercept[IllegalArgumentException] { 
        val parents = st.lookupParentage(Seq.empty[String])
        parents must be(empty)
      }
      xcption.isInstanceOf[IllegalArgumentException] must be(true)
    }
    "lookup should fail on no names" in {
      val xcption = intercept[IllegalArgumentException] {
        val parents = st.lookup[Context](Seq.empty[String])
        parents must be(empty)
      }
      xcption.isInstanceOf[IllegalArgumentException] must be(true)
    }
    "lookup should return empty when nothing found" in {
      val list = st.lookup[Epic](Seq("SomeType", "Everything"))
      list must be(empty)
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
      assertRefWithParent[Adaptor, Context](Seq("fromAPlant"), "full")
      assertRefWithParent[Function, Entity](Seq("whenUnderTheInfluence"), "Something")
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
