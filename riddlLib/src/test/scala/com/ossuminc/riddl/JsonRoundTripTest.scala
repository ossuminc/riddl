/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Finder
import com.ossuminc.riddl.passes.Pass
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
          |  state First of record d.c.e.Data is { handler H is { on other is { do "a" } } }
          |  initial state Second of record d.c.e.Data is {
          |    initial handler H2 is { on other is { do "b" } }
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

    "round-trip the A55 on-clause message binding losslessly" in {
      val bindingModel =
        """domain d is { context c is { entity e is {
          |  command Foo is { a: Integer }
          |  event Bar is { b: Integer }
          |  handler H is {
          |    on foo: command Foo is { do "a" }
          |    on bar: event Bar is { do "b" }
          |    on command Foo is { do "c" }
          |  }
          |}}}
          |""".stripMargin
      RiddlLib.parseString(bindingModel) match
        case RiddlResult.Success(root0) =>
          val json1 = RiddlLib.root2Json(root0)
          json1 must include("\"binding\"")
          json1 must include("\"foo\"")
          json1 must include("\"bar\"")
          // The fixed point proves JsonAstBuilder rebuilds the binding (else json2 would lose it).
          RiddlLib.parseJson(json1) match
            case RiddlResult.Success(root1) => RiddlLib.root2Json(root1) mustBe json1
            case RiddlResult.Failure(errors) =>
              fail(s"parseJson of the binding JSON failed: $errors")
          end match
        case RiddlResult.Failure(errors) =>
          fail(s"parse of the binding model failed: $errors")
      end match
    }

    "round-trip A42 figma references losslessly" in {
      val figmaModel =
        """domain Storefront is {
          |  application context Checkout is {
          |    command PlaceOrder is { item: String }
          |    result Confirmation is { text: String }
          |    group PaymentScreen is {
          |      input CardNumber acquires command Storefront.Checkout.PlaceOrder with {
          |        figma "FILEKEY" node "12:34"
          |      }
          |      output OrderSummary presents result Storefront.Checkout.Confirmation with {
          |        figma "FILEKEY" node "12:36"
          |      }
          |    } with { figma "FILEKEY" node "12:30" }
          |  } with { figma "FILEKEY" node "12:1" }
          |}
          |""".stripMargin
      RiddlLib.parseString(figmaModel) match
        case RiddlResult.Success(root0) =>
          val json1 = RiddlLib.root2Json(root0)
          json1 must include("\"figmaRefs\"")
          json1 must include("\"12:30\"")
          json1 must include("\"FILEKEY\"")
          // The fixed point proves JsonAstBuilder rebuilds every FigmaRef; if any were dropped,
          // json2 would be missing it.
          RiddlLib.parseJson(json1) match
            case RiddlResult.Success(root1) => RiddlLib.root2Json(root1) mustBe json1
            case RiddlResult.Failure(errors) =>
              fail(s"parseJson of the figma JSON failed: $errors")
          end match
        case RiddlResult.Failure(errors) =>
          fail(s"parse of the figma model failed: $errors")
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
          |    step Two is { do "do" } reverted by { do "undo" }
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

    // S61-1: a Module is a FLAT collection of ANY top-level definition, so ModuleDto carries a
    // group per kind. The fixed point proves both the serialize arm (JsonifierPass) and the build
    // arm (JsonAstBuilder) cover every group — a missing arm silently drops members.
    "round-trip a mixed-contents Module losslessly" in {
      val moduleModel =
        """module M is {
          |  author Reid is { name is "Reid Spencer" email is "reid@ossuminc.com" }
          |  type Amount is Number
          |  constant Limit is Number = "100"
          |  user Shopper is "a person who buys things"
          |  invariant Positive is "the limit is positive"
          |  function Compute is { ??? }
          |  context Ordering is { type Placed is event { when: TimeStamp } }
          |  entity Loose is { handler Anything is { ??? } }
          |  adaptor FromOrdering from context Ordering is { ??? }
          |  projector Totals is {
          |    record Snapshot is { total: Number }
          |    handler Updates is { ??? }
          |  }
          |  repository Ledger is { ??? }
          |  saga Checkout is {
          |    step ReserveStock is { do "reserve" } reverted by { do "release" }
          |    step ChargeCard is { do "charge" } reverted by { do "refund" }
          |  }
          |  epic Buying is {
          |    user Shopper wants to "buy something" so that "they own it"
          |    type Cart is String
          |  }
          |  domain Retail is { context Store is { ??? } }
          |  module Nested is { type Inner is String }
          |}
          |""".stripMargin
      RiddlLib.parseString(moduleModel) match
        case RiddlResult.Success(root0) =>
          val json1 = RiddlLib.root2Json(root0)
          // Every widened group must actually carry its member.
          for name <- Seq(
              "Reid",
              "Amount",
              "Limit",
              "Shopper",
              "Positive",
              "Compute",
              "Ordering",
              "Loose",
              "FromOrdering",
              "Totals",
              "Ledger",
              "Checkout",
              "Buying",
              "Retail",
              "Nested"
            )
          do json1 must include("\"" + name + "\"")
          RiddlLib.parseJson(json1) match
            case RiddlResult.Success(root1) =>
              root1.modules.headOption.map(_.id.value) mustBe Some("M")
              RiddlLib.root2Json(root1) mustBe json1
            case RiddlResult.Failure(errors) =>
              fail(s"parseJson of the module JSON failed: $errors")
          end match
        case RiddlResult.Failure(errors) =>
          fail(s"parse of the module model failed: $errors")
      end match
    }

    "round-trip a refusal interaction step (A38) losslessly" in {
      val refusalModel =
        """domain ImprovingApp is {
          |  context OrganizationContext is {
          |    entity Organization is { ??? }
          |  }
          |  user Owner is "a person"
          |  epic EstablishOrganization is {
          |    user ImprovingApp.Owner wants "to establish an organization" so that "business happens"
          |    case primary is {
          |      user ImprovingApp.Owner wants "to incorporate" so that "it can be used"
          |      step entity ImprovingApp.OrganizationContext.Organization
          |        refuses user ImprovingApp.Owner "not authorized"
          |    }
          |  }
          |}
          |""".stripMargin
      RiddlLib.parseString(refusalModel) match
        case RiddlResult.Success(root0) =>
          val json1 = RiddlLib.root2Json(root0)
          // JsonifierPass emits the refusal discriminator, from ref, user, and reason...
          json1 must include("\"refusal\"")
          json1 must include("not authorized")
          // ...and JsonAstBuilder rebuilds it so the JSON is a fixed point.
          RiddlLib.parseJson(json1) match
            case RiddlResult.Success(root1) => RiddlLib.root2Json(root1) mustBe json1
            case RiddlResult.Failure(errors) =>
              fail(s"parseJson of the refusal JSON failed: $errors")
          end match
        case RiddlResult.Failure(errors) =>
          fail(s"parse of the refusal model failed: $errors")
      end match
    }

    "round-trip a `call function F(args)` value (A24) losslessly" in {
      val callModel =
        """domain d is {
          |  context Calc is {
          |    type Args is record { a: Integer, b: Integer }
          |    type Sum is record { total: Integer }
          |    function Add is {
          |      requires record Args
          |      returns record Sum
          |      return record Sum(total = "t")
          |    }
          |    function Now is {
          |      returns record Sum
          |      return record Sum(total = "0")
          |    }
          |    function Caller is {
          |      requires record Args
          |      returns record Sum
          |      return call function Add(a = "1", b = "2")
          |    }
          |    function CallerZero is {
          |      returns record Sum
          |      return call function Now()
          |    }
          |  }
          |}
          |""".stripMargin
      RiddlLib.parseString(callModel) match
        case RiddlResult.Success(root0) =>
          val json1 = RiddlLib.root2Json(root0)
          // JsonifierPass emits the call discriminator and function ref...
          json1 must include("\"call\"")
          json1 must include("Add")
          // ...and JsonAstBuilder rebuilds it so the JSON is a fixed point.
          RiddlLib.parseJson(json1) match
            case RiddlResult.Success(root1) => RiddlLib.root2Json(root1) mustBe json1
            case RiddlResult.Failure(errors) =>
              fail(s"parseJson of the call JSON failed: $errors")
          end match
        case RiddlResult.Failure(errors) =>
          fail(s"parse of the call model failed: $errors")
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
          |        on command Cmd is { do "c" }
          |        on event Evt is { do "e" }
          |        on activate is { do "a" }
          |        on passivate is { do "p" }
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
          |        foreach p in batch { do "process" }
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

    "round-trip a nested boolean expression (A28) losslessly" in {
      val bModel =
        """domain BD is {
          |  context c is {
          |    handler h is {
          |      on init is {
          |        let x = (a or b) and not c
          |        let y = true
          |      }
          |    }
          |  }
          |}
          |""".stripMargin
      RiddlLib.parseString(bModel) match
        case RiddlResult.Success(root0) =>
          val json1 = RiddlLib.root2Json(root0)
          // JsonifierPass emits the boolean-expression discriminators...
          json1 must include("\"logical\"")
          json1 must include("\"not\"")
          json1 must include("\"boolLiteral\"")
          // ...and JsonAstBuilder rebuilds them so the JSON is a fixed point.
          RiddlLib.parseJson(json1) match
            case RiddlResult.Success(root1) => RiddlLib.root2Json(root1) mustBe json1
            case RiddlResult.Failure(errors) =>
              fail(s"parseJson of the boolean-expression JSON failed: $errors")
          end match
        case RiddlResult.Failure(errors) =>
          fail(s"parse of the boolean-expression model failed: $errors")
      end match
    }

    "round-trip boolean-expression when/require/invariant conditions (A28 s2) losslessly" in {
      val cModel =
        """domain CD is {
          |  context c is {
          |    entity e is {
          |      invariant inv is x > y
          |      handler h is {
          |        on init is {
          |          require count == total
          |          when a > b and not c then error "boom" end
          |        }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin
      RiddlLib.parseString(cModel) match
        case RiddlResult.Success(root0) =>
          val json1 = RiddlLib.root2Json(root0)
          // JsonifierPass emits the structured `expression` field for widened conditions...
          json1 must include("\"expression\"")
          json1 must include("\"comparison\"")
          json1 must include("\"logical\"")
          json1 must include("\"not\"")
          RiddlLib.parseJson(json1) match
            case RiddlResult.Success(root1) =>
              // Fixed point proves the AST<->JSON mapping is lossless for the widened conditions.
              RiddlLib.root2Json(root1) mustBe json1
              // And the rebuilt AST preserves the M3 `when a > b and not c` structure.
              val ws = Finder(root1.contents).recursiveFindByType[WhenStatement].head
              ws.condition match
                case LogicalExpression(_, LogicalOperator.And, left, right) =>
                  left mustBe a[ComparisonExpression]
                  right mustBe a[NotExpression]
                case other => fail(s"expected And when condition, got $other")
              val rs = Finder(root1.contents).recursiveFindByType[RequireStatement].head
              rs.condition mustBe a[ComparisonExpression]
              val inv = Finder(root1.contents).recursiveFindByType[Invariant].head
              inv.condition.map(_.getClass.getSimpleName) mustBe Some("ComparisonExpression")
            case RiddlResult.Failure(errors) =>
              fail(s"parseJson of the boolean-condition JSON failed: $errors")
          end match
        case RiddlResult.Failure(errors) =>
          fail(s"parse of the boolean-condition model failed: $errors")
      end match
    }

    "round-trip a bare boolean value-reference `when` condition (A17) losslessly" in {
      val cModel =
        """domain WR is {
          |  context c is {
          |    handler h is {
          |      on init is {
          |        when flag then error "one" end
          |        when order.isPaid then error "two" end
          |      }
          |    }
          |  }
          |}
          |""".stripMargin
      RiddlLib.parseString(cModel) match
        case RiddlResult.Success(root0) =>
          val json1 = RiddlLib.root2Json(root0)
          // A17: a bare boolean ValueRef condition serializes via the structured `expression` field.
          json1 must include("\"expression\"")
          json1 must include("\"valueRef\"")
          RiddlLib.parseJson(json1) match
            case RiddlResult.Success(root1) =>
              RiddlLib.root2Json(root1) mustBe json1 // fixed point => lossless
              val whens = Finder(root1.contents).recursiveFindByType[WhenStatement]
              whens.size mustBe 2
              whens.head.condition match
                case vr: ValueRef => vr.path.value mustBe Seq("flag")
                case other        => fail(s"expected a ValueRef condition, got $other")
              whens(1).condition match
                case vr: ValueRef => vr.path.value mustBe Seq("order", "isPaid")
                case other        => fail(s"expected a dotted ValueRef condition, got $other")
            case RiddlResult.Failure(errors) =>
              fail(s"parseJson of the value-ref-condition JSON failed: $errors")
          end match
        case RiddlResult.Failure(errors) =>
          fail(s"parse of the value-ref-condition model failed: $errors")
      end match
    }

    "round-trip widened operands: send/morph/set/let(prompt)/yield constructors (A54) losslessly" in {
      val wModel =
        """domain WD is {
          |  context c is {
          |    type Qty is Integer
          |    record Line is { sku: String, qty: Qty }
          |    command Add is { sku: String }
          |    event Added is { sku: String }
          |    result Res is { ok: String }
          |    query Ask yields result Res is { q: String }
          |    outlet outp is event Added
          |    entity E is {
          |      record Data is { line: Line }
          |      state S of record Data
          |      handler H is {
          |        on command Add is {
          |          let note = prompt("summarize the addition")
          |          set field E.S.line to record Line(sku = "x", qty = "1")
          |          send event Added(sku = "x") to outlet c.outp
          |          morph entity E to state E.S with record Data(line = record Line(sku = "y", qty = "2"))
          |        }
          |        on query Ask is {
          |          yield result Res(ok = "done")
          |        }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin
      RiddlLib.parseString(wModel) match
        case RiddlResult.Success(root0) =>
          val json1 = RiddlLib.root2Json(root0)
          json1 must include("\"prompt\"")
          json1 must include("\"constructor\"")
          RiddlLib.parseJson(json1) match
            case RiddlResult.Success(root1) => RiddlLib.root2Json(root1) mustBe json1
            case RiddlResult.Failure(errors) =>
              fail(s"parseJson of the widened-operand JSON failed: $errors")
          end match
        case RiddlResult.Failure(errors) =>
          fail(s"parse of the widened-operand model failed: $errors")
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

    "round-trip a state-scoped invariant (A18) losslessly" in {
      val siModel =
        """domain d is { context c is { entity e is {
          |  type Data is { x: Integer }
          |  state S of record d.c.e.Data is {
          |    invariant nonNegative is "x must be >= 0"
          |    handler H is { on other is { do "a" } }
          |  }
          |}}}
          |""".stripMargin
      RiddlLib.parseString(siModel) match
        case RiddlResult.Success(root0) =>
          val json1 = RiddlLib.root2Json(root0)
          // JsonifierPass emits the state's invariants array...
          json1 must include("\"invariants\"")
          json1 must include("\"nonNegative\"")
          json1 must include("x must be >= 0")
          RiddlLib.parseJson(json1) match
            case RiddlResult.Success(root1) =>
              // Fixed point: JsonAstBuilder rebuilds the state's invariant, else json2 would drop it.
              RiddlLib.root2Json(root1) mustBe json1
              val e = Finder(root1.contents).recursiveFindByType[Entity].head
              val s = e.states.find(_.id.value == "S").get
              s.invariants.map(_.id.value) mustBe Seq("nonNegative")
              e.invariants mustBe empty
            case RiddlResult.Failure(errors) =>
              fail(s"parseJson of the state-invariant JSON failed: $errors")
          end match
        case RiddlResult.Failure(errors) =>
          fail(s"parse of the state-invariant model failed: $errors")
      end match
    }

    "round-trip versions in BOTH component forms (A53) losslessly" in {
      // A53: a version component is EITHER a name OR a natural number. The `numeric` discriminator
      // must survive AST -> JSON -> AST at every permitted scope, else a numeric component would
      // come back named (or vice versa) and the fixed point would break.
      val vModel =
        """version Jellyfish
          |domain d is {
          |  version Garibaldi
          |  context c is {
          |    version 4
          |    entity e is {
          |      version 3
          |      type Data is { x: Integer }
          |      state S of record d.c.e.Data is { handler H is { on other is { do "a" } } }
          |    }
          |  }
          |}
          |module m is { version 9 }
          |""".stripMargin
      RiddlLib.parseString(vModel) match
        case RiddlResult.Success(root0) =>
          val json1 = RiddlLib.root2Json(root0)
          json1 must include("\"version\"")
          json1 must include("Jellyfish")
          json1 must include("Garibaldi")
          json1 must include("\"numeric\"")
          RiddlLib.parseJson(json1) match
            case RiddlResult.Success(root1) =>
              RiddlLib.root2Json(root1) mustBe json1
              root1.version.map(_.component) mustBe Some("Jellyfish")
              root1.version.flatMap(_.number) mustBe None
              val finder = Finder(root1.contents)
              val d = finder.recursiveFindByType[Domain].head
              d.version.map(_.component) mustBe Some("Garibaldi")
              d.version.flatMap(_.number) mustBe None
              val c = finder.recursiveFindByType[Context].head
              c.version.flatMap(_.number) mustBe Some(4L)
              val e = finder.recursiveFindByType[Entity].head
              e.version.flatMap(_.number) mustBe Some(3L)
              val m = finder.recursiveFindByType[Module].head
              m.version.flatMap(_.number) mustBe Some(9L)
            case RiddlResult.Failure(errors) =>
              fail(s"parseJson of the version JSON failed: $errors")
          end match
        case RiddlResult.Failure(errors) =>
          fail(s"parse of the version model failed: $errors")
      end match
    }

    "round-trip copyrights at every permitted scope (A47) losslessly" in {
      // A47: `copyright` is a NAMED leaf at root/module/domain and all six processors, and
      // `version` was widened to the same set. Both must survive AST -> JSON -> AST at every
      // scope, with the notice carried verbatim, else the fixed point would break.
      val cModel =
        """copyright Root is "© 2026 Ossum Inc."
          |version Jellyfish
          |domain d is {
          |  copyright Domain is "© 2026 Ossum Inc. (domain)"
          |  context c is {
          |    copyright Context is "© 2026 Ossum Inc. (context)"
          |    command Ping(at: TimeStamp)
          |    entity e is {
          |      copyright Entity is "© 2026 Ossum Inc. (entity)"
          |      version 3
          |      type Data is { x: Integer }
          |      state S of record d.c.e.Data is { handler H is { on other is { do "a" } } }
          |    }
          |    repository repo is {
          |      copyright Repository is "© 2026 Third Party Ltd."
          |      version 2
          |    }
          |    projector proj is {
          |      copyright Projector is "© 2026 Ossum Inc. (projector)"
          |      version 1
          |    }
          |    processor src as source is {
          |      copyright Streamlet is "© 2026 Ossum Inc. (streamlet)"
          |      version 5
          |      outlet Out is type d.c.Ping
          |    }
          |    adaptor ad to context d.c is {
          |      copyright Adaptor is "© 1998 Legacy Systems Inc."
          |      version 7
          |    }
          |  }
          |}
          |module m is { copyright Module is "© 2026 Ossum Inc. (module)" }
          |""".stripMargin
      RiddlLib.parseString(cModel) match
        case RiddlResult.Success(root0) =>
          val json1 = RiddlLib.root2Json(root0)
          json1 must include("\"copyright\"")
          json1 must include("© 1998 Legacy Systems Inc.")
          RiddlLib.parseJson(json1) match
            case RiddlResult.Success(root1) =>
              RiddlLib.root2Json(root1) mustBe json1
              root1.copyright.map(_.id.value) mustBe Some("Root")
              root1.copyright.map(_.notice) mustBe Some("© 2026 Ossum Inc.")
              val finder = Finder(root1.contents)
              finder.recursiveFindByType[Domain].head.copyright.map(_.notice) mustBe
                Some("© 2026 Ossum Inc. (domain)")
              finder.recursiveFindByType[Context].head.copyright.isDefined mustBe true
              finder.recursiveFindByType[Entity].head.copyright.isDefined mustBe true
              finder.recursiveFindByType[Repository].head.copyright.isDefined mustBe true
              finder.recursiveFindByType[Projector].head.copyright.isDefined mustBe true
              finder.recursiveFindByType[Streamlet].head.copyright.isDefined mustBe true
              val ad = finder.recursiveFindByType[Adaptor].head
              ad.copyright.map(_.notice) mustBe Some("© 1998 Legacy Systems Inc.")
              ad.version.flatMap(_.number) mustBe Some(7L)
              finder.recursiveFindByType[Module].head.copyright.isDefined mustBe true
            case RiddlResult.Failure(errors) =>
              fail(s"parseJson of the copyright JSON failed: $errors")
          end match
        case RiddlResult.Failure(errors) =>
          fail(s"parse of the copyright model failed: $errors")
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

    "round-trip a structured match (subject + patterns + guard) losslessly (A29)" in {
      val matchModel =
        """domain d is { context c is { handler h is { on init is {
          |  match order.status {
          |    case Shipped { error "s" }
          |    case == Cancelled { error "c" }
          |    case > MaxRetries when count > MaxRetries { error "r" }
          |    default { error "d" }
          |  }
          |  match "legacy" { case "x" { error "x" } }
          |}}}}
          |""".stripMargin
      RiddlLib.parseString(matchModel) match
        case RiddlResult.Success(root0) =>
          val json1 = RiddlLib.root2Json(root0)
          json1 must include("\"subject\"")
          json1 must include("guard")
          json1 must include("comparison")
          // fixed point proves JsonifierPass + JsonAstBuilder round-trip the structured match
          RiddlLib.parseJson(json1) match
            case RiddlResult.Success(root1) =>
              RiddlLib.root2Json(root1) mustBe json1
              val ms = Finder(root1).recursiveFindByType[MatchStatement].find(_.cases.size == 3).get
              ms.expression.asInstanceOf[ValueRef].path.value mustBe Seq("order", "status")
              ms.cases(0).pattern.asInstanceOf[TypePattern].typeRef.pathId.value mustBe Seq(
                "Shipped"
              )
              ms.cases(2).guard mustBe defined
            case RiddlResult.Failure(errors) =>
              fail(s"parseJson of the match JSON failed: $errors")
          end match
        case RiddlResult.Failure(errors) =>
          fail(s"parse of the match model failed: $errors")
      end match
    }

    // `Anything` replaces `Abstract`: the JSON discriminator carries the class name, so output is
    // always `"Anything"`. The deprecated `Abstract` input spelling parses to the same node and so
    // produces IDENTICAL JSON; `"Abstract"` is still accepted as a JSON input kind.
    "round-trip `Anything` (and the deprecated `Abstract` spelling) losslessly" in {
      def jsonOf(typeExpr: String): String =
        RiddlLib.parseString(s"domain d is { type Whatever is $typeExpr }\n") match
          case RiddlResult.Success(root) => RiddlLib.root2Json(root)
          case RiddlResult.Failure(errors) =>
            fail(s"parse of the `$typeExpr` model failed: $errors")

      val json1 = jsonOf("Anything")
      json1 must include("\"Anything\"")
      json1 mustNot include("\"Abstract\"")
      jsonOf("Abstract") mustBe json1

      RiddlLib.parseJson(json1) match
        case RiddlResult.Success(root1) =>
          RiddlLib.root2Json(root1) mustBe json1
          val typ = Finder(root1).recursiveFindByType[Type].find(_.id.value == "Whatever").get
          typ.typEx mustBe a[Anything]
        case RiddlResult.Failure(errors) =>
          fail(s"parseJson of the Anything JSON failed: $errors")
      end match
      // The deprecated JSON input kind still builds an `Anything` and normalizes on output.
      RiddlLib.parseJson(json1.replace("\"Anything\"", "\"Abstract\"")) match
        case RiddlResult.Success(root2) => RiddlLib.root2Json(root2) mustBe json1
        case RiddlResult.Failure(errors) =>
          fail(s"parseJson of the deprecated `Abstract` JSON kind failed: $errors")
      end match
    }

    /** #60: the predefined `Riddl` standard module is seeded into the SYMBOL TABLE by
      * `SymbolsPass`, never into the user's `Root.contents`. A model that never mentions the
      * terminators must therefore serialize to exactly the JSON it did before the module existed —
      * proved here by requiring the JSON of the freshly-parsed root and the JSON of the root after
      * the standard passes (which do the seeding) to be identical, and by requiring neither to
      * carry any name from the standard module.
      */
    "be untouched by the predefined standard module (non-injection)" in {
      val oblivious =
        """domain Simple is {
          |  type Thing is String
          |  context Only is {
          |    processor Producer as source is { outlet out is type Simple.Thing }
          |    processor Consumer as sink is { inlet in is type Simple.Thing }
          |    connector Wire is { from outlet Producer.out to inlet Consumer.in }
          |  }
          |}
          |""".stripMargin
      RiddlLib.parseString(oblivious) match
        case RiddlResult.Success(root0) =>
          val beforePasses = RiddlLib.root2Json(root0)
          val passed = Pass.runStandardPasses(root0)
          val afterPasses = RiddlLib.root2Json(passed.root.asInstanceOf[Root])
          afterPasses mustBe beforePasses
          beforePasses mustNot include("BottomlessPit")
          beforePasses mustNot include("ForeverEmpty")
          beforePasses mustNot include("Drain")
          beforePasses mustNot include("\"Riddl\"")
        case RiddlResult.Failure(errors) =>
          fail(s"parse of the oblivious model failed: $errors")
      end match
    }
  }
}
