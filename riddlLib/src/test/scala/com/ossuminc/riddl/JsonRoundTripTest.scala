/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Finder
import com.ossuminc.riddl.utils.{pc, PlatformContext}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.must.Matchers

/** Cross-platform JSON round-trip fidelity: parse RIDDL -> AST -> JSON (json1) -> AST -> JSON
  * (json2) and require `json1 == json2`. A stable fixed point proves the AST<->JSON mapping is
  * lossless and deterministic; any dropped or reordered construct makes the second JSON diverge.
  *
  * Runs on JVM, JS, and Native (unlike `Root2JsonCorpusTest`, which walks the `../riddl-models`
  * directory and is JVM-only). The inline model exercises the constructs the fidelity work fixed: a
  * multi-state entity with nested init handlers, entity-level messages, and a repository/projector
  * that define and reference their own messages.
  */
class JsonRoundTripTest extends AnyWordSpec with Matchers {

  private val model =
    """domain D is {
      |  context C is {
      |    command PlaceOrder is { qty: Integer }
      |    event OrderPlaced is { qty: Integer }
      |    entity Order is {
      |      type OpenData is { qty: Integer }
      |      type ClosedData is { reason: String }
      |      command CancelOrder is { id: String }
      |      event OrderCancelled is { id: String }
      |      state Open of record Order.OpenData is {
      |        handler OpenInit is {
      |          on init is { set state Open to "initialize" }
      |        }
      |      }
      |      state Closed of record Order.ClosedData is {
      |        handler ClosedInit is {
      |          on init is { set state Closed to "initialize" }
      |        }
      |      }
      |      handler OrderHandler is {
      |        on command PlaceOrder is {
      |          morph entity Order to state Order.Open with record PlaceOrder
      |          tell event OrderPlaced to entity Order
      |        }
      |        on command CancelOrder is {
      |          morph entity Order to state Order.Closed with record CancelOrder
      |          tell event OrderCancelled to entity Order
      |        }
      |      }
      |    }
      |    repository Repo is {
      |      query FindById is { id: String }
      |      result Found is { qty: Integer }
      |      handler RepoHandler is {
      |        on query FindById is { ??? }
      |      }
      |    }
      |    projector Proj is {
      |      handler ProjHandler is {
      |        on event OrderPlaced is { ??? }
      |      }
      |    }
      |  }
      |}
      |""".stripMargin

  "root2Json/parseJson round-trip" should {

    "be a JSON-identity fixed point on a multi-state model" in {
      RiddlLib.parseString(model) match
        case RiddlResult.Success(root0) =>
          val json1 = RiddlLib.root2Json(root0)
          RiddlLib.parseJson(json1) match
            case RiddlResult.Success(root1) =>
              val json2 = RiddlLib.root2Json(root1)
              json2 mustBe json1
            case RiddlResult.Failure(errors) =>
              fail(s"parseJson of the generated JSON failed: $errors")
          end match
        case RiddlResult.Failure(errors) =>
          fail(s"parse of the RIDDL model failed: $errors")
      end match
    }

    "preserve both entity states and processor-defined messages in the JSON" in {
      RiddlLib.parseString(model) match
        case RiddlResult.Success(root0) =>
          val json = RiddlLib.root2Json(root0)
          // Both states survive (the multi-state collapse regression guard)...
          json must include("\"Open\"")
          json must include("\"Closed\"")
          json must include("\"states\"")
          // ...and the repository's own query/result land in message arrays.
          json must include("\"FindById\"")
          json must include("\"Found\"")
        case RiddlResult.Failure(errors) =>
          fail(s"parse of the RIDDL model failed: $errors")
      end match
    }

    "round-trip the `initial` marker on states/handlers losslessly" in {
      val initModel =
        """domain d is { context c is { entity e is {
          |  type Data is { x: Integer }
          |  state First of record d.c.e.Data is { handler H is { on other is { prompt "a" } } }
          |  initial state Second of record d.c.e.Data is {
          |    initial handler H2 is { on other is { prompt "b" } }
          |  }
          |}}}
          |""".stripMargin
      RiddlLib.parseString(initModel) match
        case RiddlResult.Success(root0) =>
          val json1 = RiddlLib.root2Json(root0)
          json1 must include("\"isInitial\"")
          // fixed point proves JsonAstBuilder rebuilds isInitial (else json2 would lose the flag)
          RiddlLib.parseJson(json1) match
            case RiddlResult.Success(root1) => RiddlLib.root2Json(root1) mustBe json1
            case RiddlResult.Failure(errors) =>
              fail(s"parseJson of the initial-marker JSON failed: $errors")
          end match
        case RiddlResult.Failure(errors) =>
          fail(s"parse of the initial-marker model failed: $errors")
      end match
    }

    "round-trip named-type requires/returns on a function and saga (A9) losslessly" in {
      val rrModel =
        """domain d is { context c is {
          |  record Args is { a: Integer }
          |  result Res is { ok: Boolean }
          |  command Go is { x: Integer }
          |  command UndoGo is { x: Integer }
          |  entity e is { sink t is { inlet in is command Go } }
          |  function f is { requires record Args returns result Res ??? }
          |  saga s is {
          |    requires record Args
          |    returns result Res
          |    step One is { send command Go to inlet d.c.e.t.in }
          |      reverted by { send command UndoGo to inlet d.c.e.t.in }
          |    step Two is { prompt "do" } reverted by { prompt "undo" }
          |  }
          |}}
          |""".stripMargin
      RiddlLib.parseString(rrModel) match
        case RiddlResult.Success(root0) =>
          val json1 = RiddlLib.root2Json(root0)
          // The named references survive as ref strings (not inline field lists)...
          json1 must include("\"ref\"")
          json1 must include("record Args")
          json1 must include("result Res")
          // ...and JsonAstBuilder rebuilds them so the JSON is a fixed point.
          RiddlLib.parseJson(json1) match
            case RiddlResult.Success(root1) => RiddlLib.root2Json(root1) mustBe json1
            case RiddlResult.Failure(errors) =>
              fail(s"parseJson of the requires/returns JSON failed: $errors")
          end match
        case RiddlResult.Failure(errors) =>
          fail(s"parse of the requires/returns model failed: $errors")
      end match
    }

    "round-trip the 2.0 handler-kind clauses (on event / on activate / on passivate) losslessly" in {
      val hkModel =
        """domain HK is {
          |  context c is {
          |    command Cmd is { g: Integer }
          |    event Evt is { h: Integer }
          |    entity e is {
          |      handler h is {
          |        on command Cmd is { prompt "c" }
          |        on event Evt is { prompt "e" }
          |        on activate is { prompt "a" }
          |        on passivate is { prompt "p" }
          |        on other is { error "u" }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin
      RiddlLib.parseString(hkModel) match
        case RiddlResult.Success(root0) =>
          val json1 = RiddlLib.root2Json(root0)
          // The new clause kinds must appear (JsonifierPass emits them)...
          json1 must include("\"event\"")
          json1 must include("\"activate\"")
          json1 must include("\"passivate\"")
          // ...and JsonAstBuilder must rebuild them so the JSON is a fixed point.
          RiddlLib.parseJson(json1) match
            case RiddlResult.Success(root1) =>
              RiddlLib.root2Json(root1) mustBe json1
            case RiddlResult.Failure(errors) =>
              fail(s"parseJson of the handler-kinds JSON failed: $errors")
          end match
        case RiddlResult.Failure(errors) =>
          fail(s"parse of the handler-kinds model failed: $errors")
      end match
    }

    "round-trip a `foreach` statement (A25) losslessly" in {
      val feModel =
        """domain FE is { context c is {
          |  type Order is record { id: Integer }
          |  type OrderList is many Order
          |  type Batch is command { orders: OrderList }
          |  handler h is {
          |    on command Batch is {
          |      let batch: OrderList = "orders"
          |      foreach o in field Batch.orders {
          |        foreach p in batch { prompt "process" }
          |      }
          |    }
          |  }
          |}}
          |""".stripMargin
      RiddlLib.parseString(feModel) match
        case RiddlResult.Success(root0) =>
          val json1 = RiddlLib.root2Json(root0)
          // JsonifierPass emits the foreach kind with both a field-ref and a local collection...
          json1 must include("\"foreach\"")
          json1 must include("Batch.orders")
          // ...and JsonAstBuilder rebuilds them so the JSON is a fixed point.
          RiddlLib.parseJson(json1) match
            case RiddlResult.Success(root1) => RiddlLib.root2Json(root1) mustBe json1
            case RiddlResult.Failure(errors) =>
              fail(s"parseJson of the foreach JSON failed: $errors")
          end match
        case RiddlResult.Failure(errors) =>
          fail(s"parse of the foreach model failed: $errors")
      end match
    }

    "round-trip `put`/`return` value expressions (A45/A54/A57) losslessly" in {
      val vModel =
        """domain VD is {
          |  context Calc is {
          |    type Sum is record { total: Integer }
          |    function Add is {
          |      returns record Sum
          |      return record Sum(total = "the total")
          |    }
          |  }
          |  application context UI is {
          |    type Greeting is record { text: String }
          |    command Refresh is { ??? }
          |    group Main is {
          |      form Entry acquires type Greeting
          |      output Panel presents type Greeting
          |    }
          |    handler Screen is {
          |      on command Refresh is {
          |        put get from input Entry to output Panel
          |      }
          |    }
          |  }
          |}
          |""".stripMargin
      RiddlLib.parseString(vModel) match
        case RiddlResult.Success(root0) =>
          val json1 = RiddlLib.root2Json(root0)
          json1 must include("\"return\"")
          json1 must include("\"put\"")
          json1 must include("\"constructor\"")
          json1 must include("\"get\"")
          RiddlLib.parseJson(json1) match
            case RiddlResult.Success(root1) => RiddlLib.root2Json(root1) mustBe json1
            case RiddlResult.Failure(errors) =>
              fail(s"parseJson of the value JSON failed: $errors")
          end match
        case RiddlResult.Failure(errors) =>
          fail(s"parse of the value model failed: $errors")
      end match
    }

    "round-trip a command/query `yields` clause (A19) losslessly" in {
      val yModel =
        """domain YD is {
          |  context c is {
          |    event OrderPlaced is { id: Integer }
          |    result Found is { id: Integer }
          |    command PlaceOrder yields event OrderPlaced is { id: Integer }
          |    query FindOrder yields result Found is { id: Integer }
          |  }
          |}
          |""".stripMargin
      RiddlLib.parseString(yModel) match
        case RiddlResult.Success(root0) =>
          val json1 = RiddlLib.root2Json(root0)
          // JsonifierPass emits the yields ref...
          json1 must include("\"yields\"")
          json1 must include("OrderPlaced")
          json1 must include("Found")
          RiddlLib.parseJson(json1) match
            case RiddlResult.Success(root1) =>
              // Fixed point: JsonAstBuilder rebuilds yields, else json2 would drop it.
              RiddlLib.root2Json(root1) mustBe json1
              val f = Finder(root1.contents)
              val types = f.recursiveFindByType[Type]
              val cmd = types.find(_.id.value == "PlaceOrder").get
              cmd.typEx match
                case a: AggregateUseCaseTypeExpression =>
                  a.yields match
                    case Some(EventRef(_, pid)) => pid.value.last mustBe "OrderPlaced"
                    case other                  => fail(s"Expected EventRef, got $other")
                case other => fail(s"Expected AUCTE, got $other")
            case RiddlResult.Failure(errors) =>
              fail(s"parseJson of the yields JSON failed: $errors")
          end match
        case RiddlResult.Failure(errors) =>
          fail(s"parse of the yields model failed: $errors")
      end match
    }

    "round-trip context intention, ascribed shape (Some/None), and ports losslessly (Task 16)" in {
      val pmModel =
        """domain PM is {
          |  type T is String
          |  application context Orders as flow is {
          |    processor P as split is {
          |      inlet i is T
          |      outlet o1 is T
          |      outlet o2 is T
          |    }
          |    processor Q is {
          |      inlet qi is T
          |    }
          |    entity E is {
          |      inlet ei is T
          |    }
          |  }
          |}
          |""".stripMargin
      RiddlLib.parseString(pmModel) match
        case RiddlResult.Success(root0) =>
          val json1 = RiddlLib.root2Json(root0)
          json1 must include("\"intention\"")
          json1 must include("\"application\"")
          json1 must include("\"flow\"")
          json1 must include("\"split\"")
          RiddlLib.parseJson(json1) match
            case RiddlResult.Success(root1) =>
              // Fixed point: the AST<->JSON mapping is lossless for the new fields.
              RiddlLib.root2Json(root1) mustBe json1
              // And the rebuilt AST carries intention, shape (Some AND None), and ports.
              val f = Finder(root1.contents)
              val ctx = f.recursiveFindByType[Context].head
              ctx.intention mustBe Some(Intention.Application)
              ctx.ascribedShape.map(_.keyword) mustBe Some("flow")
              val p = f.recursiveFindByType[Streamlet].find(_.id.value == "P").get
              p.ascribedShape.map(_.keyword) mustBe Some("split")
              p.inlets.map(_.id.value) mustBe Seq("i")
              p.outlets.map(_.id.value) mustBe Seq("o1", "o2")
              val q = f.recursiveFindByType[Streamlet].find(_.id.value == "Q").get
              q.ascribedShape mustBe None
              q.inlets.map(_.id.value) mustBe Seq("qi")
              val e = f.recursiveFindByType[Entity].head
              e.inlets.map(_.id.value) mustBe Seq("ei")
            case RiddlResult.Failure(errors) =>
              fail(s"parseJson of the processor-model JSON failed: $errors")
          end match
        case RiddlResult.Failure(errors) =>
          fail(s"parse of the processor-model model failed: $errors")
      end match
    }
  }
}
