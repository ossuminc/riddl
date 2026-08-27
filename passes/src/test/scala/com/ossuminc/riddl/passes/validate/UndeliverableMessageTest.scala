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

/** A delivery whose destination declares no clause able to receive it (Reid, 2026-08-22).
  *
  * Two checks, two ends of one defect. `checkTellDeliverability` is the SENDING end — a `tell`
  * exists and the target handles nothing of that type. `checkInletsAreReceived` is the RECEIVING
  * end — a processor declares an entrance for a type it handles nowhere, whether or not anything
  * currently sends to it. They are not redundant and neither subsumes the other.
  *
  * Reported by riddl-generator: `logistics/warehousing/inventory-control` validated CLEANLY under
  * rc.20 while telling 9 distinct events to an entity with zero `on ... event` clauses, which
  * became 15 un-closable holes in the generated project.
  *
  * **`on other` satisfies both**, which is the hinge of the ruling: it states a policy for anything
  * unmatched, and it is the idiom `Riddl.BottomlessPit` is built from.
  */
class UndeliverableMessageTest extends AbstractValidatingTest {

  private def messagesFor(src: String, td: TestData): Messages =
    var captured: Messages = List.empty
    // `provideTips` is REQUIRED to see suggestions: `Messages.Accumulator.add` is a chokepoint that
    // STRIPS the suggestion unless it is set, so a test asserting on one without this reads every
    // suggestion as "".
    pc.withOptions(
      CommonOptions(showStyleWarnings = true, showWarnings = true, provideTips = true)
    ) { _ =>
      parseAndValidateDomain(RiddlParserInput(src, td), shouldFailOnErrors = false) {
        case (_, _, msgs) =>
          captured = msgs
          succeed
      }
    }
    captured
  end messagesFor

  private def toldFindings(msgs: Messages): Messages =
    msgs.filter(_.message.contains("but declares no handler clause that receives it"))

  // Matches BOTH wordings: the plain form ("...that receives it, so nothing happens when one
  // arrives") and the union form ("...for 2 of its 3 members (B, C), so nothing happens when one
  // of those arrives"). Filtering on the plain phrase alone made every union case read as "no
  // finding" -- a false green in a test whose whole subject is unions.
  private def inletFindings(msgs: Messages): Messages =
    msgs.filter(m => m.message.contains("admits") && m.message.contains("so nothing happens when"))

  /** `receiverClauses` are the clauses of entity `Sink`, which is told the event. */
  private def tellModel(receiverClauses: String): String =
    s"""domain Dom is {
       |  context Ctx is {
       |    event Happened is { what: String(1,20) }
       |    command Go is { who: String(1,20) }
       |    entity Sink is {
       |      handler SH is {
       |$receiverClauses
       |      }
       |    }
       |    entity Source is {
       |      handler SrcH is {
       |        on command Ctx.Go is {
       |          tell event Ctx.Happened(what = "x") to entity Ctx.Sink
       |        }
       |      }
       |    }
       |  }
       |}
       |""".stripMargin

  "a tell whose target has no clause for the message" should {
    "draw a CompletenessWarning naming both remedies" in { (td: TestData) =>
      val msgs = messagesFor(tellModel("""        on command Ctx.Go is { do "unrelated" }"""), td)
      val found = toldFindings(msgs)
      withClue(msgs.map(_.message).mkString("\n")) {
        found must not be empty
        found.head.kind.isCompleteness mustBe true
        // Both remedies, because either is legitimate and naming one pushes authors to it.
        found.head.suggestion must include("Add an `on` clause")
        found.head.suggestion must include("remove the `tell`")
      }
    }
  }

  "a tell whose target DOES declare the clause" should {
    "draw nothing" in { (td: TestData) =>
      val msgs = messagesFor(tellModel("""        on event Ctx.Happened is { do "handle it" }"""), td)
      withClue(msgs.map(_.message).mkString("\n")) { toldFindings(msgs) mustBe empty }
    }
  }

  "a tell whose target declares `on other`" should {
    "draw nothing — a generic catch IS receiving it" in { (td: TestData) =>
      val msgs = messagesFor(tellModel("""        on other is { do "catch everything" }"""), td)
      withClue(msgs.map(_.message).mkString("\n")) { toldFindings(msgs) mustBe empty }
    }
  }

  "an inlet whose type the owning processor receives nowhere" should {
    "draw a CompletenessWarning" in { (td: TestData) =>
      val msgs = messagesFor(
        """domain Dom is {
          |  context Ctx is {
          |    event Happened is { what: String(1,20) }
          |    command Go is { who: String(1,20) }
          |    entity Ent is {
          |      inlet Arrivals is event Ctx.Happened
          |      handler EH is {
          |        on command Ctx.Go is { do "something else entirely" }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin,
        td
      )
      val found = inletFindings(msgs)
      withClue(msgs.map(_.message).mkString("\n")) {
        found must not be empty
        found.head.kind.isCompleteness mustBe true
      }
    }

    "draw nothing when an `on other` clause is present" in { (td: TestData) =>
      val msgs = messagesFor(
        """domain Dom is {
          |  context Ctx is {
          |    event Happened is { what: String(1,20) }
          |    entity Ent is {
          |      inlet Arrivals is event Ctx.Happened
          |      handler EH is {
          |        on other is { do "catch everything" }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin,
        td
      )
      withClue(msgs.map(_.message).mkString("\n")) { inletFindings(msgs) mustBe empty }
    }

    "draw nothing when the processor DOES receive that type" in { (td: TestData) =>
      val msgs = messagesFor(
        """domain Dom is {
          |  context Ctx is {
          |    event Happened is { what: String(1,20) }
          |    entity Ent is {
          |      inlet Arrivals is event Ctx.Happened
          |      handler EH is {
          |        on event Ctx.Happened is { do "handle it" }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin,
        td
      )
      withClue(msgs.map(_.message).mkString("\n")) { inletFindings(msgs) mustBe empty }
    }
  }

  "a target whose body is `???`" should {
    "be exempt — a stub has already said not to expect much" in { (td: TestData) =>
      val msgs = messagesFor(
        """domain Dom is {
          |  context Ctx is {
          |    event Happened is { what: String(1,20) }
          |    command Go is { who: String(1,20) }
          |    entity Sink is { ??? }
          |    entity Source is {
          |      handler SrcH is {
          |        on command Ctx.Go is {
          |          tell event Ctx.Happened(what = "x") to entity Ctx.Sink
          |        }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin,
        td
      )
      withClue(msgs.map(_.message).mkString("\n")) { toldFindings(msgs) mustBe empty }
    }
  }

  /** Alternations, which the first version of both checks was blind to in BOTH directions —
    * caught by ossum.tech against rc.21. The corpus idiom is `type XEvent is one of { … }` on an
    * inlet, so the identity-only comparison demanded something no legal spelling could satisfy
    * except `on other`: the same unsatisfiable-demand trap the discard-sink exemption exists to
    * avoid. It cost the corpus 346 false positives.
    */
  private def altModel(clauses: String, inletType: String = "Ctx.AorB"): String =
    s"""domain Dom is {
       |  context Ctx is {
       |    event A is { a: String(1,9) }
       |    event B is { b: String(1,9) }
       |    type AorB is one of { Ctx.A or Ctx.B }
       |    entity Ent is {
       |      inlet In is type $inletType
       |      handler EH is {
       |$clauses
       |      }
       |    }
       |  }
       |}
       |""".stripMargin

  "an inlet typed as an alternation" should {
    "be satisfied when EVERY member has a clause" in { (td: TestData) =>
      val msgs = messagesFor(
        altModel("""        on event Ctx.A is { do "a" }
                   |        on event Ctx.B is { do "b" }""".stripMargin),
        td
      )
      withClue(msgs.map(_.message).mkString("\n")) { inletFindings(msgs) mustBe empty }
    }

    "NAME the members that have no clause when only some are handled" in { (td: TestData) =>
      // Reid, 2026-08-22: naming them is the whole point -- "declares no handler clause" is both
      // untrue and useless when four of a union's nine members ARE handled.
      val msgs = messagesFor(altModel("""        on event Ctx.A is { do "a" }"""), td)
      val found = inletFindings(msgs)
      withClue(msgs.map(_.message).mkString("\n")) {
        found must not be empty
        found.head.message must include("1 of its 2 members (B)")
        found.head.suggestion must include("B")
      }
    }

    "be satisfied by a clause naming the alternation itself" in { (td: TestData) =>
      val msgs = messagesFor(altModel("""        on event Ctx.AorB is { do "either" }"""), td)
      withClue(msgs.map(_.message).mkString("\n")) { inletFindings(msgs) mustBe empty }
    }
  }

  "a tell of one alternation member" should {
    "be received by a clause naming the alternation — the mirror-image blindness" in {
      (td: TestData) =>
        val msgs = messagesFor(
          """domain Dom is {
            |  context Ctx is {
            |    event A is { a: String(1,9) }
            |    event B is { b: String(1,9) }
            |    type AorB is one of { Ctx.A or Ctx.B }
            |    command Go is { g: String(1,9) }
            |    entity Sink is {
            |      handler SH is { on event Ctx.AorB is { do "either" } }
            |    }
            |    entity Src is {
            |      handler RH is {
            |        on command Ctx.Go is { tell event Ctx.A(a = "x") to entity Ctx.Sink }
            |      }
            |    }
            |  }
            |}
            |""".stripMargin,
          td
        )
        withClue(msgs.map(_.message).mkString("\n")) { toldFindings(msgs) mustBe empty }
    }
  }
}
