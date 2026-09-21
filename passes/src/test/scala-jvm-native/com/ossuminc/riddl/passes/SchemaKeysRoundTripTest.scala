/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{Finder, toSeq}
import com.ossuminc.riddl.language.bast.BASTReader
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.passes.prettify.{PrettifyOutput, PrettifyPass}
import com.ossuminc.riddl.utils.pc
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** B3 (2026-09-21): `key on field F` and `of X as R with history` on the prettify and BAST
  * surfaces (schema node grows two trailing sequences; revision 28).
  */
class SchemaKeysRoundTripTest extends AnyWordSpec with Matchers {

  private val src =
    """domain Kitchen is {
      |  context Tickets is {
      |    record StoredTicket is { ticketId: String, status: String } with { briefly "t" }
      |    record StoredStation is { stationId: String } with { briefly "s" }
      |    repository Store is {
      |      schema S is relational
      |        of tickets as record Tickets.StoredTicket with history
      |        of stations as record Tickets.StoredStation
      |        key on field Tickets.StoredTicket.ticketId
      |        key on field Tickets.StoredStation.stationId
      |        index on field Tickets.StoredTicket.status
      |      with { briefly "keyed" }
      |    } with { briefly "r" }
      |  } with { briefly "c" }
      |} with { briefly "d" }
      |""".stripMargin

  private def parse(text: String, origin: String): Root =
    TopLevelParser.parseInput(RiddlParserInput(text, origin)) match
      case Right(root) => root
      case Left(msgs)  => fail(s"parse of $origin failed:\n${msgs.format}")

  private def prettify(root: Root): String =
    Pass
      .runThesePasses(
        PassInput(root),
        Pass.standardPasses :+ { (in: PassInput, out: PassesOutput) =>
          PrettifyPass(in, out, PrettifyPass.Options(flatten = true, inputDir = ""))
        }
      )
      .outputs
      .outputOf[PrettifyOutput](PrettifyPass.name)
      .getOrElse(fail("no prettify output"))
      .state
      .filesAsString

  private def shape(root: Container[?]): (Seq[String], Seq[String], Seq[String]) =
    val s = Finder(root.contents).recursiveFindByType[Schema].head
    (s.keys.map(_.pathId.format), s.history.map(_.value), s.indices.map(_.pathId.format))

  private val expected = (
    Seq("Tickets.StoredTicket.ticketId", "Tickets.StoredStation.stationId"),
    Seq("tickets"),
    Seq("Tickets.StoredTicket.status")
  )

  "schema keys and history" should {

    "parse (control)" in { shape(parse(src, "src")) mustBe expected }

    "PRETTIFY to the same spelling and re-parse equal" in {
      val pretty = prettify(parse(src, "src"))
      pretty must include("of tickets as record Tickets.StoredTicket with history")
      pretty must include("key on field Tickets.StoredTicket.ticketId")
      pretty must include("index on field Tickets.StoredTicket.status")
      shape(parse(pretty, "regen")) mustBe expected
      // and the schema's own `with { }` survived beside the data line's `with history`
      pretty must include("briefly \"keyed\"")
    }

    "survive BAST at revision 28" in {
      val written = Pass
        .runThesePasses(PassInput(parse(src, "src")), Seq(BASTWriterPass.creator()))
        .outputOf[BASTOutput](BASTWriterPass.name)
        .getOrElse(fail("no BAST output"))
      BASTReader.read(written.bytes) match
        case Right(back)  => shape(back) mustBe expected
        case Left(errors) => fail(s"BAST read failed: ${errors.format}")
      end match
    }
  }
}
