/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.prettify

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Finder
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.passes.validate.AbstractValidatingTest
import com.ossuminc.riddl.passes.{Pass, PassInput, PassesOutput}
import com.ossuminc.riddl.utils.pc

import org.scalatest.*

/** A56: `tell`/`send` accept a name bound by the enclosing on-clause, so a handler can forward the
  * message it was handed. RIDDL is reflective, so that operand must survive prettify → re-parse as
  * a [[ValueRef]] and NOT be rewritten into a keyword-led [[MessageRef]].
  *
  * The negative half — that an unbound name is an Error rather than silently accepted — lives in
  * `BoundMessageOperandValidationTest`, because a green round trip proves the operand is preserved,
  * not that it means anything.
  */
class BoundMessageOperandRoundTripTest extends AbstractValidatingTest {

  private def parse(src: String, origin: String): Root =
    TopLevelParser.parseInput(RiddlParserInput(src, origin)) match
      case Right(root) => root
      case Left(msgs)  => fail(s"parse of $origin failed:\n${msgs.format}")

  private def prettify(root: Root): String =
    val creators = Pass.standardPasses :+ { (in: PassInput, out: PassesOutput) =>
      PrettifyPass(in, out, PrettifyPass.Options(flatten = true, inputDir = ""))
    }
    val result = Pass.runThesePasses(PassInput(root), creators)
    result.outputs
      .outputOf[PrettifyOutput](PrettifyPass.name)
      .getOrElse(fail("PrettifyPass produced no output"))
      .state
      .filesAsString

  private val src =
    """domain d is {
      |  context c is {
      |    command Ping is { note: String }
      |    entity E is {
      |      outlet out is type d.c.Ping
      |      handler h is {
      |        on p: command d.c.Ping is {
      |          tell p to entity d.c.F
      |          send p to outlet d.c.E.out
      |        }
      |      }
      |    }
      |    entity F is {
      |      handler g is {
      |        on command d.c.Ping is { do "handle" }
      |      }
      |    }
      |  }
      |}
      |""".stripMargin

  /** Both operands, in source order, as (statement-kind, operand-rendering). */
  private def operands(root: Root): Seq[(String, String)] =
    Finder(root).recursiveFindByType[Statement].collect {
      case t: TellStatement => "tell" -> t.msg.format
      case s: SendStatement => "send" -> s.msg.format
    }

  private def boundOperandCount(root: Root): Int =
    Finder(root).recursiveFindByType[Statement].count {
      case t: TellStatement => t.msg.isInstanceOf[ValueRef]
      case s: SendStatement => s.msg.isInstanceOf[ValueRef]
      case _                => false
    }

  "A56 bound message operand" should {

    "parse as a ValueRef and survive a prettify round trip" in { (td: TestData) =>
      val root1 = parse(src, "src")
      operands(root1) mustBe Seq("tell" -> "p", "send" -> "p")
      // The operand must be a ValueRef, not a MessageRef whose path happens to be `p`. A
      // MessageRef would resolve `p` as a type name and mean something entirely different.
      boundOperandCount(root1) mustBe 2

      val pretty = prettify(root1)
      pretty must include("on p: command d.c.Ping is")
      pretty must include("tell p to entity d.c.F")
      pretty must include("send p to outlet d.c.E.out")
      // It must NOT be re-spelled with a message keyword on the way out.
      pretty must not include ("tell command p")
      pretty must not include ("send command p")

      val root2 = parse(pretty, "regen")
      operands(root2) mustBe Seq("tell" -> "p", "send" -> "p")
      boundOperandCount(root2) mustBe 2
      // The binding that gives the operand its meaning has to survive alongside it.
      Finder(root2)
        .recursiveFindByType[OnMessageLikeClause]
        .flatMap(_.binding.map(_.value)) mustBe Seq("p")
    }

    "leave a keyword-led operand exactly as it was" in { (td: TestData) =>
      // A56 widened the operand; it must not have changed how an ordinary one parses or prints.
      val plain =
        """domain d is {
          |  context c is {
          |    command Ping is { note: String }
          |    entity F is {
          |      handler g is {
          |        on command d.c.Ping is { do "handle" }
          |      }
          |    }
          |    entity E is {
          |      handler h is {
          |        on command d.c.Ping is { tell command d.c.Ping to entity d.c.F }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin
      val root = parse(plain, "plain")
      boundOperandCount(root) mustBe 0
      val pretty = prettify(root)
      pretty must include("tell command d.c.Ping to entity d.c.F")
      boundOperandCount(parse(pretty, "plain-regen")) mustBe 0
    }
  }
}
