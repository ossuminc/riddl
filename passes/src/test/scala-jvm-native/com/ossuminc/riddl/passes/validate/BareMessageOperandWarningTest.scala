/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.utils.pc

import org.scalatest.TestData

/** Message-value-source design, Task 4: a BARE operand — `send command Foo to …`, `morph … with
  * record R` — names a message/record TYPE and says nothing about where the VALUE comes from, so a
  * generator has nothing to lower. It draws a [[CompletenessWarning]].
  *
  * **It is a Warning now and an Error later, deliberately.** riddl-models holds 14,730 bare refs
  * and ZERO constructor uses, so shipping the Error first would invalidate every message-sending
  * statement in all 189 models at once while CI requires them to validate clean. The
  * `errorsOf(msgs) mustBe empty` assertion `check` runs for EVERY case below is therefore
  * load-bearing, not decoration: it is what pins the severity at Warning.
  *
  * **A field-less message is EXEMPT** (design Q1, ruled 2026-08-14). A message with no data leaves
  * the type fully determining the value, so there is nothing to source; warning on it is exactly the
  * noise the standing `???` ruling exists to prevent.
  *
  * The design wrote that case as `event Started is { }`. **That spelling does not parse** — an empty
  * brace pair is not an aggregation, and the parser reports `Expected one of ("(" | "replies" |
  * "yields")` at the `is`. The field-less shape RIDDL actually admits is `is { ??? }`, which is what
  * the case below uses, and it lands on the same empty `AggregateTypeExpression` the exemption
  * tests — so the exemption is really "no fields OR an explicit stub", one condition, not two.
  */
class BareMessageOperandWarningTest extends AbstractValidatingTest {

  private def bareWarnings(msgs: Messages): Seq[String] =
    msgs.filter(_.kind == CompletenessWarning).map(_.message).filter(_.contains("not a value"))

  private def errorsOf(msgs: Messages): Seq[String] = msgs.filter(_.kind == Error).map(_.message)

  private def check(src: String, name: String)(f: Messages => org.scalatest.Assertion): Unit =
    parseAndValidate(src, name, shouldFailOnErrors = false) { case (_, _, msgs: Messages) =>
      errorsOf(msgs) mustBe empty
      f(msgs)
    }

  "a bare message-type operand" should {

    "warn on `send`" in { (td: TestData) =>
      val src =
        """domain d is {
          |  context c is {
          |    event Bar is { b: Integer }
          |    command Foo is { a: Integer }
          |    entity src is {
          |      outlet Out is event d.c.Bar
          |      handler Ops is {
          |        on command d.c.Foo is { send event d.c.Bar to outlet d.c.src.Out }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin
      check(src, td.name) { msgs => bareWarnings(msgs).size mustBe 1 }
    }

    "warn on `tell`" in { (td: TestData) =>
      val src =
        """domain d is {
          |  context c is {
          |    command Foo is { a: Integer }
          |    command Ship is { who: Integer }
          |    entity Warehouse is {
          |      handler Ops is { on command d.c.Ship is { do "ship" } }
          |    }
          |    entity src is {
          |      handler Ops is {
          |        on command d.c.Foo is { tell command d.c.Ship to entity d.c.Warehouse }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin
      check(src, td.name) { msgs => bareWarnings(msgs).size mustBe 1 }
    }

    "warn on `yield`" in { (td: TestData) =>
      val src =
        """domain d is {
          |  context c is {
          |    command Foo yields event Bar is { a: Integer }
          |    event Bar is { b: Integer }
          |    entity src is {
          |      handler Ops is {
          |        on command d.c.Foo is { yield event d.c.Bar }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin
      check(src, td.name) { msgs => bareWarnings(msgs).size mustBe 1 }
    }

    "warn on `reply`" in { (td: TestData) =>
      val src =
        """domain d is {
          |  context c is {
          |    query Qry replies result Res is { q: Integer }
          |    result Res is { r: Integer }
          |    entity src is {
          |      handler Ops is {
          |        on query d.c.Qry is { reply result d.c.Res }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin
      check(src, td.name) { msgs => bareWarnings(msgs).size mustBe 1 }
    }

    "warn on `morph … with`" in { (td: TestData) =>
      val src =
        """domain d is {
          |  context c is {
          |    command Foo is { a: Integer }
          |    record Data is { n: Integer }
          |    entity src is {
          |      state S of record d.c.Data
          |      handler Ops is {
          |        on command d.c.Foo is { morph entity d.c.src to state S with record d.c.Data }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin
      check(src, td.name) { msgs => bareWarnings(msgs).size mustBe 1 }
    }
  }

  "the field-less exemption" should {

    /** A message with no data: the type fully determines the value, so there is nothing for an
      * author to source and nothing for a generator to invent. `{ ??? }` is the spelling — see the
      * class comment for why the design's `{ }` is not.
      */
    "stay silent for a message with no fields" in { (td: TestData) =>
      val src =
        """domain d is {
          |  context c is {
          |    command Foo is { a: Integer }
          |    event Started is { ??? }
          |    entity src is {
          |      outlet Out is event d.c.Started
          |      handler Ops is {
          |        on command d.c.Foo is { send event d.c.Started to outlet d.c.src.Out }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin
      check(src, td.name) { msgs => bareWarnings(msgs) mustBe empty }
    }

    /** The alias chain must be FOLLOWED before deciding "no fields", or `command Ship is Shipment`
      * — riddl-models' house style — is exempted by accident even though `Shipment` has data.
      */
    "still warn when an ALIASED message resolves to a type that has fields" in { (td: TestData) =>
      val src =
        """domain d is {
          |  context c is {
          |    command Foo is { a: Integer }
          |    record Shipment is { who: Integer }
          |    command Ship is d.c.Shipment
          |    entity Warehouse is {
          |      handler Ops is { on command d.c.Ship is { do "ship" } }
          |    }
          |    entity src is {
          |      handler Ops is {
          |        on command d.c.Foo is { tell command d.c.Ship to entity d.c.Warehouse }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin
      check(src, td.name) { msgs => bareWarnings(msgs).size mustBe 1 }
    }
  }

  "the forms that DO say where the value comes from" should {

    "stay silent for a constructor operand" in { (td: TestData) =>
      val src =
        """domain d is {
          |  context c is {
          |    event Bar is { b: String }
          |    command Foo is { a: Integer }
          |    entity src is {
          |      outlet Out is event d.c.Bar
          |      handler Ops is {
          |        on command d.c.Foo is { send event d.c.Bar(b = "one") to outlet d.c.src.Out }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin
      check(src, td.name) { msgs => bareWarnings(msgs) mustBe empty }
    }

    "stay silent for a value operand" in { (td: TestData) =>
      val src =
        """domain d is {
          |  context c is {
          |    command Foo yields event Bar is { a: Integer }
          |    event Bar is { b: Integer }
          |    record Data is { evt: d.c.Bar }
          |    entity src is {
          |      state S of record d.c.Data
          |      handler Ops is {
          |        on command d.c.Foo is { yield evt }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin
      check(src, td.name) { msgs => bareWarnings(msgs) mustBe empty }
    }
  }
}
