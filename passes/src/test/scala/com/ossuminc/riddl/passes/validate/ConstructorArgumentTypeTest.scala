/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.utils.{CommonOptions, pc}
import org.scalatest.TestData

/** Constructor arguments are type-checked against the field they supply (riddl-generator,
  * 2026-08-24).
  *
  * Until this landed a constructor argument was checked for arity, duplication, ordering, name
  * validity and `empty` cardinality — but never for TYPE. `deliveryOrderId: UUID` accepted an
  * `Id(DeliveryOrder)` and the defect surfaced only when a generator emitted Java that would not
  * compile. **A generator finding a model defect a validator did not is the wrong division of
  * labour.**
  *
  * Two rules, and the second needed a ruling:
  *
  *   1. The ordinary case defers to `isAssignmentCompatible`, which already answered "no" for
  *      `Id(E)` into `UUID` — nothing at this position ever asked it. Its DELIBERATE allowance that
  *      an `Id(E)` may be supplied for a `String_`/`Pattern` field (2026-08-15) is preserved.
  *   2. Two `Id`s must name the SAME processor. `isAssignmentCompatible`'s base rule is
  *      same-CLASS, so every `UniqueId` matched every other. Reid ruled it wrong regardless of
  *      corpus frequency: *"wrong is wrong … the point is to make the language and its expression
  *      bulletproof so reliable code can be generated from it."*
  *
  * **The ids are compared by RESOLVED IDENTITY, never by path text.** The last two cases here are
  * the ones that matter: the same entity spelled two ways must stay silent, and two entities that
  * merely SHARE A NAME must not be treated as one.
  */
class ConstructorArgumentTypeTest extends AbstractValidatingTest {

  private def messagesFor(src: String, td: TestData): Messages =
    var captured: Messages = List.empty
    pc.withOptions(CommonOptions(showStyleWarnings = false, showWarnings = false)) { _ =>
      parseAndValidateDomain(RiddlParserInput(src, td), shouldFailOnErrors = false) {
        case (_, _, msgs) => captured = msgs; succeed
      }
    }
    captured

  private def errs(msgs: Messages, frag: String): Messages =
    msgs.filter(m => m.isError && m.message.contains(frag))

  /** One entity, one event carrying its id, and a command whose field type varies. */
  private def model(fieldType: String, arg: String = "Ev.oid"): String =
    s"""domain D is {
       |  context C is {
       |    entity Order is { handler H is { on other is { do "x" } } }
       |    entity Shipment is { handler H is { on other is { do "x" } } }
       |    event Ev is { oid: Id(C.Order) }
       |    command Take is { ref: $fieldType }
       |    projector P is {
       |      record R is { n: String(1,9) }
       |      handler PH is {
       |        on event C.Ev is { tell command C.Take(ref = $arg) to entity C.Order }
       |      }
       |    }
       |  }
       |}
       |""".stripMargin

  private val Mismatch = "but the value is"
  private val WrongId = "is not an id of"

  "a constructor argument" should {
    "be an Error when an Id is supplied for a UUID field" in { (td: TestData) =>
      // The reported defect, verbatim: riddlg maps Id(E) to String and UUID to java.util.UUID, so
      // the generated Java did not compile.
      val msgs = messagesFor(model("UUID"), td)
      withClue(msgs.map(_.message).mkString("\n")) {
        errs(msgs, Mismatch) must not be empty
      }
    }

    "be an Error when an Id is supplied for a numeric field" in { (td: TestData) =>
      val msgs = messagesFor(model("Integer"), td)
      withClue(msgs.map(_.message).mkString("\n")) { errs(msgs, Mismatch) must not be empty }
    }

    /** The 2026-08-15 `String_`/`Pattern` allowance is DIRECTIONAL, and the direction is easy to
      * get backwards — an earlier version of this test did.
      *
      * `isAssignmentCompatible` reads "is `other` assignable to `this`", so the allowance lives on
      * `UniqueId` and means **a String value may be supplied for an `Id` field** (the value is
      * opaque and system-generated; a business key belongs in `on init`'s parameters). It does NOT
      * say the reverse. Both directions are pinned here so neither can drift unnoticed.
      */
    "accept a String value supplied for an Id field" in { (td: TestData) =>
      val src = model("Id(C.Order)").replace("ref = Ev.oid", "ref = Ev.name")
        .replace("oid: Id(C.Order)", "oid: Id(C.Order)  name: String(1,64)")
      val msgs = messagesFor(src, td)
      withClue(msgs.map(_.message).mkString("\n")) { errs(msgs, Mismatch) mustBe empty }
    }

    "accept an Id value supplied for a String field" in { (td: TestData) =>
      // Reid, 2026-08-25: *"RIDDL should allow an `Id(X)` or a `UUID` to be converted to a string
      // since almost all UUIDs or ULIDs have string representations and that may be important for
      // logging or communicating to the user."* This closed a real asymmetry -- `UniqueId` had
      // accepted `String_` since 2026-08-15, but nothing said the reverse, and the gap was
      // invisible until something type-checked the position. 20 reactive-bbq sites depend on it.
      val msgs = messagesFor(model("String(1,64)"), td)
      withClue(msgs.map(_.message).mkString("\n")) { errs(msgs, Mismatch) mustBe empty }
    }

    "accept a UUID value supplied for a String field" in { (td: TestData) =>
      val src = model("String(1,64)").replace("ref = Ev.oid", "ref = Ev.uid")
        .replace("oid: Id(C.Order)", "oid: Id(C.Order)  uid: UUID")
      val msgs = messagesFor(src, td)
      withClue(msgs.map(_.message).mkString("\n")) { errs(msgs, Mismatch) mustBe empty }
    }

    "still REJECT an Id supplied for a Pattern field" in { (td: TestData) =>
      // Deliberately NOT widened: a pattern constrains the shape of the string, and a generated
      // ULID has no reason to satisfy an arbitrary one. Allowing it would make a constraint the
      // model states unenforceable.
      val msgs = messagesFor(model("Pattern(\"[a-z]+\")"), td)
      withClue(msgs.map(_.message).mkString("\n")) { errs(msgs, Mismatch) must not be empty }
    }

    "accept a matching Id" in { (td: TestData) =>
      val msgs = messagesFor(model("Id(C.Order)"), td)
      withClue(msgs.map(_.message).mkString("\n")) {
        errs(msgs, Mismatch) mustBe empty
        errs(msgs, WrongId) mustBe empty
      }
    }

    "not fire on an optional field supplied with a plain value" in { (td: TestData) =>
      // Cardinality is unwrapped before comparing. Without that, `Id(C.Order)?` would "mismatch"
      // a plain `Id(C.Order)` and every optional field in the corpus would redden.
      val msgs = messagesFor(model("Id(C.Order)?"), td)
      withClue(msgs.map(_.message).mkString("\n")) { errs(msgs, Mismatch) mustBe empty }
    }

    "flag a string literal supplied for a UUID field" in { (td: TestData) =>
      // **This case asserted SILENCE until 2026-08-26**, on the stated grounds that "a literal has
      // no resolvable type expression here". That was true and is no longer: [1.13] gave literals
      // a type, because a position that cannot type its most common argument shape is not really
      // type-checked. The premise changed, so the expectation changed with it.
      val msgs = messagesFor(model("UUID", arg = "\"not-a-uuid\""), td)
      withClue(msgs.map(_.message).mkString("\n")) { errs(msgs, Mismatch) must not be empty }
    }

    "stay SILENT when the value's type genuinely cannot be determined" in { (td: TestData) =>
      // The conservative rule still holds, and still needs a case -- it just needs a value that is
      // actually undeterminable. `get from state` yields no TypeExpression here, so there is
      // nothing to compare and reporting would be reasoning from absence.
      val msgs = messagesFor(model("UUID", arg = "get from state C.P.R"), td)
      withClue(msgs.map(_.message).mkString("\n")) { errs(msgs, Mismatch) mustBe empty }
    }
  }

  "an Id of the wrong entity" should {
    "be an Error even though both sides are Ids" in { (td: TestData) =>
      val msgs = messagesFor(model("Id(C.Shipment)"), td)
      withClue(msgs.map(_.message).mkString("\n")) {
        val hit = errs(msgs, WrongId)
        hit must not be empty
        hit.head.message must include("Order")
        hit.head.message must include("Shipment")
      }
    }

    "NOT fire when one entity is named two different ways" in { (td: TestData) =>
      // `Id(Order)` and `Id(C.Order)` are one entity. Comparing path TEXT would call these
      // different and turn a legal model into an error — the mistake `isAddressFieldFor` made.
      val src =
        """domain D is {
          |  context C is {
          |    entity Order is { handler H is { on other is { do "x" } } }
          |    event Ev is { oid: Id(Order) }
          |    command Take is { ref: Id(C.Order) }
          |    projector P is {
          |      record R is { n: String(1,9) }
          |      handler PH is {
          |        on event C.Ev is { tell command C.Take(ref = Ev.oid) to entity C.Order }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin
      val msgs = messagesFor(src, td)
      withClue(msgs.map(_.message).mkString("\n")) { errs(msgs, WrongId) mustBe empty }
    }

    "fire for two entities that merely SHARE A NAME in different contexts" in { (td: TestData) =>
      // The converse, and the reason resolved identity is not optional: text matching would call
      // these equal and miss a genuinely wrong id.
      val src =
        """domain D is {
          |  context A is {
          |    entity Order is { handler H is { on other is { do "x" } } }
          |  }
          |  context B is {
          |    entity Order is { handler H is { on other is { do "x" } } }
          |    event Ev is { oid: Id(B.Order) }
          |    command Take is { ref: Id(D.A.Order) }
          |    projector P is {
          |      record R is { n: String(1,9) }
          |      handler PH is {
          |        on event B.Ev is { tell command B.Take(ref = Ev.oid) to entity B.Order }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin
      val msgs = messagesFor(src, td)
      withClue(msgs.map(_.message).mkString("\n")) {
        val hit = errs(msgs, WrongId)
        hit must not be empty
        // The message must DISTINGUISH them: `identify` renders both as Entity 'Order', which is
        // true and useless.
        hit.head.message must include("D.A.Order")
        hit.head.message must include("D.B.Order")
      }
    }
  }
}
