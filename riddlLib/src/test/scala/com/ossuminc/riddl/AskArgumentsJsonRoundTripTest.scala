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

/** `ask query Q(f = v) of P` (2026-11-18): `AskValueDto` carries the query's field values as
  * `args`, written only when there are some. A fixed point alone cannot prove the args survive (a
  * consistently dropped field is still a fixed point), so this also inspects the JSON and the
  * rebuilt AST, and reads a pre-2.8.0 document that has no `args` key.
  */
class AskArgumentsJsonRoundTripTest extends AnyWordSpec with Matchers {

  private val model =
    """domain D is {
      |  context C is {
      |    command Add is { code: String }
      |    query FindListing replies result ListingFound is { code: String }
      |    query Everything replies result ListingFound is { ??? }
      |    result ListingFound is { onMenu: Boolean }
      |    entity Menu is { ??? }
      |    entity Order is {
      |      handler H is {
      |        on add: command Add is {
      |          let withArgs: type ListingFound = ask query FindListing(code = add.code) of entity Menu
      |          let bare: type ListingFound = ask query Everything of entity Menu
      |        }
      |      }
      |    }
      |  }
      |}
      |""".stripMargin

  private def ask(root: Root, name: String): Ask =
    Finder(root)
      .recursiveFindByType[LetStatement]
      .collectFirst { case LetStatement(_, id, _, a: Ask) if id.value == name => a }
      .getOrElse(fail(s"no `let $name = ask ...` found"))

  private def parsed: Root = RiddlLib.parseString(model) match
    case RiddlResult.Success(root) => root
    case RiddlResult.Failure(errs) => fail(s"parse failed: $errs")

  private def fromJson(json: String): Root = RiddlLib.parseJson(json) match
    case RiddlResult.Success(root) => root
    case RiddlResult.Failure(errs) => fail(s"parseJson failed: $errs")

  "An ask's arguments in JSON" should {
    "be a fixed point" in {
      val json1 = RiddlLib.root2Json(parsed)
      RiddlLib.root2Json(fromJson(json1)) mustBe json1
    }

    "be written for an ask with arguments and omitted for a bare one" in {
      val asks = ujson.read(RiddlLib.root2Json(parsed)).toString
      asks must include("\"value\":\"ask\"")
      // One ask carries args, holding the named value; the bare one carries none.
      "\"args\":\\[\\{\"name\":\"code\"".r.findAllIn(asks).size mustBe 1
    }

    "rebuild the arguments in the AST" in {
      val root = fromJson(RiddlLib.root2Json(parsed))
      val a = ask(root, "withArgs")
      a.args.map(_.name.map(_.value)) mustBe Seq(Some("code"))
      a.args.head.value.format mustBe "add.code"
      ask(root, "bare").args mustBe empty
    }

    "read a pre-2.8.0 ask, which has no args key, as a bare ask" in {
      val json = RiddlLib.root2Json(parsed)
      val stripped = ujson.read(json)
      def strip(v: ujson.Value): Unit = v match
        case o: ujson.Obj =>
          if o.value.get("value").contains(ujson.Str("ask")) then o.value.remove("args")
          o.value.values.foreach(strip)
        case a: ujson.Arr => a.value.foreach(strip)
        case _            => ()
      strip(stripped)
      stripped.toString must not include ("\"args\":[{\"name\":\"code\"")
      ask(fromJson(stripped.toString), "withArgs").args mustBe empty
    }
  }
}
