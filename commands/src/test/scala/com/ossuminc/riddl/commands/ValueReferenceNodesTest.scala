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

/** Value references are first-class nodes, and `find` can select statements by them (riddl-models,
  * 2026-08-25).
  *
  * The projection gave statements a span, parent and ancestors, but their OPERANDS were opaque text
  * — so consumers regexed them, and riddl-models' attempts went wrong three ways in one afternoon:
  * reading only a statement's first line (missing 6 operands inside a `when`), summing over
  * statement spans (which NEST, so a `when` counted its contents twice — 259 against a true 253),
  * and assuming every state record type ends in `Data`, which is a house convention rather than a
  * language rule.
  *
  * `resolvedKind` is the point: riddlc distinguishes these when it validates — its own error
  * enumerates "a 'let'-local, an 'on init'/'on term' parameter, a 'foreach' element, a field of the
  * handled message or entity state, or a function input" — and then discarded the answer.
  */
class ValueReferenceNodesTest extends AbstractValidatingTest {

  private val src =
    """domain D is {
      |  context C is {
      |    event Created is { name: String(1,9) }
      |    record LiveData is { recipe: String(1,9)  name: String(1,9) }
      |    record GoneData is { why: String(1,9) }
      |    entity Item is {
      |      state Live of record C.LiveData is {
      |        handler H is {
      |          on event C.Created is {
      |            morph entity C.Item to state Item.Live with record C.LiveData(recipe = LiveData.recipe, name = Created.name)
      |          }
      |        }
      |      }
      |      state Gone of record C.GoneData is { ??? }
      |    }
      |  }
      |}
      |""".stripMargin

  private def nodes(td: TestData): Seq[ProjectedNode] =
    var out: Seq[ProjectedNode] = Nil
    pc.withOptions(CommonOptions(showWarnings = false)) { _ =>
      Riddl.parseAndValidate(RiddlParserInput(src, td), shouldFailOnError = false) match
        case Left(msgs) => fail(msgs.map(_.message).mkString("\n"))
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

  private def valueRefs(td: TestData): Seq[ProjectedNode] =
    nodes(td).filter(_.record.value.get("kind").contains(ujson.Str("value-reference")))

  private def kindOf(n: ProjectedNode): String =
    n.record.value.get("resolvedKind").collect { case s: ujson.Str => s.str }.getOrElse("")

  private def nameOf(n: ProjectedNode): String =
    n.record.value.get("name").collect { case s: ujson.Str => s.str }.getOrElse("")

  "dump --json" should {
    "emit one node per value reference" in { (td: TestData) =>
      val vrs = valueRefs(td)
      withClue(vrs.map(nameOf).mkString(", ")) {
        vrs.map(nameOf) must contain("LiveData.recipe")
        vrs.map(nameOf) must contain("Created.name")
      }
    }

    "classify a state record's field as `state-field`" in { (td: TestData) =>
      val n = valueRefs(td).find(x => nameOf(x) == "LiveData.recipe")
      withClue(valueRefs(td).map(x => s"${nameOf(x)}=${kindOf(x)}").mkString(", ")) {
        n.map(kindOf) mustBe Some("state-field")
      }
    }

    "classify the handled message's field as `message-field`" in { (td: TestData) =>
      val n = valueRefs(td).find(x => nameOf(x) == "Created.name")
      withClue(valueRefs(td).map(x => s"${nameOf(x)}=${kindOf(x)}").mkString(", ")) {
        n.map(kindOf) mustBe Some("message-field")
      }
    }

    "give each one a span, so a consumer need not re-find it in the text" in { (td: TestData) =>
      valueRefs(td).foreach(n => n.record.value.keySet must contain("span"))
    }
  }

  "find" should {
    /** A statement carries no `resolvedKind` of its own — its operands are separate nodes — so this
      * matches through SPAN CONTAINMENT. That is what turns "all 59 morphs" into "the morphs that
      * read state", which was the actual work item and previously happened in Python over text.
      */
    def matched(expr: String, td: TestData): Int =
      val parsed = FindExpression.parse(expr.split(" ").toSeq.filter(_.nonEmpty)) match
        case Right(p)  => p
        case Left(err) => fail(s"'$expr' did not parse: $err")
      val all = nodes(td)
      val vrs = all.filter(_.record.value.get("kind").contains(ujson.Str("value-reference")))
      def within(n: ProjectedNode): Seq[String] =
        val loc = n.value.loc
        vrs.collect {
          case vr if vr.value.loc.offset >= loc.offset && vr.value.loc.endOffset <= loc.endOffset =>
            kindOf(vr).toLowerCase
        }
      val ctx = FindContext(depthOf = n => n.parents.size, operandKindsOf = within)
      all.count(n => parsed.expr.matches(n, ctx))

    "select a statement by an operand kind inside it" in { (td: TestData) =>
      matched("-type morph-statement -reads-state", td) mustBe 1
    }

    "not select it for an operand kind it does not contain" in { (td: TestData) =>
      // The negative direction matters more than usual: a selector that matches everything and one
      // that works are indistinguishable until pointed at a corpus.
      matched("-type morph-statement -operand-kind function-input", td) mustBe 0
    }

    "select by source text when a kind is not what you want" in { (td: TestData) =>
      matched("""-type morph-statement -source-regex LiveData""", td) mustBe 1
      matched("""-type morph-statement -source-regex zzzz""", td) mustBe 0
    }
  }
}
