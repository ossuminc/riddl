/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands

import com.ossuminc.riddl.commands.find.*
import com.ossuminc.riddl.commands.project.{ProjectedNode, ProjectionOutput, ProjectionPass}
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.passes.{Pass, PassInput, PassesOutput, Riddl}
import com.ossuminc.riddl.passes.validate.AbstractValidatingTest
import com.ossuminc.riddl.utils.{CommonOptions, pc}
import org.scalatest.TestData

/** `riddlc find -type` after `initiate` was renamed `create` (Reid, 2026-11-16). The node's kind is
  * now `create`; `-type initiate` must still select it, because a query that silently returned zero
  * after a rename is the failure `find` exists to prevent.
  *
  * Neither spelling selected anything until the same day: the projection held definitions and
  * statements but never a VALUE held in a field, so no value kind was selectable at all. Values are
  * projected now, at any depth (`Finder.nestedValues`).
  */
class FindCreateTypeTest extends AbstractValidatingTest {

  private val src =
    """domain D is {
      |  context C is {
      |    command Go is { why: String }
      |    record Fields is { total: String }
      |    entity Order is {
      |      state S of record Fields is {
      |        handler H is { on init(total: String) { do "seed" } }
      |      }
      |    }
      |    entity Caller is {
      |      state CS of record Fields is {
      |        handler CH is {
      |          on command Go {
      |            let orderId = create entity Order("5")
      |            let go = command Go(why = prompt("the reason"))
      |          }
      |        }
      |      }
      |    }
      |  }
      |}
      |""".stripMargin

  private def nodes(td: TestData): Seq[ProjectedNode] =
    var out: Seq[ProjectedNode] = Nil
    pc.withOptions(CommonOptions(showWarnings = false)) { _ =>
      Riddl.parseAndValidate(RiddlParserInput(src, td), shouldFailOnError = false) match
        case Left(msgs) => fail(s"parse failed:\n${msgs.map(_.message).mkString("\n")}")
        case Right(result) =>
          out = Pass
            .runPass[ProjectionOutput](
              PassInput(result.root),
              PassesOutput(),
              ProjectionPass(PassInput(result.root), result.outputs)
            )
            .nodes
    }
    out

  private def find(expr: String, td: TestData): Seq[ProjectedNode] =
    val parsed = FindExpression.parse(expr.split(" ").toSeq.filter(_.nonEmpty)) match
      case Right(p)  => p
      case Left(err) => fail(s"expression '$expr' did not parse: $err")
    val ctx = FindContext(depthOf = n => n.parents.size)
    nodes(td).filter(n => parsed.expr.matches(n, ctx))

  "find -type" should {
    "select a create value as `create`" in { (td: TestData) =>
      find("-type create", td) must have size 1
    }
    "still select it as `initiate`, the deprecated spelling" in { (td: TestData) =>
      find("-type initiate", td) must have size 1
    }
    "select a value nested inside another value's argument" in { (td: TestData) =>
      // `prompt(…)` sits inside the constructor's argument, two fields below the `let`.
      find("-type prompt-value", td) must have size 1
    }
    "not project plain literals or value references as value nodes" in { (td: TestData) =>
      val values = nodes(td).map(_.value)
      values.collect { case l: com.ossuminc.riddl.language.AST.LiteralString => l } mustBe empty
      // A value reference is projected only as its own `value-reference` record, never twice.
      nodes(td).count(n => n.record("kind").str == "valueref") mustBe 0
    }
  }
}
