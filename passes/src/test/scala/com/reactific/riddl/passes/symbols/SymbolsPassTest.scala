package com.reactific.riddl.passes.symbols

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.reactific.riddl.language.{At, CommonOptions, ParsingTest}
import com.reactific.riddl.passes.{Pass, PassInput}
import org.scalatest.Assertion

import scala.reflect.ClassTag

/** Unit Tests For SymbolsPassTest */
class SymbolsPassTest extends ParsingTest {

  val st: SymbolsOutput = {
    val root = checkFile("everything", "everything.riddl")
    val input: PassInput = PassInput(root, CommonOptions())
    Pass.runSymbols(input)
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

    "resolve a path identifier" in {
      val rpi = RiddlParserInput(data = """domain d is {
          |  context c is {
          |    entity e is {
          |      state s of record c.eState is {
          |        handler h is {
          |          on command c.foo { ??? }
          |        }
          |      }
          |    }
          |    record eState is { f: Integer }
          |    command foo is { ??? }
          |  }
          |}
          |""".stripMargin)
      TopLevelParser.parse(rpi) match {
        case Left(errors) =>
          fail(errors.format)
        case Right(root) =>
          val passInput = PassInput(root)
          val so = Pass.runSymbols(passInput)
          val pathId = PathIdentifier(loc = At.empty, Seq("c", "eState"))
          val domain = root.contents.head
          val context = domain.contents.head
          val entity = context.contents.find(_.id.value == "e").get
          val state = entity.contents.find(_.id.value == "s").get
          val record = context.contents.find(_.id.value == "eState").get
          val parents = Seq(state, entity, context, domain)
          val result: Seq[Symbols.SymTabItem] = so.resolvePathId[Type](pathId, parents)
          result mustNot be(empty)
          result.size must be(1)
          val (node, nodeParents) = result.head
          node mustBe record
          nodeParents mustBe(Seq(record, context, domain))

      }
    }
  }
}
