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

/** Message-value-source design, Task 2: `yield`, `reply` and `morph … with` accept a bare
  * `ValueRef` operand, as `send`/`tell` already did after Task 1.
  *
  * `yield`/`reply` were excluded from A56 on the reasoning that widening them "would interact with
  * yield conformance (A19), which compares the yielded operand against the clause's DECLARED
  * `yields`". **That reason does not survive inspection**: the comparison is by RESOLVED TYPE, and
  * a `ValueRef` supplies one exactly as a `MessageRef` does. So the interaction is not an obstacle
  * — it is a check that must KEEP WORKING, which is why the wrong-type cases below matter at least
  * as much as the accepting ones.
  *
  * `morph … with` is the same widening on the record side, and is riddlg's other 37.6% of `AI
  * FILL` holes.
  *
  * Before this task all six accepting cases were PARSE errors: `messageValue`/`recordValue` are
  * keyword-led, so a bare identifier could not be an operand at all.
  */
class YieldReplyMorphValueOperandTest extends AbstractValidatingTest {

  private def errorsOf(msgs: Messages): Seq[String] = msgs.filter(_.kind == Error).map(_.message)

  "a `yield` operand" should {

    "accept a state-record field of the declared event type" in { (td: TestData) =>
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
      parseAndValidate(src, td.name, shouldFailOnErrors = false) { case (_, _, msgs: Messages) =>
        errorsOf(msgs) mustBe empty
      }
    }

    /** The load-bearing half. A widened operand must still be COMPARED against the clause's
      * declared `yields`, or the widening has quietly deleted A19 for every value operand.
      */
    "still reject a value whose type is not the declared event" in { (td: TestData) =>
      val src =
        """domain d is {
          |  context c is {
          |    command Foo yields event Bar is { a: Integer }
          |    event Bar is { b: Integer }
          |    event Other is { o: Integer }
          |    record Data is { wrong: d.c.Other }
          |    entity src is {
          |      state S of record d.c.Data
          |      handler Ops is {
          |        on command d.c.Foo is { yield wrong }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin
      parseAndValidate(src, td.name, shouldFailOnErrors = false) { case (_, _, msgs: Messages) =>
        errorsOf(msgs) mustNot be(empty)
      }
    }

    "still reject a value that is not a message at all" in { (td: TestData) =>
      val src =
        """domain d is {
          |  context c is {
          |    command Foo yields event Bar is { a: Integer }
          |    event Bar is { b: Integer }
          |    record Data is { count: Integer }
          |    entity src is {
          |      state S of record d.c.Data
          |      handler Ops is {
          |        on command d.c.Foo is { yield count }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin
      parseAndValidate(src, td.name, shouldFailOnErrors = false) { case (_, _, msgs: Messages) =>
        errorsOf(msgs) mustNot be(empty)
      }
    }
  }

  "a `reply` operand" should {

    "accept a state-record field of the declared result type" in { (td: TestData) =>
      val src =
        """domain d is {
          |  context c is {
          |    query Qry replies result Res is { q: Integer }
          |    result Res is { r: Integer }
          |    record Data is { answer: d.c.Res }
          |    entity src is {
          |      state S of record d.c.Data
          |      handler Ops is {
          |        on query d.c.Qry is { reply answer }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin
      parseAndValidate(src, td.name, shouldFailOnErrors = false) { case (_, _, msgs: Messages) =>
        errorsOf(msgs) mustBe empty
      }
    }

    "still reject a value whose type is not the declared result" in { (td: TestData) =>
      val src =
        """domain d is {
          |  context c is {
          |    query Qry replies result Res is { q: Integer }
          |    result Res is { r: Integer }
          |    result Wrong is { w: Integer }
          |    record Data is { bad: d.c.Wrong }
          |    entity src is {
          |      state S of record d.c.Data
          |      handler Ops is {
          |        on query d.c.Qry is { reply bad }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin
      parseAndValidate(src, td.name, shouldFailOnErrors = false) { case (_, _, msgs: Messages) =>
        errorsOf(msgs) mustNot be(empty)
      }
    }
  }

  "a `morph … with` operand" should {

    "accept a value of the target state's record type" in { (td: TestData) =>
      val src =
        """domain d is {
          |  context c is {
          |    command Foo is { a: Integer }
          |    record Data is { n: Integer }
          |    record Holder is { next: d.c.Data }
          |    entity src is {
          |      state S of record d.c.Data
          |      state H of record d.c.Holder
          |      handler Ops is {
          |        on command d.c.Foo is { morph entity d.c.src to state S with next }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin
      parseAndValidate(src, td.name, shouldFailOnErrors = false) { case (_, _, msgs: Messages) =>
        errorsOf(msgs) mustBe empty
      }
    }
  }
}
