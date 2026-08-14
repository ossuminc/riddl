/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.utils.{CommonOptions, pc}
import org.scalatest.TestData

/** Which INSTANCE a `tell` reaches, derived from the message's Id(target)-typed field.
  *
  * Structural derivation wins over naming the field at the send site because ONE message may be
  * told to two DIFFERENT processor types, and each target then needs its own address; structural
  * derivation gives each one for free.
  *
  * A missing address is a CompletenessWarning, not an Error, and that is a measurement rather
  * than a preference: riddl-models holds 7,556 tells against SEVEN Id-typed fields, so an Error
  * would redden essentially every model and is not mechanically migratable.
  */
class TellAddressingTest extends AbstractValidatingTest {

  private def diagnostics(src: String, origin: String): Messages =
    var captured: Messages = Messages.empty
    pc.withOptions(
      CommonOptions(showStyleWarnings = true, showWarnings = true, showCompletenessWarnings = true)
    ) { _ =>
      parseAndValidate(src, origin, shouldFailOnErrors = false) { (_, _, messages) =>
        captured = messages
        succeed
      }
    }
    captured

  private def model(shipFields: String, tellStmt: String): String =
    s"""domain Dom is {
       |  context Ctx is {
       |    command Go is { why: String } with { briefly "g" }
       |    command Ship is { $shipFields } with { briefly "s" }
       |    record R is { total: Integer } with { briefly "r" }
       |    entity Order is {
       |      state OS of record R is {
       |        handler OH is { on command Ship { do "ship" } } with { briefly "oh" }
       |      } with { briefly "os" }
       |    } with { briefly "e" }
       |    entity Caller is {
       |      state CS of record R is {
       |        handler CH is { on command Go { $tellStmt } } with { briefly "ch" }
       |      } with { briefly "cs" }
       |    } with { briefly "ce" }
       |  } with { briefly "c" }
       |} with { briefly "d" }
       |""".stripMargin

  "tell addressing" should {

    "derive the address from the single Id(target) field" in { (td: TestData) =>
      val msgs = diagnostics(
        model("orderId: Id(entity Order)",
              """let oid = initiate entity Order
                |            tell command Ship(orderId = oid) to entity Order""".stripMargin),
        "addr-derived"
      )
      msgs.justErrors mustBe empty
      msgs.map(_.message).mkString("\n") must not include "which Order instance"
    }

    "NOT mistake a reply-to field for the address" in { (td: TestData) =>
      // The property the whole scheme rests on: address and reply-to are told apart BY TYPE.
      // Id(entity Caller) is not a candidate for a tell to Order, so this must still be a
      // single unambiguous derivation and not an ambiguity error.
      val msgs = diagnostics(
        model("orderId: Id(entity Order), from: Id(entity Caller)",
              """let oid = initiate entity Order
                |            tell command Ship(orderId = oid, from = self.id) to entity Order""".stripMargin),
        "addr-replyto"
      )
      msgs.justErrors mustBe empty
    }

    "warn when the message carries no Id(target) field" in { (td: TestData) =>
      val text = diagnostics(
        model("why: String", """tell command Ship(why = "x") to entity Order"""),
        "addr-missing"
      ).filter(_.kind == Messages.CompletenessWarning).map(_.message).mkString("\n")
      text must include("Ship")
      text must include("Order")
    }

    "stay SILENT when the told message is a '???' stub" in { (td: TestData) =>
      // The standing '???' ruling: a body of '???' says "known to be incomplete", so its absent
      // fields must not be read as "no Id(target) field" -- every other check is skipped for it.
      diagnostics(
        model("???", """tell command Ship to entity Order"""),
        "addr-stub"
      ).filter(_.kind == Messages.CompletenessWarning)
        .map(_.message).mkString("\n") must not include "instance"
    }

    "REJECT an ambiguous derivation without 'by'" in { (td: TestData) =>
      val text = diagnostics(
        model("fromOrder: Id(entity Order), toOrder: Id(entity Order)",
              """tell command Ship(fromOrder = f, toOrder = t) to entity Order"""),
        "addr-ambiguous"
      ).justErrors.map(_.message).mkString("\n")
      text must include("fromOrder")
      text must include("toOrder")
    }

    "REJECT an ambiguous derivation nested inside a 'when' block" in { (td: TestData) =>
      // `checkTellAddressing` is called from `checkStatementScopes`'s `TellStatement` case, NOT
      // from `validateStatement`'s generic dispatch -- the latter never sees a statement nested in
      // a when/match/foreach body (those are FIELD-held, not `Contents`, so the generic Pass
      // traversal does not descend into them). This proves the check is reached at that depth too.
      val text = diagnostics(
        model("fromOrder: Id(entity Order), toOrder: Id(entity Order)",
              """when true then {
                |              tell command Ship(fromOrder = f, toOrder = t) to entity Order
                |            } end""".stripMargin),
        "addr-ambiguous-nested-when"
      ).justErrors.map(_.message).mkString("\n")
      text must include("fromOrder")
      text must include("toOrder")
    }

    "accept 'by' to disambiguate" in { (td: TestData) =>
      // f/t must resolve (unlike the ambiguity/by-wrong cases below, which only check message
      // substrings): `.justErrors mustBe empty` would otherwise also catch an unrelated
      // "value reference not resolved" error from the bare `f`/`t` operands, which is not what
      // this test is about.
      diagnostics(
        model("fromOrder: Id(entity Order), toOrder: Id(entity Order)",
              """let f = initiate entity Order
                |            let t = initiate entity Order
                |            tell command Ship(fromOrder = f, toOrder = t) to entity Order by toOrder""".stripMargin),
        "addr-by"
      ).justErrors mustBe empty
    }

    "REJECT 'by' naming a field that is not Id(target)" in { (td: TestData) =>
      val text = diagnostics(
        model("orderId: Id(entity Order), why: String",
              """tell command Ship(orderId = o, why = "x") to entity Order by why"""),
        "addr-by-wrong"
      ).justErrors.map(_.message).mkString("\n")
      text must include("why")
    }

    "stay SILENT for a repository target" in { (td: TestData) =>
      // A repository is a singleton, reached by path -- there is nothing to distinguish, so
      // the diagnostic is entity-only even though the MECHANISM is uniform.
      val src =
        """domain Dom is {
          |  context Ctx is {
          |    command Save is { why: String } with { briefly "s" }
          |    command Go is { why: String } with { briefly "g" }
          |    record R is { total: Integer } with { briefly "r" }
          |    repository Inv is {
          |      handler IH is { on command Save { do "save" } } with { briefly "ih" }
          |    } with { briefly "repo" }
          |    entity Caller is {
          |      state CS of record R is {
          |        handler CH is {
          |          on command Go { tell command Save(why = "x") to repository Inv }
          |        } with { briefly "ch" }
          |      } with { briefly "cs" }
          |    } with { briefly "ce" }
          |  } with { briefly "c" }
          |} with { briefly "d" }
          |""".stripMargin
      diagnostics(src, "addr-repo")
        .filter(_.kind == Messages.CompletenessWarning)
        .map(_.message).mkString("\n") must not include "instance"
    }
  }
}
