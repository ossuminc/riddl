/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{Finder, Messages, RuleId}
import com.ossuminc.riddl.language.Messages.Message
import com.ossuminc.riddl.language.bast.BASTReader
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.passes.{Pass, PassInput, PassesOutput, Riddl}
import com.ossuminc.riddl.passes.prettify.{PrettifyOutput, PrettifyPass}
import com.ossuminc.riddl.passes.{BASTOutput, BASTWriterPass}
import com.ossuminc.riddl.utils.{CommonOptions, pc}

import org.scalatest.TestData

/** riddl-models, 2026-11-18: `ask` named its query but could not construct it, so an asker could
  * not say WHAT it asked, and inside a `foreach` the model was ambiguous about which element. `ask
  * query Q(f = v) of P` now carries the query's field values, checked by the Constructor rules. A
  * bare ask of a query WITH fields is a Deprecation until 3.0 (Reid, 2026-11-18).
  */
class AskArgumentsTest extends AbstractValidatingTest {

  /** `Order` asks `Menu` whether the code on an `Add` is on the menu; `askExpr` replaces the ask. */
  private def model(askExpr: String, extra: String = "", orderClause: String = ""): String =
    s"""domain D is { context C is {
       |  command Add is { code: String }
       |  command AddAll is { codes: String* }
       |  query FindListing replies result ListingFound is { code: String }
       |  result ListingFound is { onMenu: Boolean }
       |  query Everything replies result ListingFound is { ??? }
       |  $extra
       |  entity Menu is {
       |    inlet MenuIn is query C.FindListing
       |    outlet MenuOut is result C.ListingFound
       |    handler MH is {
       |      on query C.FindListing { reply result C.ListingFound(onMenu = true) }
       |      on query C.Everything { reply result C.ListingFound(onMenu = true) }
       |    }
       |  }
       |  entity Order is {
       |    inlet OrderIn is command C.Add
       |    inlet OrderResIn is result C.ListingFound
       |    outlet OrderOut is query C.FindListing
       |    handler OH is {
       |      on add: command C.Add is {
       |        let listing: type C.ListingFound = $askExpr
       |        when listing.onMenu then do "add it" else error "not on the menu" end
       |      }
       |      $orderClause
       |    }
       |  }
       |  connector Q is { from outlet C.Order.OrderOut to inlet C.Menu.MenuIn }
       |  connector R is { from outlet C.Menu.MenuOut to inlet C.Order.OrderResIn }
       |} }
       |""".stripMargin

  private val goodAsk = "ask query C.FindListing(code = add.code) of entity C.Menu"

  private def messages(src: String, td: TestData, tips: Boolean = false): Seq[Message] =
    var out: Seq[Message] = Seq.empty
    pc.withOptions(CommonOptions.default.copy(provideTips = tips)) { _ =>
      out = Riddl.parseAndValidate(RiddlParserInput(src, td), shouldFailOnError = false) match
        case Right(result) => result.messages
        case Left(msgs)    => msgs
    }
    out

  private def of(msgs: Seq[Message], rule: RuleId): Seq[Message] = msgs.filter(_.ruleId.contains(rule))
  private def errors(msgs: Seq[Message]): Seq[Message] = msgs.filter(_.kind == Messages.Error)

  private def parse(src: String, origin: String): Root =
    TopLevelParser.parseInput(RiddlParserInput(src, origin)) match
      case Right(root) => root
      case Left(msgs)  => fail(s"parse of $origin failed:\n${msgs.format}")

  private def theAsk(root: Container[?]): Ask =
    Finder(root).recursiveFindByType[LetStatement].collectFirst {
      case LetStatement(_, id, _, a: Ask) if id.value == "listing" => a
    }.getOrElse(fail("no `let listing = ask ...` found"))

  "An ask with arguments" should {
    "parse, resolve and validate clean" in { (td: TestData) =>
      val msgs = messages(model(goodAsk), td)
      withClue(msgs.map(_.format).mkString("\n")) {
        errors(msgs) mustBe empty
        of(msgs, RuleId.AskBareQuery) mustBe empty
      }
      val ask = theAsk(parse(model(goodAsk), td.name))
      ask.args.map(_.name.map(_.value)) mustBe Seq(Some("code"))
      ask.format mustBe "ask query C.FindListing(code = add.code) of entity C.Menu"
    }

    "report a field the query does not have, through the constructor rules" in { (td: TestData) =>
      val msgs = messages(model("ask query C.FindListing(kode = add.code) of entity C.Menu"), td)
      of(msgs, RuleId.NotAField).size mustBe 1
      of(msgs, RuleId.ConstructorMissingFields).size mustBe 1
    }

    "report a value of the wrong type" in { (td: TestData) =>
      val msgs = messages(model("ask query C.FindListing(code = true) of entity C.Menu"), td)
      val mismatches = errors(msgs).filter(_.message.contains("Field 'code' of Query 'FindListing'"))
      mismatches.size mustBe 1
    }

    "report a field supplied twice" in { (td: TestData) =>
      val src = model("ask query C.FindListing(code = add.code, code = add.code) of entity C.Menu")
      of(messages(src, td), RuleId.ArgumentDuplicated).size mustBe 1
    }

    "resolve its argument values in scope, and report one that is not" in { (td: TestData) =>
      val msgs = messages(model("ask query C.FindListing(code = nope.code) of entity C.Menu"), td)
      errors(msgs).exists(_.message.contains("'nope.code'")) mustBe true
    }

    "say which element it asks about inside a foreach (the riddl-models ambiguity)" in {
      (td: TestData) =>
        val clause =
          """on all: command C.AddAll is {
            |        foreach code in field C.AddAll.codes {
            |          let found: type C.ListingFound = ask query C.FindListing(code = code) of entity C.Menu
            |          when found.onMenu then do "add it" end
            |        }
            |      }""".stripMargin
        val msgs = messages(model(goodAsk, orderClause = clause), td)
        withClue(msgs.map(_.format).mkString("\n")) {
          // The whole model must be error-free: a parse failure would otherwise make this vacuous.
          errors(msgs) mustBe empty
          of(msgs, RuleId.AskBareQuery) mustBe empty
        }
        // ...and the element really is the argument, so the check above was not vacuous.
        val found = Finder(parse(model(goodAsk, orderClause = clause), td.name))
          .recursiveFindByType[LetStatement]
          .collectFirst { case LetStatement(_, id, _, a: Ask) if id.value == "found" => a }
        found.map(_.args.head.value.format) mustBe Some("code")
    }

    "be reachable by Finder through its arguments" in { (td: TestData) =>
      val refs = Finder(parse(model(goodAsk), td.name).contents).recursiveFindByType[ValueRef]
      refs.map(_.path.format) must contain("add.code")
    }
  }

  "A bare ask" should {
    "draw a Deprecation, not an Error, when the query has fields" in { (td: TestData) =>
      // Suggestions are stripped unless tips are on (`Accumulator.add`).
      val msgs = messages(model("ask query C.FindListing of entity C.Menu"), td, tips = true)
      val dep = of(msgs, RuleId.AskBareQuery)
      dep.size mustBe 1
      dep.head.kind mustBe Messages.Deprecation
      dep.head.message must include("will be removed in 3.0")
      dep.head.suggestion must include("ask query C.FindListing(code = …) of entity C.Menu")
      errors(msgs) mustBe empty
    }

    "draw nothing when the query has no fields" in { (td: TestData) =>
      val msgs = messages(model("ask query C.Everything of entity C.Menu"), td)
      of(msgs, RuleId.AskBareQuery) mustBe empty
    }
  }

  "The ask's arguments" should {
    "survive prettify, including a nested typed hole" in { (td: TestData) =>
      val src = model("""ask query C.FindListing(code = prompt("the code") as String) of entity C.Menu""")
      val root = parse(src, td.name)
      val creators = Pass.standardPasses :+ { (in: PassInput, out: PassesOutput) =>
        PrettifyPass(in, out, PrettifyPass.Options(flatten = true, inputDir = ""))
      }
      val text = Pass
        .runThesePasses(PassInput(root), creators)
        .outputs
        .outputOf[PrettifyOutput](PrettifyPass.name)
        .getOrElse(fail("PrettifyPass produced no output"))
        .state
        .filesAsString
      text must include("""ask query C.FindListing(code = prompt("the code") as String) of entity C.Menu""")
      val before = theAsk(root)
      val after = theAsk(parse(text, "prettified"))
      after.format mustBe before.format
      after.args.map(_.value.getClass) mustBe before.args.map(_.value.getClass)
      after.args.head.value match
        case pv: PromptValue => pv.typeRef.map(_.pathId.format) mustBe Some("String")
        case other           => fail(s"expected a PromptValue argument, got $other")
    }

    "survive a BAST round trip, with a bare ask after it still read correctly" in { (td: TestData) =>
      val clause =
        """on all: command C.AddAll is {
          |        let everything: type C.ListingFound = ask query C.Everything of entity C.Menu
          |        do "after the bare ask"
          |      }""".stripMargin
      val root = parse(model(goodAsk, orderClause = clause), td.name)
      val bytes = Pass
        .runThesePasses(PassInput(root), Seq(BASTWriterPass.creator()))
        .outputOf[BASTOutput](BASTWriterPass.name)
        .getOrElse(fail("BASTWriterPass produced no output"))
        .bytes
      val decoded = BASTReader(bytes).read() match
        case Right(m)   => m
        case Left(msgs) => fail(s"BAST round trip failed:\n${msgs.format}")
      theAsk(decoded).format mustBe theAsk(root).format
      val lets = Finder(decoded.contents).recursiveFindByType[LetStatement]
      lets.find(_.identifier.value == "everything").map(_.expression.format) mustBe
        Some("ask query C.Everything of entity C.Menu")
      Finder(decoded.contents).recursiveFindByType[DoStatement].map(_.text) must
        contain("after the bare ask")
    }

    "be walked by R5: system.now in an ask argument inside a fold is still reported" in {
      (td: TestData) =>
        val src =
          """domain D is { context C is {
            |  query Lookup replies result Found is { at: TimeStamp }
            |  result Found is { ok: Boolean }
            |  entity Other is { handler OH is { on query C.Lookup { reply result C.Found(ok = true) } } }
            |  event-sourced entity Order is {
            |    record Fields is { total: Integer }
            |    command Place yields event Placed is { total: Integer }
            |    event Placed is { total: Integer }
            |    state Main of record Order.Fields is {
            |      handler H is {
            |        on command Order.Place { yield event Order.Placed(total = 1) }
            |        on ev: event Order.Placed {
            |          let f: type C.Found = ask query C.Lookup(at = system.now) of entity C.Other
            |          set field Main.total to ev.total
            |        }
            |      }
            |    }
            |  }
            |} }
            |""".stripMargin
        val msgs = messages(src, td)
        withClue(msgs.map(_.format).mkString("\n")) {
          of(msgs, RuleId.EventSourcedFoldNondeterministic).exists(_.message.contains("system.now")) mustBe
            true
        }
    }
  }
}
