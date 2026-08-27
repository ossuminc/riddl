/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Finder
import com.ossuminc.riddl.utils.pc
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.must.Matchers

/** `forward` on the JSON surface, in BOTH transmission shapes.
  *
  * `ForwardStmtDto` carries ONE `target` field rather than send's `portlet` and tell's `processor`,
  * because the kind string already distinguishes them -- a portlet kind is exactly `inlet`/`outlet`
  * and a processor kind is never either. Two fields would make "both set" and "neither set"
  * representable for no gain.
  *
  * **A JSON-identity fixed point is NOT sufficient on its own**, and this is the trap the numeric
  * literals work recorded: a consistently-dropped or consistently-mangled field is still a perfect
  * fixed point. So the shape is asserted directly as well -- the round-tripped AST must carry one
  * portlet-targeted and one processor-targeted forward, not merely "two forwards".
  */
class ForwardStatementJsonRoundTripTest extends AnyWordSpec with Matchers {

  private val model =
    """domain Delegation is {
      |  context Boundary is {
      |    event Happened is { note: String }
      |    result Answer is { note: String }
      |    command DoIt yields event Boundary.Happened is { note: String }
      |    query AskIt replies result Boundary.Answer is { note: String }
      |    entity Worker is {
      |      inlet Work is type Boundary.DoIt
      |      handler w is {
      |        on command Boundary.DoIt is { yield event Boundary.Happened }
      |        on query Boundary.AskIt is { reply result Boundary.Answer }
      |      }
      |    }
      |    entity Front is {
      |      outlet Onward is type Boundary.DoIt
      |      handler h is {
      |        on doIt: command Boundary.DoIt is {
      |          forward doIt to outlet Boundary.Front.Onward
      |        }
      |        on askIt: query Boundary.AskIt is {
      |          forward askIt to entity Boundary.Worker
      |        }
      |      }
      |    }
      |  }
      |}
      |""".stripMargin

  private def forwardsOf(root: Root): Seq[ForwardStatement] =
    Finder(root).recursiveFindByType[ForwardStatement]

  "forward" should {

    "be a JSON-identity fixed point" in {
      RiddlLib.parseString(model) match
        case RiddlResult.Success(root0) =>
          val json1 = RiddlLib.root2Json(root0)
          RiddlLib.parseJson(json1) match
            case RiddlResult.Success(root1) => RiddlLib.root2Json(root1) mustBe json1
            case RiddlResult.Failure(errors) => fail(s"parseJson failed: $errors")
          end match
        case RiddlResult.Failure(errors) => fail(s"parse failed: $errors")
      end match
    }

    "come back with BOTH target shapes intact, not merely with two forwards" in {
      RiddlLib.parseString(model) match
        case RiddlResult.Success(root0) =>
          RiddlLib.parseJson(RiddlLib.root2Json(root0)) match
            case RiddlResult.Success(root1) =>
              val fs = forwardsOf(root1)
              fs.size mustBe 2
              fs.count(_.target.isInstanceOf[PortletRef[?]]) mustBe 1
              fs.count(_.target.isInstanceOf[ProcessorRef[?]]) mustBe 1
            case RiddlResult.Failure(errors) => fail(s"parseJson failed: $errors")
          end match
        case RiddlResult.Failure(errors) => fail(s"parse failed: $errors")
      end match
    }

    "write kind 'forward' with a single target field naming the reference kind" in {
      RiddlLib.parseString(model) match
        case RiddlResult.Success(root0) =>
          val json = ujson.read(RiddlLib.root2Json(root0))
          val found = scala.collection.mutable.ListBuffer.empty[(String, String)]
          def walk(v: ujson.Value): Unit = v match
            case o: ujson.Obj =>
              if o.value.get("kind").exists(_.str == "forward") then
                found += ((o("to").str, o("target").str))
              o.value.values.foreach(walk)
            case a: ujson.Arr => a.value.foreach(walk)
            case _            => ()
          walk(json)
          withClue(s"forward objects found: $found") {
            found.size mustBe 2
            // `AST.Set` shadows `scala.Set` under the wildcard AST import -- CLAUDE.md records
            // this trap; qualify rather than dropping the wildcard.
            found.map(_._2).toSet mustBe scala.collection.immutable.Set("outlet", "entity")
          }
        case RiddlResult.Failure(errors) => fail(s"parse failed: $errors")
      end match
    }
  }
}
