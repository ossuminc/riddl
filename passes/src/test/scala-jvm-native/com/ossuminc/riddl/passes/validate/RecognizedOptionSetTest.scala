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
      // Recognized on a Connector -- deliberately `all`, not `current`: this assertion exists to
      // show the name is SCOPED to Connector, and must not also pin whether it is current there
      // (it is not; see the Connector split test below).
      RecognizedOptions.optionSetFor("Connector").all must contain("persistent")
    }

    "split Connector's options into current and deprecated" in { (td: TestData) =>
      // synapify 2026-08-14: rc.14 promoted connector durability to an intention and the parser
      // consumes `option persistent` into it WITH a Deprecation -- but the registry entry never
      // retired, so `optionSetFor("Connector").current` still advertised it. Synapify's "add
      // option" picker is driven by `.current`, so the panel offered the spelling and the Problems
      // pane then flagged the Deprecation the panel had just invited. That is the exact loop the
      // current/deprecated split was built to close for entity intentions; rc.14 reopened it for
      // connectors.
      val set = RecognizedOptions.optionSetFor("Connector")
      set.deprecated must contain("persistent")
      set.current must not contain "persistent"
      set.replacements("persistent") must include("persistent")
      set.replacements("persistent") must include("connector")
    }

    "keep a deprecated `persistent` RECOGNIZED on a Connector" in { (td: TestData) =>
      // Deprecated, not removed: the option must keep parsing (426 uses across riddl-models), so
      // `optionsFor` -- the "is this name recognized here?" question -- must be unmoved, or a tool
      // reports "not a recognized RIDDL option" about a name RIDDL still accepts.
      val set = RecognizedOptions.optionSetFor("Connector")
      RecognizedOptions.optionsFor("Connector") must contain("persistent")
      (set.current ++ set.deprecated).toSet mustBe RecognizedOptions.optionsFor("Connector").toSet
    }

    "leave `persistent` untouched for every OTHER kind" in { (td: TestData) =>
      // `persistent` is Connector-only, so deprecating it there must shift nothing else. Pins the
      // reporter's acceptance criterion 4 -- and the per-KIND property generally, since a
      // name-keyed table would have condemned a Repository's unrelated `transient`/`consistent`.
      RecognizedOptions.optionSetFor("Repository").all must not contain "persistent"
      RecognizedOptions.optionSetFor("Entity").all must not contain "persistent"
      RecognizedOptions.optionSetFor("Context").all must not contain "persistent"
    }

    // The Connector half of the drift guard below. Same reasoning: the registry claiming a name is
    // deprecated is worth nothing unless the compiler actually deprecates it, and those live in
    // two different files (`RecognizedOptions.registry` and `StreamingParser`'s consumed-option
    // list). rc.14 is precisely the case where they disagreed.
    "actually deprecate every option it advertises as deprecated for a Connector" in {
      (td: TestData) =>
        val deprecated = RecognizedOptions.optionSetFor("Connector").deprecated
        deprecated must not be empty
        deprecated.foreach { name =>
          val src =
            s"""domain Dom is {
               |  context Ctx is {
               |    command Cmd is { why: String }
               |    source Src is { outlet Out is type Cmd }
               |    sink Snk is { inlet In is type Cmd }
               |    connector Conn is {
               |      from outlet Src.Out to inlet Snk.In
               |    } with { option $name }
               |  }
               |}
               |""".stripMargin
          TopLevelParser.parseInputWithMessages(RiddlParserInput(src, s"conn-dep-$name")) match
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
