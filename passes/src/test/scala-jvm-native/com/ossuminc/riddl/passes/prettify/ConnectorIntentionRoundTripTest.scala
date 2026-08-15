/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.prettify

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{Finder, toSeq}
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.passes.validate.AbstractValidatingTest
import com.ossuminc.riddl.passes.{Pass, PassInput, PassesOutput}
import com.ossuminc.riddl.utils.{CommonOptions, pc}

import org.scalatest.*
import org.scalatest.TestData

/** Semantic keywords written before `connector`, replacing the option they came from.
  *
  * Same category error the entity intentions fixed: `persistent` was an option, but the
  * Computational Model calls options advisory ("honored if possible") and a DELIVERY GUARANTEE is
  * not advisory. §25.7 is explicit — delivery is at-least-once on durable realizations, "weaker
  * only as a knowing deployment downgrade, **never a silent one**" — and a keyword at the
  * declaration site is exactly what makes the downgrade un-silent.
  *
  * **`at-least-once` is the default AND writable** (Reid, 2026-08-13). Absence means at-least-once,
  * so writing it is redundant; it is still accepted, and draws no warning, because a model may want
  * to state its guarantee where a reader benefits from seeing it.
  *
  * **Ordering is deliberately NOT an intention.** §25.7 makes `unordered` "permission, not mandate"
  * with a best-effort obligation — the definition of advisory — so it stays an option. The
  * admission test for this enum is whether a generator may decline to honour the keyword.
  */
class ConnectorIntentionRoundTripTest extends AbstractValidatingTest {

  private def parse(src: String, origin: String): Root =
    TopLevelParser.parseInput(RiddlParserInput(src, origin)) match
      case Right(root) => root
      case Left(msgs)  => fail(s"parse of $origin failed:\n${msgs.format}")

  private def prettify(root: Root): String =
    val creators = Pass.standardPasses :+ { (in: PassInput, out: PassesOutput) =>
      PrettifyPass(in, out, PrettifyPass.Options(flatten = true, inputDir = ""))
    }
    Pass
      .runThesePasses(PassInput(root), creators)
      .outputs
      .outputOf[PrettifyOutput](PrettifyPass.name)
      .getOrElse(fail("PrettifyPass produced no output"))
      .state
      .filesAsString

  private def connectorOf(root: Root): Connector =
    Finder(root)
      .recursiveFindByType[Connector]
      .headOption
      .getOrElse(fail("no Connector in the tree"))

  private def model(prefix: String, options: String = ""): String =
    s"""domain D is {
       |  context C is {
       |    event E is { a: String } with { briefly "e" }
       |    processor Src as source is { outlet o is event E } with { briefly "s" }
       |    processor Dst as sink is { inlet i is event E } with { briefly "d" }
       |    $prefix connector Wire is from outlet Src.o to inlet Dst.i
       |      with { briefly "w"$options }
       |  } with { briefly "c" }
       |} with { briefly "d" }
       |""".stripMargin

  private def messagesFor(src: String, origin: String): Messages.Messages =
    var captured: Messages.Messages = Messages.empty
    pc.withOptions(CommonOptions(showStyleWarnings = true, showWarnings = true)) { _ =>
      parseAndValidate(src, origin, shouldFailOnErrors = false) { (_, _, msgs) =>
        captured = msgs
        succeed
      }
    }
    captured

  "connector intentions" should {

    "parse a durability and a delivery keyword together" in { (td: TestData) =>
      val c = connectorOf(parse(model("persistent at-most-once"), "both-groups"))
      c.intentions must contain(ConnectorIntention.Persistent)
      c.intentions must contain(ConnectorIntention.AtMostOnce)
    }

    /** `exactly-once` became a third delivery intention on 2026-08-14 (Reid, asked directly). It
      * had been the ONE delivery spelling with no intention behind it, which is precisely what
      * blocked deprecating the option forms: deprecating two of three and leaving the third
      * current would have been its own inconsistency.
      */
    "parse `exactly-once` as a delivery intention" in { (td: TestData) =>
      val c = connectorOf(parse(model("exactly-once"), "exactly-once-kw"))
      c.intentions must contain(ConnectorIntention.ExactlyOnce)
    }

    "reject two delivery keywords, naming both" in { (td: TestData) =>
      // Mutual exclusion WITHIN a group is validated rather than encoded in the grammar, so the
      // message can name both offenders instead of the model failing to parse.
      val msgs = messagesFor(model("at-most-once exactly-once"), "two-delivery")
      val text = msgs.map(_.message).mkString("\n")
      msgs.justErrors mustNot be(empty)
      text must include("at-most-once")
      text must include("exactly-once")
    }

    /** All three delivery OPTIONS are consumed into the intention they duplicate, exactly as
      * `option persistent` already was. Until 2026-08-14 they parsed as plain registry options and
      * meant nothing -- two spellings where one was silently inert (reported by synapify).
      * Consuming rather than merely deprecating is what makes the round trip converge and migrates
      * a corpus for free.
      */
    "consume a deprecated delivery option into its intention" in { (td: TestData) =>
      Seq(
        "at-least-once" -> ConnectorIntention.AtLeastOnce,
        "at-most-once" -> ConnectorIntention.AtMostOnce,
        "exactly-once" -> ConnectorIntention.ExactlyOnce
      ).foreach { case (kw, intention) =>
        val c = connectorOf(parse(model("", options = s" option $kw"), s"opt-$kw"))
        withClue(s"option $kw should become $intention: ") {
          c.intentions must contain(intention)
          // CONSUMED, not merely recognised: the option must be gone from the metadata, or a round
          // trip would emit both spellings and never converge.
          c.hasOption(kw) mustBe false
        }
      }
    }

    "accept `at-least-once` even though it is the default" in { (td: TestData) =>
      // Redundant but legal, and it must draw no warning -- Reid's ruling.
      val msgs = messagesFor(model("at-least-once"), "explicit-default")
      msgs.justErrors mustBe empty
      msgs.map(_.message).mkString("\n") must not(include("at-least-once"))
    }

    "store intentions canonically regardless of write order" in { (td: TestData) =>
      // `Definition.equals` compares this field, so write order must never make two otherwise
      // identical connectors compare unequal.
      val a = connectorOf(parse(model("persistent at-most-once"), "order-a")).intentions
      val b = connectorOf(parse(model("at-most-once persistent"), "order-b")).intentions
      a mustBe b
    }

    "survive a prettify round trip in canonical order" in { (td: TestData) =>
      val pretty = prettify(parse(model("at-most-once persistent"), "rt-in"))
      pretty must include("persistent at-most-once connector Wire")
      // And re-parsing the emitted source yields the same intentions.
      connectorOf(parse(pretty, "rt-out")).intentions mustBe
        Seq(ConnectorIntention.Persistent, ConnectorIntention.AtMostOnce)
    }

    "reject two keywords from the SAME group, naming both" in { (td: TestData) =>
      val errors =
        messagesFor(model("at-least-once at-most-once"), "same-group").justErrors
          .map(_.message)
          .mkString("\n")
      errors must include("'at-least-once' and 'at-most-once'")
      errors must include("delivery intentions are mutually exclusive")
    }

    "CONTROL: keywords from DIFFERENT groups are not rejected" in { (td: TestData) =>
      // Without this, an exclusivity check that rejected any two keywords would pass the case above.
      messagesFor(model("persistent at-most-once"), "diff-groups").justErrors mustBe empty
    }
  }

  "the deprecated `option persistent`" should {

    "be CONSUMED into the intention rather than kept alongside it" in { (td: TestData) =>
      // Consuming is what makes the round trip converge and migrates the corpus for free: the 426
      // `option persistent()` uses across riddl-models become the intention on the next prettify.
      val c = connectorOf(parse(model("", options = " option persistent"), "old-spelling"))
      c.intentions must contain(ConnectorIntention.Persistent)
      c.metadata.toSeq.collect { case ov: OptionValue => ov.name } must not(contain("persistent"))
    }

    "draw a deprecation naming the replacement" in { (td: TestData) =>
      val src = model("", options = " option persistent")
      // parseInputWithMessages returns Either[Messages, (Root, Messages)] -- the RIGHT side carries
      // the parse-time messages that a successful parse produced, which is where deprecations live.
      val msgs = TopLevelParser.parseInputWithMessages(RiddlParserInput(src, "old-msg")) match
        case Right((_, ms)) => ms
        case Left(ms)       => fail(s"parse failed:\n${ms.format}")
      val text = msgs.map(_.message).mkString("\n")
      text must include("'option persistent' is deprecated")
      text must include("write 'persistent' before 'connector'")
    }

    "prettify as the keyword, so a round trip converges" in { (td: TestData) =>
      val pretty = prettify(parse(model("", options = " option persistent"), "old-rt"))
      pretty must include("persistent connector Wire")
      pretty must not(include("option persistent"))
    }

    "still satisfy the persistence gates, so migration is not forced" in { (td: TestData) =>
      // A37 and Rule 4 ask `Connector.isPersistent`, which accepts BOTH spellings. Asking only one
      // is the bug that cost 1120 false warnings on `external` (2026-08-12).
      connectorOf(parse(model("", options = " option persistent"), "gate-old")).isPersistent mustBe
        true
      connectorOf(parse(model("persistent"), "gate-new")).isPersistent mustBe true
      connectorOf(parse(model(""), "gate-none")).isPersistent mustBe false
    }
  }

  "the keyword list" should {

    "match the string literals the parser matches on" in { (td: TestData) =>
      // `StringIn` is a macro taking only constants, so the parser cannot use
      // `ConnectorIntention.keywords` directly and the two lists can drift. This pins them, exactly
      // as EntityIntentionKeywordsTest does for entities.
      ConnectorIntention.keywords must contain theSameElementsAs
        Seq("at-least-once", "at-most-once", "exactly-once", "persistent")
    }

    "be ordered longest-first so no keyword is matched as a prefix of another" in { (td: TestData) =>
      val lengths = ConnectorIntention.keywords.map(_.length)
      lengths mustBe lengths.sorted.reverse
    }
  }
}
