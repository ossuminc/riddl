/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes

import com.ossuminc.riddl.language.AST.{
  Nebula,
  OnActivationClause,
  OnEventClause,
  OnMessageClause,
  OnPassivationClause,
  Root
}
import com.ossuminc.riddl.language.{Contents, *}
import com.ossuminc.riddl.language.bast.BASTReader
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.utils.{pc, ec, Await, URL}
import org.scalatest.wordspec.AnyWordSpec

import java.nio.file.{Files, Paths}
import scala.concurrent.duration.*

/** Round-trip tests for BAST serialization/deserialization
  *
  * These tests verify that: RIDDL text -> AST -> BAST binary -> AST produces an equivalent AST
  *
  * This is the CRITICAL test for Phase 2 completion.
  */
class BASTRoundTripTest extends AnyWordSpec {

  "BAST Round Trip" should {

    "serialize and deserialize simple domain" in {
      val riddlSource = """domain TestDomain is {
                          |  type MyType is String
                          |} with { briefly "A test domain" }
                          |""".stripMargin

      val input = RiddlParserInput(riddlSource, "test-simple")
      val parseResult = TopLevelParser.parseInput(input, true)

      parseResult match {
        case Right(originalRoot: Root) =>
          println(s"\n=== Round Trip Test: simple domain ===")

          // Serialize AST -> BAST binary
          val passInput = PassInput(originalRoot)
          val writerResult = Pass.runThesePasses(passInput, Seq(BASTWriterPass.creator()))
          val output = writerResult.outputOf[BASTOutput](BASTWriterPass.name).get

          println(f"BAST written: ${output.bytes.length}%,d bytes (${output.nodeCount}%,d nodes)")

          // Deserialize BAST binary -> AST
          BASTReader.read(output.bytes) match {
            case Right(reconstructedNebula) =>
              println(s"BAST read: Nebula reconstructed")

              // Compare original and reconstructed
              val areEqual = compareRoots(originalRoot, reconstructedNebula)

              if areEqual then
                println("[OK] Round trip successful: Original AST == Reconstructed AST")
              else println("[FAIL] Round trip FAILED: ASTs differ")
              end if

              assert(areEqual, "Round trip test failed: ASTs are not equivalent")

            case Left(errors) =>
              fail(s"Deserialization failed: ${errors.format}")
          }

        case Left(messages) =>
          fail(s"Parse failed: ${messages.format}")
      }
    }

    "serialize and deserialize a domain-scoped repository" in {
      // RIDDL is reflective across BAST too: a repository defined directly in a
      // domain must survive AST -> BAST -> AST at the same scope.
      val riddlSource = """domain d is {
                          |  context a is { event AEvent is { x: String } }
                          |  context b is { event BEvent is { y: String } }
                          |  repository synth is {
                          |    handler h is {
                          |      on event a.AEvent { do "record from a" }
                          |      on event b.BEvent { do "record from b" }
                          |    }
                          |  }
                          |}
                          |""".stripMargin
      val input = RiddlParserInput(riddlSource, "test-domain-repo")
      TopLevelParser.parseInput(input, true) match {
        case Right(originalRoot: Root) =>
          val writerResult =
            Pass.runThesePasses(PassInput(originalRoot), Seq(BASTWriterPass.creator()))
          val output = writerResult.outputOf[BASTOutput](BASTWriterPass.name).get
          BASTReader.read(output.bytes) match {
            case Right(nebula) =>
              // Full structural round-trip.
              assert(
                compareRoots(originalRoot, nebula),
                "domain-scoped repository round trip failed: ASTs are not equivalent"
              )
              // And specifically: the repository is a direct child of the domain,
              // not dropped and not relocated into a context.
              val domain =
                nebula.domains
                  .find(_.id.value == "d")
                  .getOrElse(fail("domain d missing after BAST read"))
              assert(
                domain.repositories.map(_.id.value) == Seq("synth"),
                "repository is not at domain scope after BAST round trip"
              )
              assert(
                domain.contexts.forall(_.repositories.isEmpty),
                "repository leaked into a context after BAST round trip"
              )
            case Left(errors) =>
              fail(s"Deserialization failed: ${errors.format}")
          }
        case Left(messages) =>
          fail(s"Parse failed: ${messages.format}")
      }
    }

    "serialize and deserialize the `initial` marker on states and handlers" in {
      val riddlSource =
        """domain d is { context c is { entity e is {
          |  type Data is { x: Integer }
          |  state First of record d.c.e.Data is { handler H is { on other is { do "a" } } }
          |  initial state Second of record d.c.e.Data is {
          |    handler H1 is { on other is { do "b" } }
          |    initial handler H2 is { on other is { do "c" } }
          |  }
          |}}}
          |""".stripMargin
      val input = RiddlParserInput(riddlSource, "test-initial")
      TopLevelParser.parseInput(input, true) match {
        case Right(originalRoot: Root) =>
          val writerResult =
            Pass.runThesePasses(PassInput(originalRoot), Seq(BASTWriterPass.creator()))
          val output = writerResult.outputOf[BASTOutput](BASTWriterPass.name).get
          BASTReader.read(output.bytes) match {
            case Right(nebula) =>
              assert(compareRoots(originalRoot, nebula), "initial-marker round trip: ASTs differ")
              import com.ossuminc.riddl.language.Finder
              import com.ossuminc.riddl.language.AST.Entity
              val e = Finder(nebula.contents).recursiveFindByType[Entity].head
              assert(
                e.states.find(_.id.value == "Second").get.isInitial,
                "state initial lost in BAST"
              )
              assert(
                !e.states.find(_.id.value == "First").get.isInitial,
                "non-initial state flipped"
              )
              val second = e.states.find(_.id.value == "Second").get
              assert(
                second.handlers.find(_.id.value == "H2").get.isInitial,
                "handler initial lost in BAST"
              )
              assert(
                !second.handlers.find(_.id.value == "H1").get.isInitial,
                "non-initial handler flipped"
              )
            case Left(errors) => fail(s"Deserialization failed: ${errors.format}")
          }
        case Left(messages) => fail(s"Parse failed: ${messages.format}")
      }
    }

    "serialize and deserialize a `yield` statement (A22)" in {
      // The `yield` statement reuses BAST subtag 15 (formerly `reply`); verify it round-trips.
      val riddlSource =
        """domain d is { context c is {
          |  result Res is { ok: Boolean }
          |  query Ask yields result Res is { q: Integer }
          |  entity e is {
          |    record F is { q: Integer }
          |    state S of record e.F
          |    handler H is {
          |      on init { set field e.F.q to "0" }
          |      on query Ask { yield result Res }
          |    }
          |  }
          |}}
          |""".stripMargin
      val input = RiddlParserInput(riddlSource, "test-yield")
      TopLevelParser.parseInput(input, true) match {
        case Right(originalRoot: Root) =>
          val writerResult =
            Pass.runThesePasses(PassInput(originalRoot), Seq(BASTWriterPass.creator()))
          val output = writerResult.outputOf[BASTOutput](BASTWriterPass.name).get
          BASTReader.read(output.bytes) match {
            case Right(nebula) =>
              assert(compareRoots(originalRoot, nebula), "yield-statement round trip: ASTs differ")
              val ys = Finder(nebula.contents).recursiveFindByType[AST.YieldStatement]
              assert(ys.size == 1, s"expected one YieldStatement, found ${ys.size}")
              assert(ys.head.msg.operandPathId.value.last == "Res", "yield target lost in BAST")
            case Left(errors) => fail(s"Deserialization failed: ${errors.format}")
          }
        case Left(messages) => fail(s"Parse failed: ${messages.format}")
      }
    }

    "serialize and deserialize a `foreach` statement (A25)" in {
      // A25 uses new BAST subtag 16 (FORMAT_REVISION 13). Verify both a field-ref collection and a
      // let-local collection round-trip, and the nested body survives.
      val riddlSource =
        """domain d is { context c is {
          |  type Order is record { id: Integer }
          |  type OrderList is many Order
          |  type Batch is command { orders: OrderList }
          |  handler h is {
          |    on command Batch {
          |      let batch: OrderList = "orders"
          |      foreach o in field Batch.orders {
          |        foreach p in batch { do "process" }
          |      }
          |    }
          |  }
          |}}
          |""".stripMargin
      val input = RiddlParserInput(riddlSource, "test-foreach")
      TopLevelParser.parseInput(input, true) match {
        case Right(originalRoot: Root) =>
          val writerResult =
            Pass.runThesePasses(PassInput(originalRoot), Seq(BASTWriterPass.creator()))
          val output = writerResult.outputOf[BASTOutput](BASTWriterPass.name).get
          BASTReader.read(output.bytes) match {
            case Right(nebula) =>
              assert(
                compareRoots(originalRoot, nebula),
                "foreach-statement round trip: ASTs differ"
              )
              import com.ossuminc.riddl.language.Finder
              import com.ossuminc.riddl.language.AST.{FieldRef, ForeachStatement, Identifier}
              val fes = Finder(nebula.contents).recursiveFindByType[ForeachStatement]
              assert(fes.size == 2, s"expected two ForeachStatements, found ${fes.size}")
              val outer = fes.find(_.element.value == "o").getOrElse(fail("outer foreach lost"))
              outer.collection match
                case fr: FieldRef => assert(fr.pathId.value == Seq("Batch", "orders"))
                case other        => fail(s"expected FieldRef, got $other")
              val inner = fes.find(_.element.value == "p").getOrElse(fail("inner foreach lost"))
              inner.collection match
                case id: Identifier => assert(id.value == "batch")
                case other          => fail(s"expected Identifier, got $other")
            case Left(errors) => fail(s"Deserialization failed: ${errors.format}")
          }
        case Left(messages) => fail(s"Parse failed: ${messages.format}")
      }
    }

    "serialize and deserialize `put` and `return` with value expressions (A45/A54/A57)" in {
      // A45/A57 use BAST subtags 17/18 (FORMAT_REVISION 14). Verify a return with a record
      // constructor and a put reading from a UI input round-trip losslessly.
      val riddlSource =
        """domain d is {
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
          |      on command Refresh {
          |        put get from input Entry to output Panel
          |      }
          |    }
          |  }
          |}
          |""".stripMargin
      val input = RiddlParserInput(riddlSource, "test-put-return")
      TopLevelParser.parseInput(input, true) match {
        case Right(originalRoot: Root) =>
          val writerResult =
            Pass.runThesePasses(PassInput(originalRoot), Seq(BASTWriterPass.creator()))
          val output = writerResult.outputOf[BASTOutput](BASTWriterPass.name).get
          BASTReader.read(output.bytes) match {
            case Right(nebula) =>
              assert(compareRoots(originalRoot, nebula), "put/return round trip: ASTs differ")
              import com.ossuminc.riddl.language.Finder
              import com.ossuminc.riddl.language.AST.*
              val rets = Finder(nebula.contents).recursiveFindByType[ReturnStatement]
              assert(rets.size == 1, s"expected one ReturnStatement, found ${rets.size}")
              rets.head.value match
                case c: Constructor =>
                  assert(c.ref.isInstanceOf[RecordRef])
                  assert(c.args.size == 1)
                  assert(c.args.head.name.map(_.value) == Some("total"))
                case other => fail(s"expected Constructor, got $other")
              val puts = Finder(nebula.contents).recursiveFindByType[PutStatement]
              assert(puts.size == 1, s"expected one PutStatement, found ${puts.size}")
              assert(puts.head.output.pathId.value == Seq("Panel"))
              puts.head.value match
                case gv: GetValue =>
                  gv.source match
                    case ir: InputRef => assert(ir.pathId.value == Seq("Entry"))
                    case other        => fail(s"expected InputRef, got $other")
                case other => fail(s"expected GetValue, got $other")
            case Left(errors) => fail(s"Deserialization failed: ${errors.format}")
          }
        case Left(messages) => fail(s"Parse failed: ${messages.format}")
      }
    }

    "serialize and deserialize widened operands: send/morph/set/let(prompt)/yield (A54)" in {
      // A54 widens set/let values, send/tell/yield messages, and morph values. Verify each widened
      // form (including the `prompt(...)` value and inline constructors) round-trips at rev 14.
      val riddlSource =
        """domain d is {
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
          |        on command Add {
          |          let note = prompt("summarize the addition")
          |          set field E.S.line to record Line(sku = "x", qty = "1")
          |          send event Added(sku = "x") to outlet c.outp
          |          morph entity E to state E.S with record Data(line = record Line(sku = "y", qty = "2"))
          |        }
          |        on query Ask {
          |          yield result Res(ok = "done")
          |        }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin
      val input = RiddlParserInput(riddlSource, "test-widened-operands")
      TopLevelParser.parseInput(input, true) match {
        case Right(originalRoot: Root) =>
          val writerResult =
            Pass.runThesePasses(PassInput(originalRoot), Seq(BASTWriterPass.creator()))
          val output = writerResult.outputOf[BASTOutput](BASTWriterPass.name).get
          BASTReader.read(output.bytes) match {
            case Right(nebula) =>
              assert(compareRoots(originalRoot, nebula), "widened operands round trip: ASTs differ")
              import com.ossuminc.riddl.language.Finder
              import com.ossuminc.riddl.language.AST.*
              val lets = Finder(nebula.contents).recursiveFindByType[LetStatement]
              lets.find(_.identifier.value == "note").map(_.expression) match
                case Some(pv: PromptValue) => assert(pv.prompt.s == "summarize the addition")
                case other                 => fail(s"expected a PromptValue let, got $other")
              val sets = Finder(nebula.contents).recursiveFindByType[SetStatement]
              sets.head.value match
                case c: Constructor => assert(c.ref.isInstanceOf[RecordRef])
                case other          => fail(s"expected Constructor set value, got $other")
              val sends = Finder(nebula.contents).recursiveFindByType[SendStatement]
              sends.head.msg match
                case c: Constructor => assert(c.ref.isInstanceOf[EventRef])
                case other          => fail(s"expected Constructor send msg, got $other")
              val morphs = Finder(nebula.contents).recursiveFindByType[MorphStatement]
              morphs.head.value match
                case c: Constructor =>
                  assert(c.ref.isInstanceOf[RecordRef])
                  assert(c.args.head.value.isInstanceOf[Constructor])
                case other => fail(s"expected Constructor morph value, got $other")
              val yields = Finder(nebula.contents).recursiveFindByType[YieldStatement]
              yields.head.msg match
                case c: Constructor => assert(c.ref.isInstanceOf[ResultRef])
                case other          => fail(s"expected Constructor yield msg, got $other")
            case Left(errors) => fail(s"Deserialization failed: ${errors.format}")
          }
        case Left(messages) => fail(s"Parse failed: ${messages.format}")
      }
    }

    "serialize and deserialize named-type requires/returns on a function and saga (A9)" in {
      val riddlSource =
        """domain d is { context c is {
          |  record Args is { a: Integer }
          |  result Res is { ok: Boolean }
          |  command Go is { x: Integer }
          |  command UndoGo is { x: Integer }
          |  entity e is { sink t is { inlet in is command Go } }
          |  function f is { requires record Args returns result Res ??? }
          |  function g is { requires { b: Boolean } returns { r: Integer } ??? }
          |  saga s is {
          |    requires record Args
          |    returns result Res
          |    step One is { send command Go to outlet d.c.e.t.in }
          |      reverted by { send command UndoGo to outlet d.c.e.t.in }
          |    step Two is { do "do" } reverted by { do "undo" }
          |  }
          |}}
          |""".stripMargin
      val input = RiddlParserInput(riddlSource, "test-requires-returns")
      TopLevelParser.parseInput(input, true) match {
        case Right(originalRoot: Root) =>
          val writerResult =
            Pass.runThesePasses(PassInput(originalRoot), Seq(BASTWriterPass.creator()))
          val output = writerResult.outputOf[BASTOutput](BASTWriterPass.name).get
          BASTReader.read(output.bytes) match {
            case Right(nebula) =>
              assert(compareRoots(originalRoot, nebula), "requires/returns round trip: ASTs differ")
              import com.ossuminc.riddl.language.Finder
              import com.ossuminc.riddl.language.AST.{Aggregation, Function, TypeRef}
              val funcs = Finder(nebula.contents).recursiveFindByType[Function]
              val f = funcs.find(_.id.value == "f").get
              assert(f.input.get.isInstanceOf[TypeRef], "function ref requires lost in BAST")
              assert(
                f.input.get.asInstanceOf[TypeRef].keyword == "record",
                "ref keyword lost in BAST"
              )
              val g = funcs.find(_.id.value == "g").get
              assert(g.input.get.isInstanceOf[Aggregation], "inline requires flipped in BAST")
            case Left(errors) => fail(s"Deserialization failed: ${errors.format}")
          }
        case Left(messages) => fail(s"Parse failed: ${messages.format}")
      }
    }

    "serialize and deserialize a domain-scoped connector" in {
      // Reflective across BAST: a connector defined directly in a domain (cross-context)
      // must survive AST -> BAST -> AST at the same scope.
      val riddlSource =
        """domain d is {
          |  type T is { x: Integer }
          |  context a is { source src is { outlet out is type d.T } }
          |  context b is { sink snk is { inlet in is type d.T } }
          |  connector c is {
          |    from outlet d.a.src.out to inlet d.b.snk.in
          |  } with { option persistent }
          |}
          |""".stripMargin
      val input = RiddlParserInput(riddlSource, "test-domain-connector")
      TopLevelParser.parseInput(input, true) match {
        case Right(originalRoot: Root) =>
          val writerResult =
            Pass.runThesePasses(PassInput(originalRoot), Seq(BASTWriterPass.creator()))
          val output = writerResult.outputOf[BASTOutput](BASTWriterPass.name).get
          BASTReader.read(output.bytes) match {
            case Right(nebula) =>
              assert(
                compareRoots(originalRoot, nebula),
                "domain-scoped connector round trip failed: ASTs are not equivalent"
              )
              val domain =
                nebula.domains
                  .find(_.id.value == "d")
                  .getOrElse(fail("domain d missing after BAST"))
              assert(
                domain.connectors.map(_.id.value) == Seq("c"),
                "connector is not at domain scope after BAST round trip"
              )
              assert(
                domain.contexts.forall(_.connectors.isEmpty),
                "connector leaked into a context after BAST round trip"
              )
            case Left(errors) => fail(s"Deserialization failed: ${errors.format}")
          }
        case Left(messages) => fail(s"Parse failed: ${messages.format}")
      }
    }

    "serialize and deserialize the 2.0 handler-kind clauses" in {
      // Reflective across BAST too: on event / on activate / on passivate must survive
      // AST -> BAST -> AST as the same node kinds (new node tags 4/5/6, FORMAT_REVISION 7).
      val riddlSource =
        """domain d is {
          |  context c is {
          |    entity e is {
          |      command Cmd is { g: Integer }
          |      event Evt is { h: Integer }
          |      handler hh is {
          |        on command Cmd { do "handle" }
          |        on event Evt { do "note" }
          |        on activate { do "rehydrate" }
          |        on passivate { do "evict" }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin
      val input = RiddlParserInput(riddlSource, "test-handler-kinds")
      TopLevelParser.parseInput(input, true) match {
        case Right(originalRoot: Root) =>
          val writerResult =
            Pass.runThesePasses(PassInput(originalRoot), Seq(BASTWriterPass.creator()))
          val output = writerResult.outputOf[BASTOutput](BASTWriterPass.name).get
          BASTReader.read(output.bytes) match {
            case Right(nebula) =>
              assert(
                compareRoots(originalRoot, nebula),
                "handler-kinds round trip failed: ASTs are not equivalent"
              )
              // And specifically: each new clause kind survives, not collapsed/dropped.
              import com.ossuminc.riddl.language.Finder
              val f = Finder(nebula.contents)
              assert(
                f.recursiveFindByType[OnEventClause].size == 1,
                "OnEventClause did not survive BAST round trip"
              )
              assert(
                f.recursiveFindByType[OnActivationClause].size == 1,
                "OnActivationClause did not survive BAST round trip"
              )
              assert(
                f.recursiveFindByType[OnPassivationClause].size == 1,
                "OnPassivationClause did not survive BAST round trip"
              )
              assert(
                f.recursiveFindByType[OnMessageClause].size == 1,
                "OnMessageClause (on command) did not survive BAST round trip"
              )
            case Left(errors) =>
              fail(s"Deserialization failed: ${errors.format}")
          }
        case Left(messages) =>
          fail(s"Parse failed: ${messages.format}")
      }
    }

    "serialize and deserialize context intention, ascribed shape, and ports (Task 15)" in {
      // Reflective across BAST: context intention, a processor's OPTIONAL ascribed shape
      // (Some AND None), the fixed Router-vs-Void distinction, and ports on a non-streamlet
      // processor must all survive AST -> BAST -> AST.
      val riddlSource =
        """domain d is {
          |  type T is String
          |  application context Orders as flow is {
          |    processor P as router is {
          |      inlet i1 is T
          |      inlet i2 is T
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
      val input = RiddlParserInput(riddlSource, "test-processor-model")
      TopLevelParser.parseInput(input, true) match {
        case Right(originalRoot: Root) =>
          val writerResult =
            Pass.runThesePasses(PassInput(originalRoot), Seq(BASTWriterPass.creator()))
          val output = writerResult.outputOf[BASTOutput](BASTWriterPass.name).get
          BASTReader.read(output.bytes) match {
            case Right(nebula) =>
              assert(
                compareRoots(originalRoot, nebula),
                "processor-model round trip failed: ASTs are not equivalent"
              )
              import com.ossuminc.riddl.language.Finder
              import com.ossuminc.riddl.language.AST.{Context, Entity, Intention, Streamlet}
              val f = Finder(nebula.contents)
              val ctx = f.recursiveFindByType[Context].head
              assert(ctx.intention == Some(Intention.Application), "context intention lost in BAST")
              assert(
                ctx.ascribedShape.map(_.keyword) == Some("flow"),
                "context ascribed shape lost in BAST"
              )
              val p = f.recursiveFindByType[Streamlet].find(_.id.value == "P").get
              assert(
                p.ascribedShape.map(_.keyword) == Some("router"),
                "Router shape not distinguished from Void in BAST"
              )
              assert(p.inlets.map(_.id.value) == Seq("i1", "i2"), "P inlets lost")
              assert(p.outlets.map(_.id.value) == Seq("o1", "o2"), "P outlets lost")
              val q = f.recursiveFindByType[Streamlet].find(_.id.value == "Q").get
              assert(q.ascribedShape.isEmpty, "None ascribed shape leaked a value in BAST")
              assert(q.inlets.map(_.id.value) == Seq("qi"), "Q inlet lost")
              val e = f.recursiveFindByType[Entity].head
              assert(e.inlets.map(_.id.value) == Seq("ei"), "entity port lost in BAST")
            case Left(errors) => fail(s"Deserialization failed: ${errors.format}")
          }
        case Left(messages) => fail(s"Parse failed: ${messages.format}")
      }
    }

    "serialize and deserialize dokn.riddl" in {
      val url = URL.fromCwdPath("language/input/dokn.riddl")
      val inputFuture = RiddlParserInput.fromURL(url, "dokn-test")

      val result = Await.result(
        inputFuture.map { input =>
          val parseResult = TopLevelParser.parseInput(input, true)
          parseResult match {
            case Right(originalRoot: Root) =>
              println(s"\n=== Round Trip Test: dokn.riddl ===")
              println(s"Original AST with ${originalRoot.contents.toSeq.size} items")

              val passInput = PassInput(originalRoot)
              val writerResult = Pass.runThesePasses(passInput, Seq(BASTWriterPass.creator()))
              val output = writerResult.outputOf[BASTOutput](BASTWriterPass.name).get

              println(
                f"BAST written: ${output.bytes.length}%,d bytes (${output.nodeCount}%,d nodes)"
              )

              BASTReader.read(output.bytes) match {
                case Right(reconstructedNebula) =>
                  println(
                    s"BAST read: Nebula with ${reconstructedNebula.contents.toSeq.size} items"
                  )
                  true
                case Left(errors) =>
                  println(s"[FAIL] Deserialization failed: ${errors.format}")
                  false
              }

            case Left(messages) =>
              println(s"Parse failed: ${messages.format}")
              false
          }
        },
        30.seconds
      )

      assert(result, "Round trip test failed for dokn.riddl")
    }

    "serialize and deserialize everything.riddl" in {
      val url = URL.fromCwdPath("language/input/everything.riddl")
      val inputFuture = RiddlParserInput.fromURL(url, "everything-test")

      val result = Await.result(
        inputFuture.map { input =>
          // Step 1: Parse RIDDL text -> AST
          val parseResult = TopLevelParser.parseInput(input, true)
          parseResult match {
            case Right(originalRoot: Root) =>
              println(s"\n=== Round Trip Test: everything.riddl ===")
              println(s"Original AST parsed successfully")

              // Step 2: Serialize AST -> BAST binary
              val passInput = PassInput(originalRoot)
              val writerResult = Pass.runThesePasses(passInput, Seq(BASTWriterPass.creator()))
              val output = writerResult.outputOf[BASTOutput](BASTWriterPass.name).get

              println(
                f"BAST written: ${output.bytes.length}%,d bytes (${output.nodeCount}%,d nodes)"
              )

              // Step 3: Deserialize BAST binary -> AST
              val bastReader = BASTReader(output.bytes)
              bastReader.enableDebugTracking()
              bastReader.read() match {
                case Right(reconstructedNebula) =>
                  println(s"BAST read: Nebula reconstructed")

                  // Step 4: Compare original and reconstructed
                  val areEqual = compareRoots(originalRoot, reconstructedNebula)

                  if areEqual then
                    println("[OK] Round trip successful: Original AST == Reconstructed AST")
                  else println("[FAIL] Round trip FAILED: ASTs differ")
                  end if

                  areEqual

                case Left(errors) =>
                  println(s"[FAIL] Deserialization failed: ${errors.format}")
                  false
              }

            case Left(messages) =>
              println(s"Parse failed: ${messages.format}")
              false
          }
        },
        30.seconds
      )

      assert(result, "Round trip test failed: ASTs are not equivalent")
    }
  }

  /** Compare Root (original) with Nebula (reconstructed) for deep structural equality
    *
    * Note: BASTWriter writes Root using NODE_NEBULA tag, so deserialization produces Nebula. This
    * is expected - we're comparing the CONTENT, not the container type.
    *
    * Uses DeepASTComparison to recursively verify all fields, identifiers, locations, and nested
    * content.
    */
  private def compareRoots(original: Root, reconstructed: Nebula): Boolean = {
    println(s"\n=== Deep Structural Comparison ===")
    println(s"Original: Root with ${original.contents.toSeq.size} top-level elements")
    println(s"Reconstructed: Nebula with ${reconstructed.contents.toSeq.size} top-level elements")

    // Perform deep comparison
    val results = DeepASTComparison.compareRootAndNebula(original, reconstructed)

    // Generate report
    val report = DeepASTComparison.report(results)
    println(report)

    // Check if all comparisons succeeded
    val allSucceeded = results.forall(_.isSuccess)

    if allSucceeded then
      println(
        "[OK] Complete structural reflectivity verified: AST -> BAST -> AST preserves all data"
      )
    else println("[FAIL] Structural differences detected - see failures above")
    end if

    allSucceeded
  }
}
