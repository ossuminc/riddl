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
                          |      on event a.AEvent { prompt "record from a" }
                          |      on event b.BEvent { prompt "record from b" }
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
                nebula.domains.find(_.id.value == "d").getOrElse(fail("domain d missing after BAST"))
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
          |        on command Cmd { prompt "handle" }
          |        on event Evt { prompt "note" }
          |        on activate { prompt "rehydrate" }
          |        on passivate { prompt "evict" }
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
