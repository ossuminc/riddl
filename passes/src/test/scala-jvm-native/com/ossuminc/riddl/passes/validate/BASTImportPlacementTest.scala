/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{Contents, *}
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.passes.{BASTOutput, BASTWriterPass, Pass, PassInput}
import com.ossuminc.riddl.utils.pc
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.nio.file.{Files, Path}

/** S61-2: sense-at-location.
  *
  * A definition plucked out of a `.bast` file must be structurally legal WHERE THE DIRECTIVE SITS,
  * exactly as if it had been written there. A Context is not legal at Root; a Domain is not legal
  * inside a Context. Getting this wrong builds a tree the parser would have rejected, and a
  * subsequent flatten would make it permanent — so an illegal placement is an Error.
  */
class BASTImportPlacementTest extends AnyWordSpec with Matchers {

  /** Holds one of each shape the cases below need to place well or badly. */
  private val librarySource: String =
    """domain Lib is {
      |  type Money is Number
      |  context Accounts is {
      |    type Ledger is String
      |  }
      |}
      |""".stripMargin

  private val kw = "im" + "port"
  private val complaint = "is not allowed at this location"

  private def parse(src: String, origin: String): Root =
    TopLevelParser.parseInput(RiddlParserInput(src, origin)) match
      case Right(root) => root
      case Left(msgs)  => fail(s"parse of $origin failed:\n${msgs.format}")

  private def withLibrary(name: String)(f: Path => Unit): Unit =
    val root = parse(librarySource, s"$name-library")
    val bytes = Pass
      .runThesePasses(PassInput(root), Seq(BASTWriterPass.creator()))
      .outputOf[BASTOutput](BASTWriterPass.name)
      .getOrElse(fail("BASTWriterPass produced no output"))
      .bytes
    val dir = Files.createTempDirectory(s"bast-placement-$name")
    val file = dir.resolve("lib.bast")
    Files.write(file, bytes)
    try f(file)
    finally
      Files.deleteIfExists(file)
      Files.deleteIfExists(dir)
    end try
  end withLibrary

  /** The placement complaints raised by the standard passes over `source`. */
  private def placementErrors(source: String, origin: String): Seq[String] =
    Pass
      .runThesePasses(PassInput(parse(source, origin)), Pass.standardPasses)
      .messages
      .filter(m => m.kind.isError && m.message.contains(complaint))
      .map(_.message)

  "Sense-at-location" should {

    "reject a Context loaded at Root level" in {
      withLibrary("context-at-root") { lib =>
        val errors = placementErrors(
          s"""$kw context Accounts from "${lib.toAbsolutePath}"
             |domain App is { ??? }
             |""".stripMargin,
          "context-at-root"
        )
        errors.size mustBe 1
        errors.head must include("imported Context 'Accounts'")
      }
    }

    "accept a Domain loaded at Root level" in {
      withLibrary("domain-at-root") { lib =>
        placementErrors(
          s"""$kw domain Lib from "${lib.toAbsolutePath}"
             |domain App is { ??? }
             |""".stripMargin,
          "domain-at-root"
        ) mustBe empty
      }
    }

    "reject a Domain loaded inside a Context" in {
      withLibrary("domain-in-context") { lib =>
        // The full form brings in everything the library's root Module holds — here, a Domain,
        // which a Context may not contain.
        val errors = placementErrors(
          s"""domain App is {
             |  context C is {
             |    $kw "${lib.toAbsolutePath}"
             |  }
             |}
             |""".stripMargin,
          "domain-in-context"
        )
        errors.size mustBe 1
        errors.head must include("imported Domain 'Lib'")
      }
    }

    "accept a Type loaded inside a Context" in {
      withLibrary("type-in-context") { lib =>
        placementErrors(
          s"""domain App is {
             |  context C is {
             |    $kw type Money from "${lib.toAbsolutePath}"
             |  }
             |}
             |""".stripMargin,
          "type-in-context"
        ) mustBe empty
      }
    }

    "accept anything at all inside a module, which is a flat collection" in {
      withLibrary("in-module") { lib =>
        placementErrors(
          s"""module M is {
             |  $kw context Accounts from "${lib.toAbsolutePath}"
             |}
             |""".stripMargin,
          "in-module"
        ) mustBe empty
      }
    }
  }
}
