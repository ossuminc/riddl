/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{Contents, *}
import com.ossuminc.riddl.language.At
import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.utils.{ec, pc, Await}
import com.ossuminc.riddl.utils.PathUtils

import java.nio.file.Path
import org.scalatest.TestData

import scala.concurrent.duration.DurationInt

class ContextValidationTest extends JVMAbstractValidatingTest {

  "Context" should {
    "allow options" in { (td: TestData) =>
      val input =
        """domain bar {
         |context foo { ??? } with {
         |  option wrapper option service option gateway option package("foo")
         |  option technology("http")
         |}}
       """.stripMargin
      parseAndValidate(input, "", true) {
        case (root: Root, rpi: RiddlParserInput, msgs: Messages) =>
          msgs.filter(_.kind.isError) mustBe empty
          val context = root.domains.head.contexts.head
          context.options.size mustBe 5
          val names = context.options.map(_.name)
          names must contain("wrapper")
          names must contain("service")
          names must contain("gateway")
          context.options.find(_.name == "package") match {
            case Some(option) =>
              option must be(
                OptionValue(
                  At(rpi, 87, 109),
                  "package",
                  Seq(LiteralString(At(rpi, 102, 107), "foo"))
                )
              )
            case None => fail("No package option")
          }
          context.options.find(_.name == "technology") match {
            case Some(option) =>
              option must be(
                OptionValue(
                  At(rpi, 111, 137),
                  "technology",
                  Seq(LiteralString(At(rpi, 129, 135), "http"))
                )
              )
            case None => fail("No technology option")
          }
      }
    }
    "allow types" in { (td: TestData) =>
      val input = """type C is Current
                    |""".stripMargin
      parseAndValidateContext(input) { case (context: Context, rpi, msgs: Messages) =>
        msgs.filter(_.kind.isError) mustBe empty
        context.types.size mustBe 1
        context.types.head mustBe Type(
          (2, 2, rpi),
          Identifier((2, 7, rpi), "C"),
          Current((2, 12, rpi))
        )
      }
    }
    "allow functions" in { (td: TestData) =>
      val input =
        """function fun is {
          |  requires { i: Integer }
          |  returns { o: Integer }
          |  ???
          |}
          |""".stripMargin
      parseAndValidateContext(input) { case (context: Context, rpi, msgs: Messages) =>
        val errors = msgs.justErrors
        // info(errors.format)
        errors mustBe empty
        context.functions.size mustBe 1
        val function = context.functions.head
        function mustBe Function((2, 2, rpi), Identifier((2, 11, rpi), "fun"))
        // The `mustBe` above deliberately says nothing about `requires`/`returns`. They are
        // Contents now rather than constructor fields, and `Definition.equals` SKIPS Contents
        // fields (AST.scala:1038) to avoid O(subtree) comparison -- so they need assertions of
        // their own, where before they rode along as product elements.
        function.input mustBe Some(
          Aggregation(
            (3, 12, rpi),
            Contents(
              Field(
                (3, 14, rpi),
                Identifier((3, 14, rpi), "i"),
                Integer((3, 17, rpi))
              )
            )
          )
        )
        function.output mustBe Some(
          Aggregation(
            (4, 11, rpi),
            Contents(
              Field(
                (4, 13, rpi),
                Identifier((4, 13, rpi), "o"),
                Integer((4, 16, rpi))
              )
            )
          )
        )
      }
    }
    "allow entities" in { (td: TestData) =>
      val input = """entity ent is { ??? }
                    |""".stripMargin
      parseAndValidateContext(input) { case (context: Context, rpi, msgs: Messages) =>
        val errors = msgs.justErrors
        // info(errors.format)
        errors must be(empty)
        val expected = Entity((2, 2, rpi), Identifier((2, 9, rpi), "ent"))
        context.entities.size mustBe 1
        context.entities.head mustBe expected
      }

    }
    "allow processors" in { (td: TestData) =>
      val input = """source src is { ??? }
                    |""".stripMargin
      parseAndValidateContext(input) { case (context: Context, rpi, msgs: Messages) =>
        val errors = msgs.justErrors
        // info(errors.format)
        errors must be(empty)
        // The ascribed shape's loc is normalized to At.empty on every surface
        // (parser/BAST/JSON) so that `ascribedShape` — which participates in
        // Definition.equals — stays surface-independent.
        val expected = Streamlet(
          (2, 2, rpi),
          Identifier((2, 9, rpi), "src"),
          Some(Source(At.empty))
        )
        // [4.1]: `source src is { ??? }` declares NO portlets, so it is not a streamlet under the
        // ruling — a streamlet is a processor with a non-zero portlet count, whatever keyword
        // declared it. The case is about what the PARSER produced, so it asks the contents.
        context.streamlets mustBe empty
        val declared = context.contents.filter[Streamlet]
        declared.size mustBe 1
        declared.head mustBe expected
      }
    }
    "allow projectors" in { (td: TestData) =>
      val input =
        """projector prj is {
          |  record one is { ??? }
          |  handler two is { ??? }
          |}
          |""".stripMargin
      parseAndValidateContext(input) { case (context: Context, rpi, msgs: Messages) =>
        val errors = msgs.justErrors
        // info(errors.format)
        errors must be(empty)
        context.projectors.size mustBe 1
        val actual = context.projectors.head
        val expected =
          Projector(
            At(rpi, 34, 104),
            Identifier(At(rpi, 44, 48), "prj"),
            Contents(
              Type(
                At(rpi, 55, 79),
                Identifier(At(rpi, 62, 66), "one"),
                AggregateUseCaseTypeExpression(
                  At(rpi, 69, 79),
                  AggregateUseCase.RecordCase,
                  Contents.empty()
                )
              ),
              Handler(
                At(rpi, 79, 102),
                Identifier(At(rpi, 87, 91), "two"),
                Contents.empty()
              )
            )
          )
        actual mustBe expected
      }
    }

    "allow includes" in { (td: TestData) =>
      val name = "language/input/context/context-with-include.riddl"
      val path = Path.of(name)
      val url = PathUtils.urlFromCwdPath(path)
      val future = RiddlParserInput.fromURL(url).map { rpi =>
        parseTopLevelDomains(rpi) match
          case Left(errors) => fail(errors.format)
          case Right(root) =>
            root mustNot be(empty)
            root.contents.isEmpty mustNot be(true)
            val d = root.domains.head
            d.contexts mustNot be(empty)
            val c = d.contexts.head
            c.includes mustNot be(empty)
            val inc = c.includes.head
            inc.contents.filter[Comment].headOption match
              case Some(c: Comment) => c.format.contains("foo")
              case None             => fail("test case should have term 'foo'")
            end match
        end match
      }
      Await.result(future, 10.seconds)
    }
  }
}
