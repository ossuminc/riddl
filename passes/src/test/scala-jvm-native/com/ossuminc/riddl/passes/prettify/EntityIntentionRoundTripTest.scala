/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.prettify

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Finder
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.passes.validate.AbstractValidatingTest
import com.ossuminc.riddl.passes.{Pass, PassInput, PassesOutput}
import com.ossuminc.riddl.utils.pc

import org.scalatest.*

/** Semantic keywords written before `entity`, replacing the options they came from.
  *
  * They are not metadata: whether an entity is event-sourced decides what the model MEANS and,
  * through the event-sourcing rules, whether it is even legal. A hard Error keyed off something the
  * Computational Model calls an instruction "to be honored if possible" is a category error, so
  * these live in the grammar now.
  *
  * Any order is accepted; PrettifyPass emits the canonical one — the same bargain as `A | B` vs
  * `one of { … }`. The parser stores them canonically so that write order can never make two
  * otherwise-identical entities compare unequal (`Definition.equals` compares this field).
  */
class EntityIntentionRoundTripTest extends AbstractValidatingTest {

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

  private def entityOf(root: Root): Entity =
    Finder(root).recursiveFindByType[Entity].headOption.getOrElse(fail("no Entity in the tree"))

  private def model(prefix: String, options: String = ""): String =
    s"""domain D is {
       |  context C is {
       |    $prefix entity Order is {
       |      record Fields is { total: Integer } with { briefly "f" }
       |      state Main of record Order.Fields is { ??? } with { briefly "s" }
       |    } with { briefly "o"$options }
       |  } with { briefly "c" }
       |} with { briefly "d" }
       |""".stripMargin

  "an intention keyword before `entity`" should {

    "parse into the entity's intentions" in { (td: TestData) =>
      val e = entityOf(parse(model("event-sourced"), "one"))
      e.intentions mustBe Seq(EntityIntention.EventSourced)
      e.isEventSourced mustBe true
    }

    "accept every keyword" in { (td: TestData) =>
      EntityIntention.canonicalOrder.foreach { intention =>
        val e = entityOf(parse(model(intention.keyword), intention.keyword))
        withClue(s"keyword '${intention.keyword}': ") { e.intentions mustBe Seq(intention) }
      }
    }

    "leave an entity with no prefix alone" in { (td: TestData) =>
      val e = entityOf(parse(model(""), "none"))
      e.intentions mustBe empty
      e.isEventSourced mustBe false
    }
  }

  "several intentions in ANY order" should {

    "parse to the identical AST regardless of how they were written" in { (td: TestData) =>
      val canonical = entityOf(parse(model("aggregate consistent event-sourced"), "canonical"))
      val scrambled = entityOf(parse(model("event-sourced aggregate consistent"), "scrambled"))
      canonical.intentions mustBe
        Seq(EntityIntention.Aggregate, EntityIntention.Consistent, EntityIntention.EventSourced)
      scrambled.intentions mustBe canonical.intentions
    }

    "prettify to the canonical order" in { (td: TestData) =>
      val pretty = prettify(parse(model("event-sourced aggregate consistent"), "scrambled"))
      pretty must include("aggregate consistent event-sourced entity Order")
    }
  }

  "the round trip" should {
    "preserve the intentions" in { (td: TestData) =>
      val once = parse(model("aggregate consistent event-sourced"), "src")
      val again = parse(prettify(once), "regen")
      entityOf(again).intentions mustBe entityOf(once).intentions
    }
  }

  "a deprecated option" should {

    "still set the intention, and say so" in { (td: TestData) =>
      val input = RiddlParserInput(model("", " option event-sourced"), "deprecated")
      TopLevelParser.parseInputWithMessages(input) match
        case Left(msgs) => fail(s"parse failed:\n${msgs.format}")
        case Right((root, msgs)) =>
          entityOf(root).isEventSourced mustBe true
          withClue(s"messages were: ${msgs.map(_.message).mkString("\n")}") {
            msgs.exists(m =>
              m.message.contains("option event-sourced") &&
                m.message.contains("deprecated")
            ) mustBe true
          }
    }

    "be CONSUMED, so a round trip converges on the keyword" in { (td: TestData) =>
      val pretty = prettify(parse(model("", " option event-sourced"), "deprecated"))
      pretty must include("event-sourced entity Order")
      // The option is folded into the intention and dropped, so it must not also be emitted.
      pretty mustNot include("option event-sourced")
    }

    "map `value` to `persistent` -- the same meaning, said more clearly" in { (td: TestData) =>
      val e = entityOf(parse(model("", " option value"), "value"))
      e.intentions mustBe Seq(EntityIntention.Persistent)
    }
  }

  "mutually exclusive intentions" should {

    def exclusionErrors(prefix: String, td: TestData): Seq[String] =
      var found = Seq.empty[String]
      parseAndValidateDomain(RiddlParserInput(model(prefix), td), shouldFailOnErrors = false) {
        case (_, _, msgs) =>
          found = msgs
            .filter(m => m.isError && m.message.contains("mutually exclusive"))
            .map(_.message)
          succeed
      }
      found

    "be an error within the persistence group" in { (td: TestData) =>
      val e = exclusionErrors("persistent event-sourced", td)
      withClue(s"errors were: ${e.mkString("\n")}") {
        e must not be empty
        e.head must include("persistence")
        // 'event-sourced' already implies 'persistent' -- the suggestion should say so.
        e.head must include("event-sourced")
      }
    }

    "be an error within the consistency group" in { (td: TestData) =>
      val e = exclusionErrors("consistent available", td)
      withClue(s"errors were: ${e.mkString("\n")}") {
        e must not be empty
        e.head must include("consistency")
      }
    }

    "allow one from each group together" in { (td: TestData) =>
      val e = entityOf(parse(model("aggregate available transient"), "mixed"))
      e.intentions mustBe
        Seq(EntityIntention.Aggregate, EntityIntention.Available, EntityIntention.Transient)
    }
  }

  "the parser's literal keyword list" should {
    "match EntityIntention.keywords -- StringIn is a macro and cannot read the enum" in {
      (td: TestData) =>
        // If a keyword is added to the enum but not to the parser's StringIn literals, it simply
        // never parses. Prove every declared keyword is reachable from source.
        EntityIntention.canonicalOrder.foreach { intention =>
          val e = entityOf(parse(model(intention.keyword), s"reach-${intention.keyword}"))
          withClue(s"'${intention.keyword}' is declared but does not parse: ") {
            e.intentions mustBe Seq(intention)
          }
        }
    }
  }
}
