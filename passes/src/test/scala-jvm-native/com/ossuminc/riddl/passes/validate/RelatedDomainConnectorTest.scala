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

/** A connector may cross a domain boundary when the two domains are RELATED — they share an
  * ancestor domain (Reid, 2026-09-03).
  *
  * **This existed to resolve a genuine contradiction, not to relax a rule.** A6 reachability became
  * an Error on 2026-09-02, and reactive-bbq's `Corporate -> Restaurant` tell then had no legal
  * spelling: omit the connector and A6 errors, add it and `stream-crosses-domains` errors. Two
  * rules, no model satisfying both.
  *
  * The rule's target is a connector between UNRELATED domains — "a failure of domain analysis" —
  * and a shared ancestor rules that out: it is movement inside one enterprise between two of its
  * own divisions. Top-level domains share no ancestor, so the protection is untouched, which is
  * what the negative case below pins.
  */
class RelatedDomainConnectorTest extends AbstractValidatingTest {

  /** Sibling domains under a common parent. */
  private val siblings: String =
    """domain Enterprise is {
      |  command Cmd is { x: Integer } with { briefly "c" }
      |  domain Alpha is {
      |    context A is {
      |      outlet aout is command Enterprise.Cmd with { briefly "o" }
      |      handler h is { on command Enterprise.Cmd { do "emit" } } with { briefly "h" }
      |    } with { briefly "a" }
      |  } with { briefly "al" }
      |  domain Beta is {
      |    context B is {
      |      inlet bin is command Enterprise.Cmd with { briefly "i" }
      |      handler h2 is { on command Enterprise.Cmd { do "handle" } } with { briefly "h" }
      |    } with { briefly "b" }
      |  } with { briefly "be" }
      |  connector Cross is { from outlet Alpha.A.aout to inlet Beta.B.bin } with { briefly "x" }
      |} with { briefly "e" }
      |""".stripMargin

  /** The same two domains with NO common parent -- both top level. The command stays in `Alpha`
    * and `Beta` refers to it, because a `command` cannot be declared at root scope (root admits
    * only domain/author/copyright/import/include/module/version) and a differently-declared type
    * on each side would fail the connector's type check instead, testing the wrong thing.
    */
  private val unrelated: String =
    """domain Alpha is {
      |  command Cmd is { x: Integer } with { briefly "c" }
      |  context A is {
      |    outlet aout is command Alpha.Cmd with { briefly "o" }
      |    handler h is { on command Alpha.Cmd { do "emit" } } with { briefly "h" }
      |  } with { briefly "a" }
      |  connector Cross is { from outlet Alpha.A.aout to inlet Beta.B.bin } with { briefly "x" }
      |} with { briefly "al" }
      |domain Beta is {
      |  context B is {
      |    inlet bin is command Alpha.Cmd with { briefly "i" }
      |    handler h2 is { on command Alpha.Cmd { do "handle" } } with { briefly "h" }
      |  } with { briefly "b" }
      |} with { briefly "be" }
      |""".stripMargin

  "a connector between domains" should {

    "be ACCEPTED when the domains are siblings under a common parent" in { (td: TestData) =>
      parseAndValidate(siblings, td.name, shouldFailOnErrors = false) {
        case (_, _, msgs: Messages) =>
          msgs.filter(_.message.contains("connects UNRELATED domains")) mustBe empty
      }
    }

    // The negative control. Without it, deleting the check entirely would look identical to
    // scoping it correctly -- and the whole point is that the protection survives.
    "still be REJECTED when the domains share no ancestor" in { (td: TestData) =>
      parseAndValidate(unrelated, td.name, shouldFailOnErrors = false) {
        case (_, _, msgs: Messages) =>
          assertValidationMessage(msgs, Error, "connects UNRELATED domains")
      }
    }

    // Relatedness is about a SHARED ancestor, not equal depth: the ruling was phrased for
    // siblings, and the reasoning -- divisions of one enterprise -- does not distinguish
    // `Corporate.Finance -> Restaurant.FrontOfHouse` from `Corporate -> Restaurant`.
    "be ACCEPTED when the endpoints sit at DIFFERENT depths under the common ancestor" in {
      (td: TestData) =>
        val src =
          """domain Enterprise is {
            |  command Cmd is { x: Integer } with { briefly "c" }
            |  domain Alpha is {
            |    domain Inner is {
            |      context A is {
            |        outlet aout is command Enterprise.Cmd with { briefly "o" }
            |        handler h is { on command Enterprise.Cmd { do "emit" } } with { briefly "h" }
            |      } with { briefly "a" }
            |    } with { briefly "in" }
            |  } with { briefly "al" }
            |  domain Beta is {
            |    context B is {
            |      inlet bin is command Enterprise.Cmd with { briefly "i" }
            |      handler h2 is { on command Enterprise.Cmd { do "handle" } } with { briefly "h" }
            |    } with { briefly "b" }
            |  } with { briefly "be" }
            |  connector Cross is { from outlet Alpha.Inner.A.aout to inlet Beta.B.bin }
            |    with { briefly "x" }
            |} with { briefly "e" }
            |""".stripMargin
        parseAndValidate(src, td.name, shouldFailOnErrors = false) {
          case (_, _, msgs: Messages) =>
            msgs.filter(_.message.contains("connects UNRELATED domains")) mustBe empty
        }
    }
  }
}
