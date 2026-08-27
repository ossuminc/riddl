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
  * A missing address is a CompletenessWarning, not an Error, and that is a measurement rather than
  * a preference: riddl-models holds 7,556 tells against SEVEN Id-typed fields, so an Error would
  * redden essentially every model and is not mechanically migratable.
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
        model(
          "orderId: Id(entity Order)",
          """let oid = initiate entity Order
                |            tell command Ship(orderId = oid) to entity Order""".stripMargin
        ),
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
        model(
          "orderId: Id(entity Order), from: Id(entity Caller)",
          """let oid = initiate entity Order
                |            tell command Ship(orderId = oid, from = self.id) to entity Order""".stripMargin
        ),
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
        .map(_.message)
        .mkString("\n") must not include "instance"
    }

    "REJECT an ambiguous derivation without 'by'" in { (td: TestData) =>
      val text = diagnostics(
        model(
          "fromOrder: Id(entity Order), toOrder: Id(entity Order)",
          """tell command Ship(fromOrder = f, toOrder = t) to entity Order"""
        ),
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
        model(
          "fromOrder: Id(entity Order), toOrder: Id(entity Order)",
          """when true then {
                |              tell command Ship(fromOrder = f, toOrder = t) to entity Order
                |            } end""".stripMargin
        ),
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
        model(
          "fromOrder: Id(entity Order), toOrder: Id(entity Order)",
          """let f = initiate entity Order
                |            let t = initiate entity Order
                |            tell command Ship(fromOrder = f, toOrder = t) to entity Order by toOrder""".stripMargin
        ),
        "addr-by"
      ).justErrors mustBe empty
    }

    "REJECT 'by' naming a field that is not Id(target)" in { (td: TestData) =>
      val text = diagnostics(
        model(
          "orderId: Id(entity Order), why: String",
          """tell command Ship(orderId = o, why = "x") to entity Order by why"""
        ),
        "addr-by-wrong"
      ).justErrors.map(_.message).mkString("\n")
      text must include("why")
    }

    "derive the address through an ALIAS-declared message type" in { (td: TestData) =>
      // Regression for a review finding: `Type.isEmpty` (= `Container.isEmpty` over `Type.contents`)
      // is vacuously TRUE for an `AliasedTypeExpression` -- `Type.contents` returns `Seq.empty` for
      // anything that isn't directly an Aggregation/AggregateUseCaseTypeExpression/Enumeration --
      // so gating the whole check on `mt.isEmpty` treated EVERY alias-declared message as a `???`
      // stub and silently skipped it, no matter how many fields the aliased-to type had.
      val src =
        """domain Dom is {
          |  context Ctx is {
          |    command Shipment is { orderId: Id(entity Order) } with { briefly "sb" }
          |    command Ship is Shipment with { briefly "s" }
          |    command Go is { why: String } with { briefly "g" }
          |    record R is { total: Integer } with { briefly "r" }
          |    entity Order is {
          |      state OS of record R is {
          |        handler OH is { on command Shipment { do "ship" } } with { briefly "oh" }
          |      } with { briefly "os" }
          |    } with { briefly "e" }
          |    entity Caller is {
          |      state CS of record R is {
          |        handler CH is {
          |          on command Go { tell command Ship(orderId = "the order") to entity Order }
          |        } with { briefly "ch" }
          |      } with { briefly "ce" }
          |    } with { briefly "c" }
          |  } with { briefly "c" }
          |} with { briefly "d" }
          |""".stripMargin
      // The constructor form works here as of 2026-08-14. It could NOT before: `validateConstructor`
      // read only the DIRECT fields of `Ship` (`typ.typEx match { case ate: AggregateTypeExpression
      // => ate.fields; case _ => Seq.empty }`), found none through `command Ship is Shipment`, and
      // so misreported "'orderId' is not a field of Type 'Ship'". That gap is fixed -- it now goes
      // through the shared, cycle-guarded `aggregateFieldsOf` -- and this fixture uses the
      // constructor because the bare form became an Error the same day.
      val msgs = diagnostics(src, "addr-alias-derived")
      msgs.justErrors mustBe empty
      msgs.map(_.message).mkString("\n") must not include "which Order instance"
    }

    "NOT silently accept a garbage 'by' field for an ALIAS-declared message type" in {
      (td: TestData) =>
        // Same regression as above, from the OTHER side: before the fix, an alias-declared message
        // was treated as a stub and this whole check -- including 'by' validation -- never ran, so
        // 'by nonsense' was accepted with no diagnostic at all. This is the review finding's exact
        // failure scenario.
        val src =
          """domain Dom is {
          |  context Ctx is {
          |    command Shipment is {
          |      fromOrder: Id(entity Order), toOrder: Id(entity Order)
          |    } with { briefly "sb" }
          |    command Ship is Shipment with { briefly "s" }
          |    command Go is { why: String } with { briefly "g" }
          |    record R is { total: Integer } with { briefly "r" }
          |    entity Order is {
          |      state OS of record R is {
          |        handler OH is { on command Shipment { do "ship" } } with { briefly "oh" }
          |      } with { briefly "os" }
          |    } with { briefly "e" }
          |    entity Caller is {
          |      state CS of record R is {
          |        handler CH is {
          |          on command Go { tell command Ship to entity Order by nonsense }
          |        } with { briefly "ch" }
          |      } with { briefly "ce" }
          |    } with { briefly "c" }
          |  } with { briefly "c" }
          |} with { briefly "d" }
          |""".stripMargin
        val text = diagnostics(src, "addr-alias-by-wrong").justErrors.map(_.message).mkString("\n")
        text must include("nonsense")
    }

    "NOT be fooled by a foreign field typed 'Id' for a SAME-NAMED entity in another context" in {
      (td: TestData) =>
        // Regression for a review finding: the brief's Step 4 pseudocode matched candidates by the
        // last PATH SEGMENT'S NAME (`uid.entityPath.value.lastOption.contains(p.id.value)`), so two
        // entities named 'Order' in different contexts collided -- a field typed `Id(CtxA.Order)`
        // counted as an address for `entity CtxB.Order` merely because both paths end in "Order".
        // Here `foreignId` genuinely addresses `CtxA.Order`, not the tell's target `CtxB.Order`, so
        // it must NOT be picked up: the real (and only) candidate is `orderId`, and the tell must
        // resolve unambiguously without 'by'.
        val src =
          """domain Dom is {
            |  context CtxA is {
            |    command Noop is { why: String } with { briefly "n" }
            |    record RA is { total: Integer } with { briefly "ra" }
            |    entity Order is {
            |      state OS of record RA is {
            |        handler OH is { on command Noop { do "noop" } } with { briefly "oh" }
            |      } with { briefly "os" }
            |    } with { briefly "ea" }
            |  } with { briefly "ca" }
            |  context CtxB is {
            |    command Ship is {
            |      foreignId: Id(entity CtxA.Order), orderId: Id(entity CtxB.Order)
            |    } with { briefly "s" }
            |    command Go is { why: String } with { briefly "g" }
            |    record RB is { total: Integer } with { briefly "rb" }
            |    entity Order is {
            |      state OS of record RB is {
            |        handler OH is { on command Ship { do "ship" } } with { briefly "oh" }
            |      } with { briefly "os" }
            |    } with { briefly "eb" }
            |    entity Caller is {
            |      state CS of record RB is {
            |        handler CH is {
            |          on command Go {
            |            let foreignOid = initiate entity CtxA.Order
            |            let oid = initiate entity CtxB.Order
            |            tell command Ship(foreignId = foreignOid, orderId = oid) to entity CtxB.Order
            |          }
            |        } with { briefly "ch" }
            |      } with { briefly "ce" }
            |    } with { briefly "eb2" }
            |  } with { briefly "cb" }
            |} with { briefly "d" }
            |""".stripMargin
        val msgs = diagnostics(src, "addr-name-collision-genuine")
        msgs.justErrors mustBe empty
        msgs.map(_.message).mkString("\n") must not include "which CtxB.Order instance"
    }

    "NOT report a false ambiguity when both Id-typed fields address a FOREIGN same-named entity" in {
      (td: TestData) =>
        // The other half of the same regression: two fields both typed `Id(entity CtxA.Order)`
        // (neither addressing the tell's actual target, `entity CtxB.Order`) must NOT be reported
        // as an ambiguous derivation just because their last path segment matches the target's
        // name. The correct outcome is "no candidates" -- a CompletenessWarning, not an Error --
        // exactly as if the message carried no Id-typed field for the target at all.
        val src =
          """domain Dom is {
            |  context CtxA is {
            |    command Noop is { why: String } with { briefly "n" }
            |    record RA is { total: Integer } with { briefly "ra" }
            |    entity Order is {
            |      state OS of record RA is {
            |        handler OH is { on command Noop { do "noop" } } with { briefly "oh" }
            |      } with { briefly "os" }
            |    } with { briefly "ea" }
            |  } with { briefly "ca" }
            |  context CtxB is {
            |    command Ship is {
            |      fromForeign: Id(entity CtxA.Order), toForeign: Id(entity CtxA.Order)
            |    } with { briefly "s" }
            |    command Go is { why: String } with { briefly "g" }
            |    record RB is { total: Integer } with { briefly "rb" }
            |    entity Order is {
            |      state OS of record RB is {
            |        handler OH is { on command Ship { do "ship" } } with { briefly "oh" }
            |      } with { briefly "os" }
            |    } with { briefly "eb" }
            |    entity Caller is {
            |      state CS of record RB is {
            |        handler CH is {
            |          on command Go {
            |            let f = initiate entity CtxA.Order
            |            let t = initiate entity CtxA.Order
            |            tell command Ship(fromForeign = f, toForeign = t) to entity CtxB.Order
            |          }
            |        } with { briefly "ch" }
            |      } with { briefly "ce" }
            |    } with { briefly "eb2" }
            |  } with { briefly "cb" }
            |} with { briefly "d" }
            |""".stripMargin
        val msgs = diagnostics(src, "addr-name-collision-foreign")
        val errText = msgs.justErrors.map(_.message).mkString("\n")
        errText must not include "ambiguous"
        val warnText =
          msgs.filter(_.kind == Messages.CompletenessWarning).map(_.message).mkString("\n")
        warnText must include("Ship")
        warnText must include("Order")
    }

    "derive the address through an ALIAS-declared Id FIELD type" in { (td: TestData) =>
      // Filed by riddl-models against rc.14: `isAddressFieldFor` matched `f.typeEx` against
      // `case uid: UniqueId` and fell to `false` for everything else, so a field typed by the
      // named alias `type OrderId is Id(entity Order)` was never a candidate. That alias IS the
      // documented house style (riddl-models CLAUDE.md, "Type IDs as {Name}Id"), so the check
      // caught only the rare inline spelling and misfired on the common one: reactive-bbq went
      // from 0 messages at rc.13 to 111 at rc.14, 72 of 86 distinct ones false.
      //
      // Note the two commands are otherwise IDENTICAL -- `DirectCmd` was never flagged and
      // `AliasCmd` always was, and the alias is the whole difference between them.
      val src =
        """domain Dom is {
          |  context Ctx is {
          |    type OrderId is Id(entity Order)
          |    command DirectCmd is { orderId: Id(entity Order) } with { briefly "d" }
          |    command AliasCmd is { orderId: OrderId } with { briefly "a" }
          |    command Go is { why: String } with { briefly "g" }
          |    record R is { total: Integer } with { briefly "r" }
          |    entity Order is {
          |      state OS of record R is {
          |        handler OH is {
          |          on command DirectCmd { do "direct" }
          |          on command AliasCmd { do "alias" }
          |        } with { briefly "oh" }
          |      } with { briefly "os" }
          |    } with { briefly "e" }
          |    entity Caller is {
          |      state CS of record R is {
          |        handler CH is {
          |          on command Go {
          |            tell command DirectCmd(orderId = "the order") to entity Order
          |            tell command AliasCmd(orderId = "the order") to entity Order
          |          }
          |        } with { briefly "ch" }
          |      } with { briefly "cs" }
          |    } with { briefly "ce" }
          |  } with { briefly "c" }
          |} with { briefly "d" }
          |""".stripMargin
      val msgs = diagnostics(src, "addr-alias-field")
      msgs.justErrors mustBe empty
      msgs.map(_.message).mkString("\n") must not include "which Order instance"
    }

    "REJECT an ambiguous derivation through ALIAS-declared Id field types" in { (td: TestData) =>
      // The other side of the same fix: once an aliased field COUNTS as a candidate, two of them
      // must produce the ambiguity Error exactly as two inline ones do. Without this case a fix
      // that resolved aliases only in the "is there at least one?" direction would still look
      // green -- the warning would stop firing while `by`/ambiguity stayed blind.
      val src =
        """domain Dom is {
          |  context Ctx is {
          |    type OrderId is Id(entity Order)
          |    command Ship is { fromOrder: OrderId, toOrder: OrderId } with { briefly "s" }
          |    command Go is { why: String } with { briefly "g" }
          |    record R is { total: Integer } with { briefly "r" }
          |    entity Order is {
          |      state OS of record R is {
          |        handler OH is { on command Ship { do "ship" } } with { briefly "oh" }
          |      } with { briefly "os" }
          |    } with { briefly "e" }
          |    entity Caller is {
          |      state CS of record R is {
          |        handler CH is {
          |          on command Go { tell command Ship(orderId = "the order") to entity Order }
          |        } with { briefly "ch" }
          |      } with { briefly "cs" }
          |    } with { briefly "ce" }
          |  } with { briefly "c" }
          |} with { briefly "d" }
          |""".stripMargin
      val text =
        diagnostics(src, "addr-alias-field-ambiguous").justErrors.map(_.message).mkString("\n")
      text must include("fromOrder")
      text must include("toOrder")
    }

    "NOT treat an alias to a FOREIGN same-named entity as an address" in { (td: TestData) =>
      // Resolution must survive the indirection: `ForeignId` aliases `Id(entity CtxA.Order)`, so
      // it is NOT an address for `entity CtxB.Order` even though both paths end in "Order". This
      // pins that the alias arm compares resolved identity (`eq`) like the direct arm, rather
      // than degrading to the name matching the direct arm was explicitly fixed to avoid.
      val src =
        """domain Dom is {
          |  context CtxA is {
          |    command Noop is { why: String } with { briefly "n" }
          |    record RA is { total: Integer } with { briefly "ra" }
          |    entity Order is {
          |      state OS of record RA is {
          |        handler OH is { on command Noop { do "noop" } } with { briefly "oh" }
          |      } with { briefly "os" }
          |    } with { briefly "ea" }
          |  } with { briefly "ca" }
          |  context CtxB is {
          |    type ForeignId is Id(entity CtxA.Order)
          |    command Ship is { foreignId: ForeignId } with { briefly "s" }
          |    command Go is { why: String } with { briefly "g" }
          |    record RB is { total: Integer } with { briefly "rb" }
          |    entity Order is {
          |      state OS of record RB is {
          |        handler OH is { on command Ship { do "ship" } } with { briefly "oh" }
          |      } with { briefly "os" }
          |    } with { briefly "eb" }
          |    entity Caller is {
          |      state CS of record RB is {
          |        handler CH is {
          |          on command Go { tell command Ship to entity CtxB.Order }
          |        } with { briefly "ch" }
          |      } with { briefly "cs" }
          |    } with { briefly "ce" }
          |  } with { briefly "cb" }
          |} with { briefly "d" }
          |""".stripMargin
      val msgs = diagnostics(src, "addr-alias-field-foreign")
      msgs.justErrors.map(_.message).mkString("\n") must not include "ambiguous"
      val warnText =
        msgs.filter(_.kind == Messages.CompletenessWarning).map(_.message).mkString("\n")
      warnText must include("Ship")
      warnText must include("Order")
    }

    "TERMINATE on a cyclic alias used as the MESSAGE type" in { (td: TestData) =>
      // Pre-existing in rc.14, found while fixing the field-type alias case and verified against
      // the released binary: `fieldsWithOwner` follows the alias chain with no visited set, so
      // `type A is B` / `type B is A` recurses forever. Confirmed by running rc.14's riddlc --
      // `java.lang.StackOverflowError ... at ValidationPass.fieldsWithOwner`. That is a crash of
      // the validator, not a diagnostic: the author gets "Exception Thrown" and no line number.
      //
      // A cycle is a modelling error that some other check may well want to report; what this
      // test pins is only that resolving one TERMINATES. Asserting on the absence of a crash is
      // the whole point, so there is deliberately no assertion about the messages.
      val src =
        """domain Dom is {
          |  context Ctx is {
          |    type A is B
          |    type B is A
          |    command Ship is A with { briefly "s" }
          |    command Go is { why: String } with { briefly "g" }
          |    record R is { total: Integer } with { briefly "r" }
          |    entity Order is {
          |      state OS of record R is {
          |        handler OH is { on command Ship { do "ship" } } with { briefly "oh" }
          |      } with { briefly "os" }
          |    } with { briefly "e" }
          |    entity Caller is {
          |      state CS of record R is {
          |        handler CH is {
          |          on command Go { tell command Ship(orderId = "the order") to entity Order }
          |        } with { briefly "ch" }
          |      } with { briefly "cs" }
          |    } with { briefly "ce" }
          |  } with { briefly "c" }
          |} with { briefly "d" }
          |""".stripMargin
      diagnostics(src, "addr-alias-cycle-message") // must not StackOverflow
      succeed
    }

    "TERMINATE on a cyclic alias used as an Id FIELD type" in { (td: TestData) =>
      // The same cycle reached through the NEW recursion this fix adds. Without a shared visited
      // set, resolving a field's type through aliases reintroduces the crash above on a model
      // that rc.14 merely mis-warned about -- turning a wrong message into a dead validator.
      val src =
        """domain Dom is {
          |  context Ctx is {
          |    type A is B
          |    type B is A
          |    command Ship is { thing: A } with { briefly "s" }
          |    command Go is { why: String } with { briefly "g" }
          |    record R is { total: Integer } with { briefly "r" }
          |    entity Order is {
          |      state OS of record R is {
          |        handler OH is { on command Ship { do "ship" } } with { briefly "oh" }
          |      } with { briefly "os" }
          |    } with { briefly "e" }
          |    entity Caller is {
          |      state CS of record R is {
          |        handler CH is {
          |          on command Go { tell command Ship(orderId = "the order") to entity Order }
          |        } with { briefly "ch" }
          |      } with { briefly "cs" }
          |    } with { briefly "ce" }
          |  } with { briefly "c" }
          |} with { briefly "d" }
          |""".stripMargin
      diagnostics(src, "addr-alias-cycle-field") // must not StackOverflow
      succeed
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
        .map(_.message)
        .mkString("\n") must not include "instance"
    }

    /** The `elements` gap. `checkTellAddressing` is reached at ANY depth -- including inside a
      * `foreach` body, as its own call site comments say -- but it was invoked without the
      * `foreach` element bindings, so a `tell` whose operand IS the loop element resolved to
      * nothing and every addressing check silently skipped it.
      *
      * `widenedOperandType`'s scaladoc asserted the opposite: "none of this function's three call
      * sites resolve an operand from inside a `foreach` body, so there is no position at which an
      * element binding could be in scope." That was a claim about code elsewhere, and it was false
      * -- `checkTellAddressing` is called from `checkStatementScopes`, which recurses into
      * `foreach` bodies precisely so it can thread those bindings.
      */
    "REJECT an ambiguous derivation when the operand is a `foreach` element" in { (td: TestData) =>
      val src =
        """domain Dom is {
          |  context Ctx is {
          |    command Ship is { fromOrder: Id(entity Order), toOrder: Id(entity Order) } with { briefly "s" }
          |    record Batch is { ships: many Ship } with { briefly "b" }
          |    command Go is { batch: Batch } with { briefly "g" }
          |    record R is { total: Integer } with { briefly "r" }
          |    entity Order is {
          |      state OS of record R is {
          |        handler OH is { on command Ship { do "ship" } } with { briefly "oh" }
          |      } with { briefly "os" }
          |    } with { briefly "e" }
          |    entity Caller is {
          |      state CS of record R is {
          |        handler CH is {
          |          on command Go { foreach s in field batch.ships { tell s to entity Order } }
          |        } with { briefly "ch" }
          |      } with { briefly "cs" }
          |    } with { briefly "ce" }
          |  } with { briefly "c" }
          |} with { briefly "d" }
          |""".stripMargin
      val text = diagnostics(src, "addr-foreach").justErrors.map(_.message).mkString("\n")
      withClue(text) {
        text must include("fromOrder")
        text must include("toOrder")
      }
    }
  }
}
