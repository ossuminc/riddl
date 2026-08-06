/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.{RecognizedOptions, Messages}
import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.utils.{CommonOptions, pc}

import org.scalatest.TestData

/** synapify 2026-08-05: an authoring tool needs to know which recognized options are DEPRECATED for
  * a given kind, so its picker stops offering spellings its own Problems pane then flags.
  *
  * The load-bearing test here is the last one. A registry that merely CLAIMS a name is deprecated
  * is worth nothing if the compiler does not actually deprecate it, and those are two different
  * tables in two different files (`RecognizedOptions.registry` and `EntityParser`'s intention map).
  * This repo has watched option tables drift apart three times, so the agreement is tested
  * BEHAVIOURALLY rather than by comparing the tables.
  */
class RecognizedOptionSetTest extends AbstractValidatingTest {

  "RecognizedOptionSet" should {

    "split Entity's options into current and deprecated" in { (td: TestData) =>
      val set = RecognizedOptions.optionSetFor("Entity")
      // The six spellings that became entity intentions in 2.0.
      set.deprecated must contain theSameElementsAs Seq(
        "aggregate",
        "available",
        "consistent",
        "event-sourced",
        "transient",
        "value"
      )
      set.current must not contain "event-sourced"
      set.current must not contain "value"
      // Every deprecated name carries guidance, which is the point of shipping the map.
      set.replacements.keySet mustBe set.deprecated.toSet
      set.replacements("value") must include("persistent")
      set.replacements("event-sourced") must include("event-sourced")
    }

    "keep `optionsFor` exactly as it was — the union, deprecated included" in { (td: TestData) =>
      // Consumers that only want "is this name recognized here?" must be unaffected: a deprecated
      // option still parses and must still be RECOGNIZED, or a tool reports "not a recognized
      // RIDDL option" about a name RIDDL accepts.
      val set = RecognizedOptions.optionSetFor("Entity")
      RecognizedOptions.optionsFor("Entity") mustBe set.all
      RecognizedOptions.optionsFor("Entity") must contain("event-sourced")
      (set.current ++ set.deprecated).toSet mustBe RecognizedOptions.optionsFor("Entity").toSet
    }

    "deprecate per KIND, not per name" in { (td: TestData) =>
      // `consistent`/`available`/`transient` became entity intentions, but a Repository has no
      // intentions, so on a Repository they are current. A flat name-keyed table would have
      // wrongly condemned the Repository spelling.
      val repo = RecognizedOptions.optionSetFor("Repository")
      repo.current must contain("consistent")
      repo.current must contain("available")
      repo.current must contain("transient")
      repo.deprecated must not contain "consistent"
    }

    "not advertise `persistent` on an Entity" in { (td: TestData) =>
      // `persistent` IS in the registry, but scoped to Connector, where it means connector
      // persistence -- a different thing from the entity persistence intention. Adding it to
      // Entity would re-introduce exactly what 2.0 deprecated: an intention spelled as an option.
      val entity = RecognizedOptions.optionSetFor("Entity")
      entity.all must not contain "persistent"
      RecognizedOptions.optionSetFor("Connector").current must contain("persistent")
    }

    // The drift guard. If the registry and the parser ever disagree, this reddens.
    //
    // It parses with `parseInputWithMessages`, NOT through `parseAndValidate`: entity-intention
    // deprecations are emitted at PARSE time, and `parseInput` (which the helper uses) returns the
    // Root alone and drops them. A version of this test written on the helper reported that
    // `aggregate` "drew no deprecation" when riddlc plainly emits one — the helper was the liar.
    "actually deprecate every option it advertises as deprecated for an Entity" in {
      (td: TestData) =>
        val deprecated = RecognizedOptions.optionSetFor("Entity").deprecated
        deprecated must not be empty
        deprecated.foreach { name =>
          val src =
            s"""domain Dom is {
               |  context Ctx is {
               |    entity Ent is {
               |      handler Hnd is { on other is { do "x" } }
               |    } with { option $name }
               |  }
               |}
               |""".stripMargin
          TopLevelParser.parseInputWithMessages(RiddlParserInput(src, s"dep-$name")) match
            case Left(errs) => fail(s"option '$name' did not parse:\n${errs.format}")
            case Right((_, msgs)) =>
              withClue(
                s"option '$name' is advertised as deprecated but drew no deprecation:\n" +
                  msgs.format
              ) {
                msgs.exists(m =>
                  m.kind == Messages.Deprecation && m.message.contains(name)
                ) mustBe true
              }
        }
    }
  }
}
