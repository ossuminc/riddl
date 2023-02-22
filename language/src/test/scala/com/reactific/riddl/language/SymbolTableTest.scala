/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.parsing.RiddlParserInput
import org.scalatest.Assertion

import scala.reflect.ClassTag

class SymbolTableTest extends ParsingTest {

  "Symbol table" should {

    def captureEverythingSymbols: SymbolTable = {
      val root = checkFile("everything", "everything.riddl")
      SymbolTable(root)
    }

    val st = captureEverythingSymbols

    def assertRefWithParent[
      T <: Definition: ClassTag,
      P <: Definition: ClassTag
    ](names: Seq[String],
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

    "handle #116 - https://github.com/reactific/riddl/issues/116" in {
      val src = """
                  |domain ImprovingApp is {
                  |  context Vendor is {
                  |    entity Product is {???}
                  |  }
                  |  domain Inventory is {
                  |    context Product is {
                  |      entity Product is {???}
                  |    }
                  |  }
                  |}""".stripMargin
      val input = RiddlParserInput(src)
      Riddl.parseAndValidate(
        input,
        CommonOptions(showMissingWarnings = false, showStyleWarnings = false)
      ) match {
        case Right(result) =>
          val model = result.root
          model.isRootContainer must be(true)
          model.contents.headOption must not(be(empty))
          model.contents.head.contexts.size must be(1)
        case Left(errors) => fail(s"""Failed to parse & validate: " +
                                     |${errors.format}""".stripMargin)
      }
    }
  }
}
