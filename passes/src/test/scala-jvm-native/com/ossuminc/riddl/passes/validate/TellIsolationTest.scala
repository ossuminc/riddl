/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.utils.{CommonOptions, pc}
import org.scalatest.TestData

/** The cross-context `tell` isolation seam (Reid, 2026-08-13; shipped 2026-08-16).
  *
  * A `tell` into a DIFFERENT context is an Error unless the message type is declared in a Domain
  * ancestral to both. Across domains an adaptor is always required. This completes A4, which
  * already rejects naming a foreign context's message TYPES outside adaptor scope; the seam now
  * covers foreign processor TARGETS too.
  *
  * **`send` is deliberately NOT covered**, and that is a finding rather than an omission:
  * `SendStatement.portlet` is a `PortletRef`, so `send` names an Inlet or Outlet and structurally
  * cannot name a foreign processor at all. A message crossing a context boundary by `send` travels
  * through a CONNECTOR, which is the sanctioned mediator for streaming exactly as an adaptor is for
  * direct messaging.
  *
  * **Shipped as an Error with no warning period**, against this repo's usual warn-then-flip
  * sequencing, because the census made that ceremony unnecessary: 18 crossings in 7,537 tells
  * (0.24%), all in two models. The old text-based estimate said 5,301 (64%) and was the sole
  * argument for a staged rollout.
  */
class TellIsolationTest extends AbstractValidatingTest {

  private def diagnostics(src: String, origin: String): Messages =
    var captured: Messages = Messages.empty
    pc.withOptions(CommonOptions(showStyleWarnings = true, showWarnings = true)) { _ =>
      parseAndValidate(src, origin, shouldFailOnErrors = false) { (_, _, messages) =>
        captured = messages
        succeed
      }
    }
    captured.find(_.message.contains("Expected one of")) match
      case Some(m) => fail(s"fixture did not parse, so any absence proves nothing:\n${m.format}")
      case None    => captured

  private def errorsIn(src: String, origin: String): String =
    diagnostics(src, origin).justErrors.map(_.message).mkString("\n")

  /** Two contexts under ONE domain. `where` places the `Ship` command — "shared" puts it at domain
    * level (the exemption), "local" leaves it inside the target's context (the violation).
    */
  private def oneDomain(where: String): String =
    val shared = if where == "shared" then """command Ship is { why: String } with { briefly "s" }""" else ""
    val local = if where == "local" then """command Ship is { why: String } with { briefly "s" }""" else ""
    s"""domain Dom is {
       |  $shared
       |  context Target is {
       |    $local
       |    record R is { total: Integer } with { briefly "r" }
       |    entity Order is {
       |      state OS of record R is {
       |        handler OH is { on command Ship { do "ship" } } with { briefly "oh" }
       |      } with { briefly "os" }
       |    } with { briefly "e" }
       |  } with { briefly "tc" }
       |  context Caller is {
       |    command Go is { why: String } with { briefly "g" }
       |    record CR is { total: Integer } with { briefly "cr" }
       |    entity Sender is {
       |      state CS of record CR is {
       |        handler CH is {
       |          on command Go { tell command Ship(why = "x") to entity Target.Order }
       |        } with { briefly "ch" }
       |      } with { briefly "cs" }
       |    } with { briefly "ce" }
       |  } with { briefly "cc" }
       |} with { briefly "d" }
       |""".stripMargin

  "the cross-context tell seam" should {

    "ACCEPT a tell within one context" in { (td: TestData) =>
      val src =
        """domain Dom is {
          |  context Ctx is {
          |    command Ship is { why: String } with { briefly "s" }
          |    command Go is { why: String } with { briefly "g" }
          |    record R is { total: Integer } with { briefly "r" }
          |    entity Order is {
          |      state OS of record R is {
          |        handler OH is { on command Ship { do "ship" } } with { briefly "oh" }
          |      } with { briefly "os" }
          |    } with { briefly "e" }
          |    entity Sender is {
          |      state CS of record R is {
          |        handler CH is {
          |          on command Go { tell command Ship(why = "x") to entity Order }
          |        } with { briefly "ch" }
          |      } with { briefly "cs" }
          |    } with { briefly "ce" }
          |  } with { briefly "c" }
          |} with { briefly "d" }
          |""".stripMargin
      errorsIn(src, td.name) must not include "isolation"
    }

    "REJECT a tell into another context when the message is that context's own" in {
      (td: TestData) =>
        errorsIn(oneDomain("local"), td.name) must include("isolation")
    }

    "ACCEPT a tell into another context when the message is declared in the shared domain" in {
      (td: TestData) =>
        // The exemption Reid's ruling grants: a type owned by the common ancestor is not FOREIGN
        // to either context, so no adaptor is required for the two to speak it.
        errorsIn(oneDomain("shared"), td.name) must not include "isolation"
    }

    "REJECT a tell across DOMAINS even when the message is visible" in { (td: TestData) =>
      // No shared domain means no common owner for the type, so an adaptor is ALWAYS required --
      // the exemption cannot apply however the message is declared.
      val src =
        """domain A is {
          |  context Target is {
          |    command Ship is { why: String } with { briefly "s" }
          |    record R is { total: Integer } with { briefly "r" }
          |    entity Order is {
          |      state OS of record R is {
          |        handler OH is { on command Ship { do "ship" } } with { briefly "oh" }
          |      } with { briefly "os" }
          |    } with { briefly "e" }
          |  } with { briefly "tc" }
          |} with { briefly "da" }
          |domain B is {
          |  context Caller is {
          |    command Go is { why: String } with { briefly "g" }
          |    record CR is { total: Integer } with { briefly "cr" }
          |    entity Sender is {
          |      state CS of record CR is {
          |        handler CH is {
          |          on command Go { tell command A.Target.Ship(why = "x") to entity A.Target.Order }
          |        } with { briefly "ch" }
          |      } with { briefly "cs" }
          |    } with { briefly "ce" }
          |  } with { briefly "cc" }
          |} with { briefly "db" }
          |""".stripMargin
      errorsIn(src, td.name) must include("isolation")
    }
  }
}
