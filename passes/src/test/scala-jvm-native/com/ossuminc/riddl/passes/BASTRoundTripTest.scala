/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes

import com.ossuminc.riddl.language.AST.{
  Module,
  OnActivationClause,
  OnEventClause,
  OnMessageClause,
  OnMessageLikeClause,
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
            case Right(reconstructedModule) =>
              println(s"BAST read: Module reconstructed")

              // Compare original and reconstructed
              val areEqual = compareRoots(originalRoot, reconstructedModule)

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
            case Right(module) =>
              // Full structural round-trip.
              assert(
                compareRoots(originalRoot, module),
                "domain-scoped repository round trip failed: ASTs are not equivalent"
              )
              // And specifically: the repository is a direct child of the domain,
              // not dropped and not relocated into a context.
              val domain =
                module.domains
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

    // `Anything` (formerly spelled `Abstract`) keeps BAST tag TYPE_REF/99 — the Scala name
    // changed, the wire format did not, so FORMAT_REVISION does NOT move. Both spellings must
    // therefore produce byte-IDENTICAL BAST, since both parse to the same `Anything` node.
    "serialize and deserialize `Anything` with a wire format identical to `Abstract`" in {
      def bastOf(typeExpr: String): Array[Byte] =
        val src = s"domain d is { type Whatever is $typeExpr }\n"
        TopLevelParser.parseInput(RiddlParserInput(src, s"bast-$typeExpr"), true) match {
          case Right(root: Root) =>
            val writerResult =
              Pass.runThesePasses(PassInput(root), Seq(BASTWriterPass.creator()))
            writerResult.outputOf[BASTOutput](BASTWriterPass.name).get.bytes
          case Left(messages) => fail(s"Parse failed: ${messages.format}")
        }

      val anythingBytes = bastOf("Anything")
      // The deprecated spelling has the same character length, so locations coincide and the
      // encodings must match byte for byte.
      assert(
        anythingBytes.sameElements(bastOf("Abstract")),
        "BAST bytes differ between `Anything` and the deprecated `Abstract` spelling"
      )

      BASTReader.read(anythingBytes) match {
        case Right(module) =>
          val typ = Finder(module)
            .recursiveFindByType[com.ossuminc.riddl.language.AST.Type]
            .find(_.id.value == "Whatever")
            .getOrElse(fail("type Whatever missing after BAST read"))
          assert(
            typ.typEx.isInstanceOf[com.ossuminc.riddl.language.AST.Anything],
            s"expected Anything, got ${typ.typEx.getClass.getSimpleName}"
          )
        case Left(errors) => fail(s"Deserialization failed: ${errors.format}")
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
            case Right(module) =>
              assert(compareRoots(originalRoot, module), "initial-marker round trip: ASTs differ")
              import com.ossuminc.riddl.language.Finder
              import com.ossuminc.riddl.language.AST.Entity
              val e = Finder(module.contents).recursiveFindByType[Entity].head
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
          |  query Ask replies result Res is { q: Integer }
          |  entity e is {
          |    record F is { q: Integer }
          |    state S of record e.F
          |    handler H is {
          |      on init { set field e.F.q to "0" }
          |      on query Ask { reply result Res }
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
            case Right(module) =>
              assert(compareRoots(originalRoot, module), "yield-statement round trip: ASTs differ")
              // ReplyStatement, not Yield: the fixture answers a QUERY, and as of 2.0 that is
              // `reply`. This also exercises BAST statement tag 19, added with the node.
              val ys = Finder(module.contents).recursiveFindByType[AST.ReplyStatement]
              assert(ys.size == 1, s"expected one ReplyStatement, found ${ys.size}")
              assert(
                ys.head.msg.deliverableOperandPathId.value.last == "Res",
                "reply target lost in BAST"
              )
            case Left(errors) => fail(s"Deserialization failed: ${errors.format}")
          }
        case Left(messages) => fail(s"Parse failed: ${messages.format}")
      }
    }

    "serialize and deserialize ValueRef operands on yield/reply/morph (revision 17)" in {
      // Message-value design Task 2/3: `yield`, `reply` and `morph … with` accept a bare ValueRef.
      // Two DIFFERENT codecs carry it -- the message operand (already had discriminator 2, but the
      // reader THREW on it for yield/reply) and the record operand (which gains discriminator 2
      // here) -- so both are exercised in one model. This is what revision 17 is for.
      val riddlSource =
        """domain d is { context c is {
          |  command Foo yields event Bar is { a: Integer }
          |  event Bar is { b: Integer }
          |  query Qry replies result Res is { q: Integer }
          |  result Res is { r: Integer }
          |  record Data is { evt: d.c.Bar, answer: d.c.Res }
          |  record Other is { m: Integer }
          |  record Holder is { next: d.c.Other }
          |  entity src is {
          |    state S of record d.c.Data
          |    state T of record d.c.Other
          |    state H of record d.c.Holder
          |    handler Ops is {
          |      on command d.c.Foo is {
          |        morph entity d.c.src to state T with next
          |        yield evt
          |      }
          |      on query d.c.Qry is { reply answer }
          |    }
          |  }
          |}}
          |""".stripMargin
      val input = RiddlParserInput(riddlSource, "test-value-operands")
      TopLevelParser.parseInput(input, true) match {
        case Right(originalRoot: Root) =>
          val writerResult =
            Pass.runThesePasses(PassInput(originalRoot), Seq(BASTWriterPass.creator()))
          val output = writerResult.outputOf[BASTOutput](BASTWriterPass.name).get
          BASTReader.read(output.bytes) match {
            case Right(module) =>
              assert(compareRoots(originalRoot, module), "value-operand round trip: ASTs differ")
              // Assert the NODE KIND survived, not merely that something came back: a reader that
              // rebuilt these as keyword-led refs would still compare equal on the path alone.
              val ys = Finder(module.contents).recursiveFindByType[AST.YieldStatement]
              assert(ys.size == 1, s"expected one YieldStatement, found ${ys.size}")
              assert(ys.head.msg.isInstanceOf[AST.ValueRef], "yield operand is not a ValueRef")
              assert(ys.head.msg.deliverableOperandPathId.value.last == "evt", "yield operand lost")

              val rs = Finder(module.contents).recursiveFindByType[AST.ReplyStatement]
              assert(rs.size == 1, s"expected one ReplyStatement, found ${rs.size}")
              assert(rs.head.msg.isInstanceOf[AST.ValueRef], "reply operand is not a ValueRef")
              assert(rs.head.msg.deliverableOperandPathId.value.last == "answer", "reply lost")

              val ms = Finder(module.contents).recursiveFindByType[AST.MorphStatement]
              assert(ms.size == 1, s"expected one MorphStatement, found ${ms.size}")
              ms.head.value match
                case vr: AST.ValueRef =>
                  assert(vr.path.value.last == "next", "morph operand lost in BAST")
                case other =>
                  fail(s"morph operand came back as ${other.getClass.getSimpleName}, not ValueRef")
            case Left(errors) => fail(s"Deserialization failed: ${errors.format}")
          }
        case Left(messages) => fail(s"Parse failed: ${messages.format}")
      }
    }

    "serialize and deserialize a `foreach` statement (A25)" in {
      // A25 uses new BAST subtag 16 (a wire change carried into version 2). Verify both a field-ref collection and a
      // let-local collection round-trip, and the nested body survives.
      val riddlSource =
        """domain d is { context c is {
          |  record Order is { id: Integer }
          |  type OrderList is many Order
          |  command Batch is { orders: OrderList }
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
            case Right(module) =>
              assert(
                compareRoots(originalRoot, module),
                "foreach-statement round trip: ASTs differ"
              )
              import com.ossuminc.riddl.language.Finder
              import com.ossuminc.riddl.language.AST.{FieldRef, ForeachStatement, Identifier}
              val fes = Finder(module.contents).recursiveFindByType[ForeachStatement]
              assert(fes.size == 2, s"expected two ForeachStatements, found ${fes.size}")
              val outer = fes.find(_.element.value == "o").getOrElse(fail("outer foreach lost"))
              outer.collection match
                case fr: FieldRef => assert(fr.pathId.value == Seq("Batch", "orders"))
                case other        => fail(s"expected FieldRef, got $other")
              val inner = fes.find(_.element.value == "p").getOrElse(fail("inner foreach lost"))
              inner.collection match
                case id: Identifier => assert(id.value == "batch")
                case other          => fail(s"expected Identifier, got $other")
              // Neither loop destructures, so neither may come back carrying a value name. The
              // presence flag is written unconditionally at FORMAT_REVISION 10; if it were skipped
              // for `None`, the collection's own type flag would be read in its place and every
              // byte after it would be misaligned.
              assert(fes.forall(_.valueElement.isEmpty), "a single-name foreach grew a value name")
            case Left(errors) => fail(s"Deserialization failed: ${errors.format}")
          }
        case Left(messages) => fail(s"Parse failed: ${messages.format}")
      }
    }

    "serialize and deserialize a destructuring `foreach k, v` (FORMAT_REVISION 10)" in {
      // The mapping form binds TWO names. The second is an Option written inline before the
      // collection, so a reader that does not know about it misreads everything downstream --
      // which is what the revision bump gates.
      val riddlSource =
        """domain d is { context c is {
          |  record Line is { sku: String }
          |  type ById is mapping from Integer to Line
          |  command Cmd is { byId: ById }
          |  handler h is {
          |    on command Cmd {
          |      foreach k, v in field Cmd.byId { do "process the entry" }
          |    }
          |  }
          |}}
          |""".stripMargin
      val input = RiddlParserInput(riddlSource, "test-foreach-destructuring")
      TopLevelParser.parseInput(input, true) match {
        case Right(originalRoot: Root) =>
          val writerResult =
            Pass.runThesePasses(PassInput(originalRoot), Seq(BASTWriterPass.creator()))
          val output = writerResult.outputOf[BASTOutput](BASTWriterPass.name).get
          BASTReader.read(output.bytes) match {
            case Right(module) =>
              assert(
                compareRoots(originalRoot, module),
                "destructuring foreach round trip: ASTs differ"
              )
              import com.ossuminc.riddl.language.Finder
              import com.ossuminc.riddl.language.AST.{FieldRef, ForeachStatement}
              val fes = Finder(module.contents).recursiveFindByType[ForeachStatement]
              assert(fes.size == 1, s"expected one ForeachStatement, found ${fes.size}")
              val fs = fes.head
              assert(fs.element.value == "k", s"key name lost: ${fs.element.value}")
              assert(
                fs.valueElement.map(_.value).contains("v"),
                s"value name lost: ${fs.valueElement.map(_.value)}"
              )
              // The collection must still be read correctly AFTER the new optional field --
              // the alignment check that makes the two assertions above mean something.
              fs.collection match
                case fr: FieldRef => assert(fr.pathId.value == Seq("Cmd", "byId"))
                case other        => fail(s"expected FieldRef, got $other")
              assert(fs.doStatements.toSeq.size == 1, "loop body lost")
            case Left(errors) => fail(s"Deserialization failed: ${errors.format}")
          }
        case Left(messages) => fail(s"Parse failed: ${messages.format}")
      }
    }

    "serialize and deserialize `put` and `return` with value expressions (A45/A54/A57)" in {
      // A45/A57 use BAST subtags 17/18 (a wire change carried into version 2). Verify a return with a record
      // constructor and a put reading from a UI input round-trip losslessly.
      val riddlSource =
        """domain d is {
          |  context Calc is {
          |    record Sum is { total: Integer }
          |    function Add is {
          |      returns record Sum
          |      return record Sum(total = "the total")
          |    }
          |  }
          |  application context UI is {
          |    record Greeting is { text: String }
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
            case Right(module) =>
              assert(compareRoots(originalRoot, module), "put/return round trip: ASTs differ")
              import com.ossuminc.riddl.language.Finder
              import com.ossuminc.riddl.language.AST.*
              val rets = Finder(module.contents).recursiveFindByType[ReturnStatement]
              assert(rets.size == 1, s"expected one ReturnStatement, found ${rets.size}")
              rets.head.value match
                case c: Constructor =>
                  assert(c.ref.isInstanceOf[RecordRef])
                  assert(c.args.size == 1)
                  assert(c.args.head.name.map(_.value) == Some("total"))
                case other => fail(s"expected Constructor, got $other")
              val puts = Finder(module.contents).recursiveFindByType[PutStatement]
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

    "serialize and deserialize a `call function F(args)` value (A24)" in {
      // A24 uses value discriminator 6 (a wire change carried into version 2). A return of a call with named args
      // and an empty-arg call must round-trip byte-symmetrically preserving the Call structure.
      val riddlSource =
        """domain d is {
          |  context Calc is {
          |    record Args is { a: Integer, b: Integer }
          |    record Sum is { total: Integer }
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
      val input = RiddlParserInput(riddlSource, "test-call")
      TopLevelParser.parseInput(input, true) match {
        case Right(originalRoot: Root) =>
          val writerResult =
            Pass.runThesePasses(PassInput(originalRoot), Seq(BASTWriterPass.creator()))
          val output = writerResult.outputOf[BASTOutput](BASTWriterPass.name).get
          BASTReader.read(output.bytes) match {
            case Right(module) =>
              assert(compareRoots(originalRoot, module), "call round trip: ASTs differ")
              import com.ossuminc.riddl.language.Finder
              import com.ossuminc.riddl.language.AST.*
              // A Call is a Value (not a Contents node): reach it through its ReturnStatement.
              val calls = Finder(module.contents)
                .recursiveFindByType[ReturnStatement]
                .map(_.value)
                .collect { case c: Call => c }
              assert(calls.size == 2, s"expected two Calls, found ${calls.size}")
              val add = calls.find(_.function.pathId.value == Seq("Add")).get
              assert(add.args.size == 2)
              assert(add.args.map(_.name.map(_.value)) == Seq(Some("a"), Some("b")))
              assert(calls.find(_.function.pathId.value == Seq("Now")).get.args.isEmpty)
            case Left(errors) => fail(s"Deserialization failed: ${errors.format}")
          }
        case Left(messages) => fail(s"Parse failed: ${messages.format}")
      }
    }

    "serialize and deserialize a nested boolean expression (A28)" in {
      // A28 uses BAST value discriminator 5 with sub-tags 0-3 (a wire change carried into version 2). Verify a nested
      // let x = (a or b) and not c survives byte-symmetric round-trip preserving its tree shape.
      val riddlSource =
        """domain d is {
          |  context c is {
          |    handler h is {
          |      on init {
          |        let x = (a or b) and not c
          |      }
          |    }
          |  }
          |}
          |""".stripMargin
      val input = RiddlParserInput(riddlSource, "test-boolexpr")
      TopLevelParser.parseInput(input, true) match {
        case Right(originalRoot: Root) =>
          val writerResult =
            Pass.runThesePasses(PassInput(originalRoot), Seq(BASTWriterPass.creator()))
          val output = writerResult.outputOf[BASTOutput](BASTWriterPass.name).get
          BASTReader.read(output.bytes) match {
            case Right(module) =>
              import com.ossuminc.riddl.language.Finder
              import com.ossuminc.riddl.language.AST.*
              val lets = Finder(module.contents).recursiveFindByType[LetStatement]
              assert(lets.size == 1, s"expected one LetStatement, found ${lets.size}")
              lets.head.expression match
                case LogicalExpression(_, LogicalOperator.And, left, right) =>
                  left match
                    case LogicalExpression(_, LogicalOperator.Or, a, b) =>
                      assert(a.asInstanceOf[ValueRef].path.value == Seq("a"))
                      assert(b.asInstanceOf[ValueRef].path.value == Seq("b"))
                    case other => fail(s"expected Or on the left, got $other")
                  right match
                    case NotExpression(_, inner) =>
                      assert(inner.asInstanceOf[ValueRef].path.value == Seq("c"))
                    case other => fail(s"expected Not on the right, got $other")
                case other => fail(s"expected And at the root, got $other")
            case Left(errors) => fail(s"Deserialization failed: ${errors.format}")
          }
        case Left(messages) => fail(s"Parse failed: ${messages.format}")
      }
    }

    "serialize and deserialize boolean-expression conditions in when/require/invariant (A28 s2)" in {
      // A28 slice 2 widens the when/require/invariant condition BAST codecs (flag 2 -> writeValue,
      // and the invariant option sub-flag). Verify each survives a byte round-trip, and that the M3
      // `when a > b and not c` structure is preserved.
      val riddlSource =
        """domain d is {
          |  context c is {
          |    entity e is {
          |      invariant inv is x > y
          |      handler h is {
          |        on init {
          |          require count == total
          |          when a > b and not c then error "boom" end
          |        }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin
      val input = RiddlParserInput(riddlSource, "test-boolcond")
      TopLevelParser.parseInput(input, true) match {
        case Right(originalRoot: Root) =>
          val writerResult =
            Pass.runThesePasses(PassInput(originalRoot), Seq(BASTWriterPass.creator()))
          val output = writerResult.outputOf[BASTOutput](BASTWriterPass.name).get
          BASTReader.read(output.bytes) match {
            case Right(module) =>
              import com.ossuminc.riddl.language.Finder
              import com.ossuminc.riddl.language.AST.*
              // require count == total -> ComparisonExpression
              val requires = Finder(module.contents).recursiveFindByType[RequireStatement]
              assert(requires.size == 1, s"expected one RequireStatement, found ${requires.size}")
              requires.head.condition match
                case ComparisonExpression(_, ComparisonOperator.EQ, l, r) =>
                  assert(l.asInstanceOf[ValueRef].path.value == Seq("count"))
                  assert(r.asInstanceOf[ValueRef].path.value == Seq("total"))
                case other => fail(s"expected a comparison require condition, got $other")
              // when a > b and not c -> And(Comparison, Not)
              val whens = Finder(module.contents).recursiveFindByType[WhenStatement]
              assert(whens.size == 1, s"expected one WhenStatement, found ${whens.size}")
              whens.head.condition match
                case LogicalExpression(_, LogicalOperator.And, left, right) =>
                  left match
                    case ComparisonExpression(_, ComparisonOperator.GT, a, b) =>
                      assert(a.asInstanceOf[ValueRef].path.value == Seq("a"))
                      assert(b.asInstanceOf[ValueRef].path.value == Seq("b"))
                    case other => fail(s"expected a > b on the left, got $other")
                  right match
                    case NotExpression(_, inner) =>
                      assert(inner.asInstanceOf[ValueRef].path.value == Seq("c"))
                    case other => fail(s"expected not c on the right, got $other")
                case other => fail(s"expected And when condition, got $other")
              // invariant inv is x > y -> ComparisonExpression
              val invs = Finder(module.contents).recursiveFindByType[Invariant]
              assert(invs.size == 1, s"expected one Invariant, found ${invs.size}")
              invs.head.condition match
                case Some(ComparisonExpression(_, ComparisonOperator.GT, l, r)) =>
                  assert(l.asInstanceOf[ValueRef].path.value == Seq("x"))
                  assert(r.asInstanceOf[ValueRef].path.value == Seq("y"))
                case other => fail(s"expected a comparison invariant condition, got $other")
            case Left(errors) => fail(s"Deserialization failed: ${errors.format}")
          }
        case Left(messages) => fail(s"Parse failed: ${messages.format}")
      }
    }

    "serialize and deserialize a bare boolean value-reference `when` condition (A17)" in {
      // A17 adds when-condition BAST flag 3 (writeValue of a ValueRef) — a wire change carried into version 2. Verify a
      // single-name and a dotted-path bare boolean reference each survive a byte round-trip.
      val riddlSource =
        """domain d is {
          |  context c is {
          |    handler h is {
          |      on init {
          |        when flag then error "one" end
          |        when order.isPaid then error "two" end
          |      }
          |    }
          |  }
          |}
          |""".stripMargin
      val input = RiddlParserInput(riddlSource, "test-whenref")
      TopLevelParser.parseInput(input, true) match {
        case Right(originalRoot: Root) =>
          val writerResult =
            Pass.runThesePasses(PassInput(originalRoot), Seq(BASTWriterPass.creator()))
          val output = writerResult.outputOf[BASTOutput](BASTWriterPass.name).get
          BASTReader.read(output.bytes) match {
            case Right(module) =>
              import com.ossuminc.riddl.language.Finder
              import com.ossuminc.riddl.language.AST.*
              val whens = Finder(module.contents).recursiveFindByType[WhenStatement]
              assert(whens.size == 2, s"expected two WhenStatements, found ${whens.size}")
              whens.head.condition match
                case vr: ValueRef => assert(vr.path.value == Seq("flag"))
                case other        => fail(s"expected a ValueRef condition, got $other")
              whens(1).condition match
                case vr: ValueRef => assert(vr.path.value == Seq("order", "isPaid"))
                case other        => fail(s"expected a dotted ValueRef condition, got $other")
            case Left(errors) => fail(s"Deserialization failed: ${errors.format}")
          }
        case Left(messages) => fail(s"Parse failed: ${messages.format}")
      }
    }

    "serialize and deserialize a structured match: subject + patterns + guard (A29)" in {
      // A29 restructures MatchStatement (subject union + structured patterns + optional guards),
      // a wire change carried into version 2. Verify the value-ref subject, a type-case, a
      // comparison pattern, a guard,
      // and a legacy literal pattern all survive a byte round-trip.
      val riddlSource =
        """domain d is {
          |  context c is {
          |    handler h is {
          |      on init {
          |        match order.status {
          |          case Shipped when active { error "s" }
          |          case == Cancelled { error "c" }
          |          case > MaxRetries when count > MaxRetries { error "r" }
          |          default { error "d" }
          |        }
          |        match "legacy" {
          |          case "x" { error "x" }
          |        }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin
      val input = RiddlParserInput(riddlSource, "test-match")
      TopLevelParser.parseInput(input, true) match {
        case Right(originalRoot: Root) =>
          val writerResult =
            Pass.runThesePasses(PassInput(originalRoot), Seq(BASTWriterPass.creator()))
          val output = writerResult.outputOf[BASTOutput](BASTWriterPass.name).get
          BASTReader.read(output.bytes) match {
            case Right(module) =>
              import com.ossuminc.riddl.language.Finder
              import com.ossuminc.riddl.language.AST.*
              val matches = Finder(module.contents).recursiveFindByType[MatchStatement]
              assert(matches.size == 2, s"expected two MatchStatements, found ${matches.size}")
              val structured =
                matches.find(_.cases.size == 3).getOrElse(fail("structured match lost"))
              structured.expression match
                case vr: ValueRef => assert(vr.path.value == Seq("order", "status"))
                case other        => fail(s"expected a ValueRef subject, got $other")
              structured.cases(0).pattern match
                case TypePattern(_, tr) => assert(tr.pathId.value == Seq("Shipped"))
                case other              => fail(s"expected TypePattern, got $other")
              // A29: a bare boolean value-ref guard round-trips via the value codec (no new tag).
              structured.cases(0).guard match
                case Some(vr: ValueRef) => assert(vr.path.value == Seq("active"))
                case other              => fail(s"expected a bare ValueRef guard, got $other")
              structured.cases(1).pattern match
                case ComparisonPattern(_, ComparisonOperator.EQ, c) =>
                  assert(c.asInstanceOf[ValueRef].path.value == Seq("Cancelled"))
                case other => fail(s"expected == ComparisonPattern, got $other")
              structured.cases(2).pattern match
                case ComparisonPattern(_, ComparisonOperator.GT, _) => succeed
                case other => fail(s"expected > ComparisonPattern, got $other")
              structured.cases(2).guard match
                case Some(_: ComparisonExpression) => succeed
                case other                         => fail(s"expected a guard, got $other")
              val legacy = matches.find(_.cases.size == 1).getOrElse(fail("legacy match lost"))
              legacy.expression match
                case ls: LiteralString => assert(ls.s == "legacy")
                case other             => fail(s"expected a LiteralString subject, got $other")
              legacy.cases.head.pattern match
                case LiteralPattern(_, ls) => assert(ls.s == "x")
                case other                 => fail(s"expected LiteralPattern, got $other")
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
          |    query Ask replies result Res is { q: String }
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
          |          reply result Res(ok = "done")
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
            case Right(module) =>
              assert(compareRoots(originalRoot, module), "widened operands round trip: ASTs differ")
              import com.ossuminc.riddl.language.Finder
              import com.ossuminc.riddl.language.AST.*
              val lets = Finder(module.contents).recursiveFindByType[LetStatement]
              lets.find(_.identifier.value == "note").map(_.expression) match
                case Some(pv: PromptValue) => assert(pv.prompt.s == "summarize the addition")
                case other                 => fail(s"expected a PromptValue let, got $other")
              val sets = Finder(module.contents).recursiveFindByType[SetStatement]
              sets.head.value match
                case c: Constructor => assert(c.ref.isInstanceOf[RecordRef])
                case other          => fail(s"expected Constructor set value, got $other")
              val sends = Finder(module.contents).recursiveFindByType[SendStatement]
              sends.head.msg match
                case c: Constructor => assert(c.ref.isInstanceOf[EventRef])
                case other          => fail(s"expected Constructor send msg, got $other")
              val morphs = Finder(module.contents).recursiveFindByType[MorphStatement]
              morphs.head.value match
                case c: Constructor =>
                  assert(c.ref.isInstanceOf[RecordRef])
                  assert(c.args.head.value.isInstanceOf[Constructor])
                case other => fail(s"expected Constructor morph value, got $other")
              val replies = Finder(module.contents).recursiveFindByType[ReplyStatement]
              replies.head.msg match
                case c: Constructor => assert(c.ref.isInstanceOf[ResultRef])
                case other          => fail(s"expected Constructor reply msg, got $other")
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
          |  function f is {
          |    // what it needs
          |    requires record Args
          |    returns result Res
          |  }
          |  function g is { requires { b: Boolean } returns { r: Integer } ??? }
          |  saga s is {
          |    requires record Args
          |    returns result Res
          |    step One is { send command Go to inlet d.c.e.t.in }
          |      reverted by { send command UndoGo to inlet d.c.e.t.in }
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
            case Right(module) =>
              assert(compareRoots(originalRoot, module), "requires/returns round trip: ASTs differ")
              import com.ossuminc.riddl.language.Finder
              import com.ossuminc.riddl.language.AST.{Aggregation, Function, TypeRef}
              val funcs = Finder(module.contents).recursiveFindByType[Function]
              val f = funcs.find(_.id.value == "f").get
              assert(f.input.get.isInstanceOf[TypeRef], "function ref requires lost in BAST")
              assert(
                f.input.get.asInstanceOf[TypeRef].keyword == "record",
                "ref keyword lost in BAST"
              )
              val g = funcs.find(_.id.value == "g").get
              assert(g.input.get.isInstanceOf[Aggregation], "inline requires flipped in BAST")
              // The clauses are CONTENTS, so BAST has to carry their position, not just their
              // value: a comment written above `requires` must come back above it.
              import com.ossuminc.riddl.language.toSeq
              assert(
                f.contents.toSeq.map(_.getClass.getSimpleName) ==
                  Seq("LineComment", "Requires", "Returns"),
                s"clause order lost in BAST: ${f.contents.toSeq.map(_.getClass.getSimpleName)}"
              )
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
            case Right(module) =>
              assert(
                compareRoots(originalRoot, module),
                "domain-scoped connector round trip failed: ASTs are not equivalent"
              )
              val domain =
                module.domains
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
      // AST -> BAST -> AST as the same node kinds (new node tags 4/5/6, a wire change carried into version 2).
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
            case Right(module) =>
              assert(
                compareRoots(originalRoot, module),
                "handler-kinds round trip failed: ASTs are not equivalent"
              )
              // And specifically: each new clause kind survives, not collapsed/dropped.
              import com.ossuminc.riddl.language.Finder
              val f = Finder(module.contents)
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

    "serialize and deserialize the A55 on-clause message binding" in {
      // Reflective across BAST: the optional local binding rides on the existing on-clause
      // sub-discriminators 2 (message) and 4 (event) — no new node tag.
      val riddlSource =
        """domain d is {
          |  context c is {
          |    entity e is {
          |      command Cmd is { g: Integer }
          |      event Evt is { h: Integer }
          |      handler hh is {
          |        on cmd: command Cmd { do "handle" }
          |        on evt: event Evt { do "note" }
          |        on command Cmd { do "again" }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin
      val input = RiddlParserInput(riddlSource, "test-on-clause-binding")
      TopLevelParser.parseInput(input, true) match {
        case Right(originalRoot: Root) =>
          val writerResult =
            Pass.runThesePasses(PassInput(originalRoot), Seq(BASTWriterPass.creator()))
          val output = writerResult.outputOf[BASTOutput](BASTWriterPass.name).get
          BASTReader.read(output.bytes) match {
            case Right(module) =>
              assert(
                compareRoots(originalRoot, module),
                "on-clause binding round trip failed: ASTs are not equivalent"
              )
              import com.ossuminc.riddl.language.Finder
              val clauses = Finder(module.contents).recursiveFindByType[OnMessageLikeClause]
              assert(clauses.size == 3, s"expected 3 on-clauses, got ${clauses.size}")
              assert(
                clauses.flatMap(_.binding.map(_.value)) == Seq("cmd", "evt"),
                "on-clause bindings did not survive BAST round trip"
              )
            case Left(errors) => fail(s"Deserialization failed: ${errors.format}")
          }
        case Left(messages) => fail(s"Parse failed: ${messages.format}")
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
            case Right(module) =>
              assert(
                compareRoots(originalRoot, module),
                "processor-model round trip failed: ASTs are not equivalent"
              )
              import com.ossuminc.riddl.language.Finder
              import com.ossuminc.riddl.language.AST.{Context, Entity, Intention, Streamlet}
              val f = Finder(module.contents)
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

    "serialize and deserialize a state-scoped invariant (A18)" in {
      // A18 adds Invariant to StateContents (a wire change carried into version 2). Verify an invariant declared
      // inside a state survives AST -> BAST -> AST, staying inside the state (not relocated).
      val riddlSource =
        """domain d is { context c is { entity e is {
          |  type Data is { x: Integer }
          |  state S of record d.c.e.Data is {
          |    invariant nonNegative is "x must be >= 0"
          |    handler H is { on other is { do "a" } }
          |  }
          |}}}
          |""".stripMargin
      val input = RiddlParserInput(riddlSource, "test-state-invariant")
      TopLevelParser.parseInput(input, true) match {
        case Right(originalRoot: Root) =>
          val writerResult =
            Pass.runThesePasses(PassInput(originalRoot), Seq(BASTWriterPass.creator()))
          val output = writerResult.outputOf[BASTOutput](BASTWriterPass.name).get
          BASTReader.read(output.bytes) match {
            case Right(module) =>
              assert(compareRoots(originalRoot, module), "state-invariant round trip: ASTs differ")
              import com.ossuminc.riddl.language.Finder
              import com.ossuminc.riddl.language.AST.Entity
              val e = Finder(module.contents).recursiveFindByType[Entity].head
              val s = e.states.find(_.id.value == "S").getOrElse(fail("state S lost in BAST"))
              assert(
                s.invariants.map(_.id.value) == Seq("nonNegative"),
                "state-scoped invariant lost in BAST"
              )
              assert(
                s.invariants.head.condition
                  .collect { case ls: AST.LiteralString => ls.s }
                  .contains("x must be >= 0"),
                "invariant condition lost in BAST"
              )
              assert(e.invariants.isEmpty, "invariant leaked to entity level in BAST")
            case Left(errors) => fail(s"Deserialization failed: ${errors.format}")
          }
        case Left(messages) => fail(s"Parse failed: ${messages.format}")
      }
    }

    "serialize and deserialize versions in BOTH component forms (A53)" in {
      // A53 adds Version (NODE_VERSION = 103, a wire change carried into version 2) at root/module/domain/context/
      // entity. Both the NAMED and the NUMERIC component form must survive AST -> BAST -> AST at
      // their own scope, keeping the numeric/named discriminator intact.
      val riddlSource =
        """version Jellyfish
          |domain d is {
          |  version Garibaldi
          |  context c is {
          |    version 4
          |    entity e is {
          |      version 3
          |      record R(x: Integer)
          |      state S of record d.c.e.R is { handler H is { on other is { do "a" } } }
          |    }
          |  }
          |}
          |module m is { version 9 }
          |""".stripMargin
      val input = RiddlParserInput(riddlSource, "test-version")
      TopLevelParser.parseInput(input, true) match {
        case Right(originalRoot: Root) =>
          val writerResult =
            Pass.runThesePasses(PassInput(originalRoot), Seq(BASTWriterPass.creator()))
          val output = writerResult.outputOf[BASTOutput](BASTWriterPass.name).get
          BASTReader.read(output.bytes) match {
            case Right(module) =>
              assert(compareRoots(originalRoot, module), "version round trip: ASTs differ")
              import com.ossuminc.riddl.language.Finder
              import com.ossuminc.riddl.language.AST.{Context, Domain, Entity, Module as ModuleAST}
              // The BAST serialization root is a Module standing in for the Root, so the root-level
              // `version Jellyfish` lands directly in its contents.
              assert(
                module.version.map(_.component).contains("Jellyfish"),
                "root-level named version lost in BAST"
              )
              assert(module.version.flatMap(_.number).isEmpty, "named version became numeric")
              val finder = Finder(module.contents)
              val d = finder.recursiveFindByType[Domain].head
              assert(d.version.map(_.component).contains("Garibaldi"), "domain version lost")
              assert(d.version.flatMap(_.number).isEmpty, "domain version became numeric")
              val c = finder.recursiveFindByType[Context].head
              assert(c.version.flatMap(_.number).contains(4L), "numeric context version lost")
              val e = finder.recursiveFindByType[Entity].head
              assert(e.version.flatMap(_.number).contains(3L), "numeric entity version lost")
              val m = finder.recursiveFindByType[ModuleAST].find(_.id.value == "m").get
              assert(m.version.flatMap(_.number).contains(9L), "numeric module version lost")
            case Left(errors) => fail(s"Deserialization failed: ${errors.format}")
          }
        case Left(messages) => fail(s"Parse failed: ${messages.format}")
      }
    }

    "serialize and deserialize copyrights at every permitted scope (A47)" in {
      // A47 adds Copyright (NODE_COPYRIGHT = 104, a wire change carried into version 2) at root/module/domain and
      // all six processors, and widens Version to the same set. Both leaves must survive
      // AST -> BAST -> AST at their own scope, with the notice carried verbatim.
      val riddlSource =
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
          |      record R(x: Integer)
          |      state S of record d.c.e.R is { handler H is { on other is { do "a" } } }
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
      val input = RiddlParserInput(riddlSource, "test-copyright")
      TopLevelParser.parseInput(input, true) match {
        case Right(originalRoot: Root) =>
          val writerResult =
            Pass.runThesePasses(PassInput(originalRoot), Seq(BASTWriterPass.creator()))
          val output = writerResult.outputOf[BASTOutput](BASTWriterPass.name).get
          BASTReader.read(output.bytes) match {
            case Right(module) =>
              assert(compareRoots(originalRoot, module), "copyright round trip: ASTs differ")
              import com.ossuminc.riddl.language.Finder
              import com.ossuminc.riddl.language.AST.{
                Adaptor,
                Context,
                Domain,
                Entity,
                Module as ModuleAST,
                Projector,
                Repository,
                Streamlet
              }
              // The BAST serialization root is a Module standing in for the Root, so the
              // root-level `copyright Root` lands directly in its contents.
              assert(
                module.copyright.map(_.id.value).contains("Root"),
                "root-level copyright lost in BAST"
              )
              val finder = Finder(module.contents)
              assert(
                finder
                  .recursiveFindByType[Domain]
                  .head
                  .copyright
                  .map(_.notice)
                  .contains(
                    "© 2026 Ossum Inc. (domain)"
                  ),
                "domain copyright lost"
              )
              assert(
                finder.recursiveFindByType[Context].head.copyright.isDefined,
                "context copyright lost"
              )
              assert(
                finder.recursiveFindByType[Entity].head.copyright.isDefined,
                "entity copyright lost"
              )
              assert(
                finder.recursiveFindByType[Repository].head.copyright.isDefined,
                "repository copyright lost"
              )
              assert(
                finder.recursiveFindByType[Projector].head.copyright.isDefined,
                "projector copyright lost"
              )
              assert(
                finder.recursiveFindByType[Streamlet].head.copyright.isDefined,
                "streamlet copyright lost"
              )
              val ad = finder.recursiveFindByType[Adaptor].head
              assert(ad.copyright.isDefined, "adaptor copyright lost")
              assert(ad.version.flatMap(_.number).contains(7L), "adaptor version lost")
              val m = finder.recursiveFindByType[ModuleAST].find(_.id.value == "m").get
              assert(m.copyright.isDefined, "module copyright lost")
            case Left(errors) => fail(s"Deserialization failed: ${errors.format}")
          }
        case Left(messages) => fail(s"Parse failed: ${messages.format}")
      }
    }

    "serialize and deserialize figma references (A42)" in {
      // A42 adds FigmaRef metadata (NODE_FIGMA_REF = 105, a wire change carried into version 2) on inputs,
      // outputs, groups and application-intended contexts. Both literal strings must survive
      // AST -> BAST -> AST on every one of them.
      val riddlSource =
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
      val input = RiddlParserInput(riddlSource, "test-figma")
      TopLevelParser.parseInput(input, true) match {
        case Right(originalRoot: Root) =>
          val writerResult =
            Pass.runThesePasses(PassInput(originalRoot), Seq(BASTWriterPass.creator()))
          val output = writerResult.outputOf[BASTOutput](BASTWriterPass.name).get
          BASTReader.read(output.bytes) match {
            case Right(module) =>
              assert(compareRoots(originalRoot, module), "figma round trip: ASTs differ")
              import com.ossuminc.riddl.language.Finder
              import com.ossuminc.riddl.language.AST.{Context, Group, Input, Output}
              val finder = Finder(module.contents)
              val group = finder.recursiveFindByType[Group].head
              assert(group.figmaRefs.size == 1, "group figma ref lost")
              assert(group.figmaRefs.head.fileKey.s == "FILEKEY", "group figma fileKey lost")
              assert(group.figmaRefs.head.nodeId.s == "12:30", "group figma nodeId lost")
              assert(
                finder.recursiveFindByType[Input].head.figmaRefs.head.nodeId.s == "12:34",
                "input figma ref lost"
              )
              assert(
                finder.recursiveFindByType[Output].head.figmaRefs.head.nodeId.s == "12:36",
                "output figma ref lost"
              )
              assert(
                finder.recursiveFindByType[Context].head.figmaRefs.head.nodeId.s == "12:1",
                "context figma ref lost"
              )
            case Left(errors) => fail(s"Deserialization failed: ${errors.format}")
          }
        case Left(messages) => fail(s"Parse failed: ${messages.format}")
      }
    }

    "serialize and deserialize Id(P)'s kind keyword (processor-instance-identity task 1)" in {
      // FORMAT_REVISION moved to 15 FOR this feature, but no .riddl in this repo or the
      // corpus uses the keyword form, so the alignment-critical `true` + interned-string
      // branch of the Some(keyword) write/read path was proven only by reading the
      // writer/reader code, not by an actual write -> read cycle. Both the keyword and bare
      // forms are exercised so a leak in either direction (dropped keyword, or a bare Id
      // spuriously gaining one) is caught.
      val riddlSource =
        """domain d is { context c is {
          |  entity E is { ??? }
          |  type WithKeyword is Id(entity E)
          |  type Bare is Id(E)
          |}}
          |""".stripMargin
      val input = RiddlParserInput(riddlSource, "test-id-keyword")
      TopLevelParser.parseInput(input, true) match {
        case Right(originalRoot: Root) =>
          val writerResult =
            Pass.runThesePasses(PassInput(originalRoot), Seq(BASTWriterPass.creator()))
          val output = writerResult.outputOf[BASTOutput](BASTWriterPass.name).get
          BASTReader.read(output.bytes) match {
            case Right(module) =>
              assert(compareRoots(originalRoot, module), "Id(P) keyword round trip: ASTs differ")
              import com.ossuminc.riddl.language.Finder
              import com.ossuminc.riddl.language.AST.{Type, UniqueId}
              val types = Finder(module.contents).recursiveFindByType[Type]
              types.find(_.id.value == "WithKeyword").get.typEx match
                case uid: UniqueId =>
                  assert(uid.kindKeyword.contains("entity"), "Id keyword lost in BAST")
                case other => fail(s"expected UniqueId, got $other")
              types.find(_.id.value == "Bare").get.typEx match
                case uid: UniqueId =>
                  assert(uid.kindKeyword.isEmpty, "bare Id gained a keyword in BAST")
                case other => fail(s"expected UniqueId, got $other")
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
                case Right(reconstructedModule) =>
                  println(
                    s"BAST read: Module with ${reconstructedModule.contents.toSeq.size} items"
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
                case Right(reconstructedModule) =>
                  println(s"BAST read: Module reconstructed")

                  // Step 4: Compare original and reconstructed
                  val areEqual = compareRoots(originalRoot, reconstructedModule)

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

  /** Compare Root (original) with Module (reconstructed) for deep structural equality
    *
    * Note: BASTWriter writes a Root as a NODE_MODULE node (S61-1), so deserialization produces a is
    * expected - we're comparing the CONTENT, not the container type.
    *
    * Uses DeepASTComparison to recursively verify all fields, identifiers, locations, and nested
    * content.
    */
  private def compareRoots(original: Root, reconstructed: Module): Boolean = {
    println(s"\n=== Deep Structural Comparison ===")
    println(s"Original: Root with ${original.contents.toSeq.size} top-level elements")
    println(s"Reconstructed: Module with ${reconstructed.contents.toSeq.size} top-level elements")

    // Perform deep comparison
    val results = DeepASTComparison.compareRootAndModule(original, reconstructed)

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
