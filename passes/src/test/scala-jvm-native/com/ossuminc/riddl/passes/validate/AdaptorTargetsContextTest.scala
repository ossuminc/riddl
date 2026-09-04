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

/** An adaptor translates between CONTEXTS and may address only a context (Reid, 2026-09-03).
  *
  * **Narrower than the boundary rule, and it does not follow from it.** `checkTargetBoundary` asks
  * what CROSSES a context and leaves intra-context sends unrestricted, so an adaptor telling an
  * entity of its own context satisfied it — which is why 29 of reactive-bbq's 35 adaptor tells
  * validated clean. An adaptor is different because the boundary IS its whole job: reaching inward
  * to a named entity or repository makes it a participant in that context's business rather than
  * its translator, and binds a foreign context's message shape to one processor inside this one.
  *
  * Written as models that violate the rule, per riddl-generator's standing lesson: "the validator
  * checks X" is verified by running a model that violates X, not by finding the check in the code.
  */
class AdaptorTargetsContextTest extends AbstractValidatingTest {

  private def model(stmt: String): String =
    s"""domain D is {
       |  command Cmd is { x: Integer } with { briefly "c" }
       |  context Other is {
       |    inlet oin is command D.Cmd with { briefly "i" }
       |    handler oh is { on command D.Cmd { do "handle" } } with { briefly "h" }
       |  } with { briefly "o" }
       |  context Home is {
       |    inlet hin is command D.Cmd with { briefly "i" }
       |    entity Inner is {
       |      inlet ein is command D.Cmd with { briefly "i" }
       |      handler ih is { on command D.Cmd { do "inner" } } with { briefly "h" }
       |    } with { briefly "e" }
       |    repository Store is {
       |      inlet sin is command D.Cmd with { briefly "i" }
       |      handler sh is { on command D.Cmd { do "store" } } with { briefly "h" }
       |    } with { briefly "r" }
       |    adaptor In from context D.Other is {
       |      outlet aout is command D.Cmd with { briefly "o" }
       |      handler ah is { on command D.Cmd { $stmt } } with { briefly "h" }
       |    } with { briefly "ad" }
       |  } with { briefly "hm" }
       |  connector ToInner is { from outlet Home.In.aout to inlet Home.Inner.ein }
       |    with { briefly "w1" }
       |  connector ToStore is { from outlet Home.In.aout to inlet Home.Store.sin }
       |    with { briefly "w2" }
       |  connector ToOther is { from outlet Home.In.aout to inlet Other.oin }
       |    with { briefly "w3" }
       |} with { briefly "d" }
       |""".stripMargin

  private def diagnostics(stmt: String, origin: String): Messages =
    var captured: Messages = Messages.empty
    parseAndValidate(model(stmt), origin, shouldFailOnErrors = false) { (_, _, messages) =>
      captured = messages
      succeed
    }
    captured

  private def offences(stmt: String, origin: String): Seq[String] =
    diagnostics(stmt, origin).map(_.message).filter(_.contains("may address only a context"))

  "an adaptor" should {

    "be REJECTED for a tell to an entity — the case the corpus is full of" in { (td: TestData) =>
      offences("tell command D.Cmd to entity Home.Inner", "adaptor-entity") mustNot be(empty)
    }

    "be REJECTED for a tell to a repository" in { (td: TestData) =>
      offences("tell command D.Cmd to repository Home.Store", "adaptor-repo") mustNot be(empty)
    }

    "be ACCEPTED for a tell to a context" in { (td: TestData) =>
      offences("tell command D.Cmd to context D.Other", "adaptor-context") mustBe empty
    }

    // A context's own portlet IS its public surface — the same indirection `send` already makes
    // for the boundary check, so judging the portlet's OWNER rather than the portlet keeps the
    // two rules consistent instead of making `send` the one shape that escapes.
    "be ACCEPTED for a send to a CONTEXT's portlet" in { (td: TestData) =>
      offences("send command D.Cmd to inlet Other.oin", "adaptor-ctx-portlet") mustBe empty
    }

    // Publishing on a portlet the adaptor OWNS is emission, not addressing -- §17's "a processor
    // publishes ONLY through its own outlet". Found by `SharedAdaptorTest`'s wrapper-adaptation
    // fixture, which the first draft of this rule broke: without the exemption an adaptor could
    // not emit at all, so the rule forbade the very shape it exists to require.
    "be ACCEPTED sending to an outlet it OWNS" in { (td: TestData) =>
      offences("send command D.Cmd to outlet Home.In.aout", "adaptor-own-outlet") mustBe empty
    }

    "be REJECTED for a send to an ENTITY's portlet" in { (td: TestData) =>
      offences("send command D.Cmd to inlet Home.Inner.ein", "adaptor-entity-portlet") mustNot be(
        empty
      )
    }
  }

  // The negative control for the whole rule: it must fire because the sender is an ADAPTOR, not
  // because the target is an entity. Without this, banning entity targets everywhere would look
  // identical to the rule working.
  "a non-adaptor sender" should {
    "be ACCEPTED telling an entity in its own context" in { (td: TestData) =>
      val src =
        """domain D is {
          |  command Cmd is { x: Integer } with { briefly "c" }
          |  context Home is {
          |    entity Inner is {
          |      inlet ein is command D.Cmd with { briefly "i" }
          |      handler ih is { on command D.Cmd { do "inner" } } with { briefly "h" }
          |    } with { briefly "e" }
          |    entity Caller is {
          |      outlet cout is command D.Cmd with { briefly "o" }
          |      handler ch is {
          |        on command D.Cmd { tell command D.Cmd to entity Home.Inner }
          |      } with { briefly "h" }
          |    } with { briefly "c" }
          |    connector W is { from outlet Home.Caller.cout to inlet Home.Inner.ein }
          |      with { briefly "w" }
          |  } with { briefly "hm" }
          |} with { briefly "d" }
          |""".stripMargin
      parseAndValidate(src, td.name, shouldFailOnErrors = false) { case (_, _, msgs: Messages) =>
        msgs.map(_.message).filter(_.contains("may address only a context")) mustBe empty
      }
    }
  }
}
