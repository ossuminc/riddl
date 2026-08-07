/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{Contents, *}
import com.ossuminc.riddl.language.bast.{BASTLoader, BASTReader}
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.passes.prettify.{PrettifyOutput, PrettifyPass}
import com.ossuminc.riddl.passes.transforms.FlattenPass
import com.ossuminc.riddl.utils.pc
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.nio.file.{Files, Path}

/** S61-2: import-loading maturation.
  *
  * A load (`import`) plucks definitions out of an already-compiled `.bast` file. Loading populates
  * the `BASTImport` WRAPPER only — nothing is spliced into the enclosing container until an
  * explicit flatten runs. These tests pin down the gaps slice 2 closed:
  *
  *   - the full form parses at all (`Keywords.import_` ends in a cut, so the old `selective | full`
  *     choice could never fall through to the full form),
  *   - all three surface forms load, including `im`+`port domain X from ...`, which used to hit a
  *     stub that returned a `NotImplemented` placeholder Domain,
  *   - `parseString` loads (it previously did not, so every string-parsed model silently kept empty
  *     wrappers),
  *   - the walk finds wrappers at ANY depth (it used to stop at Root -> Domain -> Context, so one
  *     inside a `module` was never loaded),
  *   - flatten makes the imported definitions permanent and they still resolve afterwards.
  */
class BASTImportLoadingTest extends AnyWordSpec with Matchers {

  /** The library that gets compiled to `.bast` and loaded by every case below. */
  private val librarySource: String =
    """domain Lib is {
      |  type Money is Number
      |  context Accounts is {
      |    type Ledger is String
      |  }
      |}
      |""".stripMargin

  private def parse(src: String, origin: String): Root =
    TopLevelParser.parseInput(RiddlParserInput(src, origin)) match
      case Right(root) => root
      case Left(msgs)  => fail(s"parse of $origin failed:\n${msgs.format}")

  /** Compile `source` to a Module-rooted `.bast` file and return its path. */
  private def compileToBast(source: String, name: String): Path =
    val root = parse(source, s"$name-source")
    val written = Pass.runThesePasses(PassInput(root), Seq(BASTWriterPass.creator()))
    val bytes = written
      .outputOf[BASTOutput](BASTWriterPass.name)
      .getOrElse(fail("BASTWriterPass produced no output"))
      .bytes
    val dir = Files.createTempDirectory(s"bast-load-$name")
    val file = dir.resolve("lib.bast")
    Files.write(file, bytes)
    file
  end compileToBast

  private def withLibrary(name: String)(f: Path => Unit): Unit =
    val file = compileToBast(librarySource, name)
    try f(file)
    finally
      Files.deleteIfExists(file)
      Files.deleteIfExists(file.getParent)
    end try
  end withLibrary

  private def theOnlyImport(container: Container[?]): BASTImport =
    BASTLoader.getImports(container) match
      case Seq(one) => one
      case other    => fail(s"expected exactly one load directive, got ${other.size}")

  private val kw = "im" + "port"

  "BAST load directives" should {

    "load every top-level definition for the full form" in {
      withLibrary("full") { lib =>
        val root = parse(
          s"""$kw "${lib.toAbsolutePath}"
             |domain App is { ??? }
             |""".stripMargin,
          "full"
        )
        val bi = theOnlyImport(root)
        bi.contents.toSeq.collect { case d: Domain => d.id.value } mustBe Seq("Lib")
      }
    }

    "load exactly one definition, renamed, for the selective-by-kind form" in {
      withLibrary("selective") { lib =>
        val root = parse(
          s"""domain App is {
             |  context C is {
             |    $kw type Money from "${lib.toAbsolutePath}" as Cash
             |  }
             |}
             |""".stripMargin,
          "selective"
        )
        val bi = theOnlyImport(root)
        bi.contents.toSeq.collect { case t: Type => t.id.value } mustBe Seq("Cash")
      }
    }

    // Form A used to be parsed by `importDef`, a stub that discarded the path and produced a
    // Domain named "NotImplemented". It is now a selective load of one domain.
    "load exactly the named domain for the domain-from form" in {
      withLibrary("domain-from") { lib =>
        val root = parse(
          s"""domain App is {
             |  $kw domain Lib from "${lib.toAbsolutePath}"
             |}
             |""".stripMargin,
          "domain-from"
        )
        val bi = theOnlyImport(root)
        val domains = bi.contents.toSeq.collect { case d: Domain => d }
        domains.map(_.id.value) mustBe Seq("Lib")
        domains.head.types.map(_.id.value) must contain("Money")
      }
    }

    // Regression: parseString skipped loadBASTImports entirely, so wrappers stayed empty.
    "load when the model is parsed from a string" in {
      withLibrary("parse-string") { lib =>
        val src =
          s"""$kw "${lib.toAbsolutePath}"
             |domain App is { ??? }
             |""".stripMargin
        TopLevelParser.parseString(src) match
          case Left(msgs) => fail(s"parseString failed:\n${msgs.format}")
          case Right(root) =>
            val bi = theOnlyImport(root)
            bi.contents.toSeq.collect { case d: Domain => d.id.value } mustBe Seq("Lib")
      }
    }

    // The old walk only descended Root -> Domain -> Context, so neither of these was ever found.
    "find and load one nested inside a module" in {
      withLibrary("in-module") { lib =>
        val root = parse(
          s"""module M is {
             |  $kw "${lib.toAbsolutePath}"
             |}
             |""".stripMargin,
          "in-module"
        )
        val bi = theOnlyImport(root)
        bi.contents.toSeq.collect { case d: Domain => d.id.value } mustBe Seq("Lib")
      }
    }

    "find and load one nested deeper than a Context" in {
      withLibrary("deep") { lib =>
        val root = parse(
          s"""module Outer is {
             |  module Inner is {
             |    domain D is {
             |      context C is {
             |        $kw type Money from "${lib.toAbsolutePath}"
             |      }
             |    }
             |  }
             |}
             |""".stripMargin,
          "deep"
        )
        val bi = theOnlyImport(root)
        bi.contents.toSeq.collect { case t: Type => t.id.value } mustBe Seq("Money")
      }
    }
  }

  "Flattening a loaded model" should {

    "make the imported definitions permanent at the site and still resolvable" in {
      withLibrary("flatten") { lib =>
        val root = parse(
          s"""domain App is {
             |  $kw type Money from "${lib.toAbsolutePath}"
             |  context C is {
             |    type Amount is App.Money
             |  }
             |}
             |""".stripMargin,
          "flatten"
        )

        // Two different questions, two different answers, and the split is deliberate.
        //
        // READING: `types` reports the imported type. A client asking what types a domain has
        // wants all of them; whether one arrived inline, by include, or by import is riddl's
        // bookkeeping, not the client's. (This assertion used to be `mustNot contain`.)
        root.domains.head.types.map(_.id.value) must contain("Money")

        // RESOLVING: a reference to it still does NOT resolve before flatten. Loading fills
        // wrappers; a self-contained model requires an explicit flatten. That contract is
        // unchanged -- the symbol table is built by traversal, not by these accessors.
        Pass
          .runThesePasses(PassInput(root), Pass.standardPasses)
          .messages
          .filter(_.kind.isError)
          .map(_.message)
          .mkString must include("App.Money")

        Pass.runThesePasses(PassInput(root), Seq(FlattenPass.creator(PassOptions.empty)))

        // After flatten it is a real member of the domain and no wrapper survives.
        root.domains.head.types.map(_.id.value) must contain("Money")
        BASTLoader.getImports(root) mustBe empty

        // ...and the model is now self-contained: it validates on its own, no .bast file needed,
        // with the reference to the imported type resolving at the site of the load directive.
        val again = Pass.runThesePasses(PassInput(root), Pass.standardPasses)
        again.messages.filter(_.kind.isError) mustBe empty
      }
    }

    // `definitions` joined the include-transparent accessors on 2026-08-06 (synapify's task), so
    // it now answers the READING question above for imports as well, exactly like `types` does.
    // `directDefinitions` is the literal reading that ResolutionPass keeps -- which is what lets
    // the resolve-side contract pinned just above stay true while the read side changes.
    "report an imported definition through definitions but not directDefinitions" in {
      withLibrary("transparent") { lib =>
        val root = parse(
          s"""domain App is {
             |  $kw type Money from "${lib.toAbsolutePath}"
             |}
             |""".stripMargin,
          "transparent"
        )
        val app = root.domains.head

        // READING: transparent, so the imported type is a definition of the domain.
        app.contents.definitions.map(_.id.value) must contain("Money")

        // PROVENANCE: literal, so only the wrapper is a direct child -- nothing inside it.
        app.contents.directDefinitions.map(_.id.value) mustNot contain("Money")

        // And the wrapper is still there for the tooling that cares which is which.
        BASTLoader.getImports(app) mustNot be(empty)
      }
    }
  }

  "A model containing a load directive" should {

    "round-trip through prettify with the loaded content inlined when flattened" in {
      withLibrary("prettify") { lib =>
        val root = parse(
          s"""$kw "${lib.toAbsolutePath}"
             |domain App is { ??? }
             |""".stripMargin,
          "prettify"
        )
        val creators = Pass.standardPasses :+ { (in: PassInput, out: PassesOutput) =>
          PrettifyPass(in, out, PrettifyPass.Options(flatten = true, inputDir = ""))
        }
        val pretty = Pass
          .runThesePasses(PassInput(root), creators)
          .outputs
          .outputOf[PrettifyOutput](PrettifyPass.name)
          .getOrElse(fail("PrettifyPass produced no output"))
          .state
          .filesAsString

        parse(pretty, "regen").domains.map(_.id.value) must contain allOf ("Lib", "App")
      }
    }

    "round-trip through BAST preserving the directive" in {
      withLibrary("bast") { lib =>
        val root = parse(
          s"""$kw type Money from "${lib.toAbsolutePath}" as Cash
             |domain App is { ??? }
             |""".stripMargin,
          "bast"
        )
        val bytes = Pass
          .runThesePasses(PassInput(root), Seq(BASTWriterPass.creator()))
          .outputOf[BASTOutput](BASTWriterPass.name)
          .getOrElse(fail("BASTWriterPass produced no output"))
          .bytes

        BASTReader.read(bytes) match
          case Left(msgs) => fail(s"BAST read failed:\n${msgs.format}")
          case Right(module) =>
            val bi = theOnlyImport(module)
            bi.kindOpt mustBe Some("type")
            bi.selector.map(_.value) mustBe Some("Money")
            bi.alias.map(_.value) mustBe Some("Cash")
            // Contents are deliberately not serialized: the .bast file is the source of truth and
            // is re-read on load.
            bi.contents.isEmpty mustBe true
      }
    }
  }
}
