/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST.Adaptor
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.utils.PlatformContext
import org.scalatest.TestData

trait SharedAdaptorTest(using PlatformContext) extends AbstractValidatingTest {

  "Adaptors" should {
    "handle undefined body" in { (td: TestData) =>
      val input = RiddlParserInput(
        """adaptor PaymentAdapter from context Foo is {
          |  ???
          |}
          |""".stripMargin,
        td
      )
      parseDefinition[Adaptor](input) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right(_) => succeed
      }
    }

    "allow message actions" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain ignore is {
          |  context Target is {???}
          |  context Foo is {
          |    event ItHappened = { abc: String } with { described as "abc" }
          |    adaptor PaymentAdapter to context Target is {
          |      handler sendAMessage is {
          |        on event ItHappened {
          |          do "handle it"
          |        } with { described as "?" }
          |        on other { error "unexpected message" }
          |      } with { explained as "?" }
          |    } with {
          |      explained as "?"
          |    }
          |  } with {
          |    explained as "?"
          |  }
          |} with {
          |  explained as "?"
          |}
          |""".stripMargin,
        td
      )
      parseAndValidateDomain(input) { (_, _, messages) =>
        // `ItHappened` is declared and handled here but never emitted, which #17 correctly reports
        // -- this fixture is a focused adaptor test, not a complete model. Giving it a real emitter
        // was tried and is worse: an entity drags in the entity-completeness rules (needs a state,
        // a sink, a repository) and a source drags in the connector ones (outlet not connected).
        // So the one expected message is excluded by name rather than the assertion weakened.
        val unrelated = messages.filterNot(_.message.contains("nothing in the model emits it"))
        unrelated.isOnlyIgnorable mustBe true
      }
    }

    "allow wrapper adaptations" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain ignore is {
          | context Target is {???}
          | context Foo is {
          |  command ItWillHappen = { abc: String } with { described as "abc" }
          |  command  LetsDoIt is { bcd: String with { described as "abc" } } with { described as "?" }
          |
          |  entity MyEntity is {
          |    sink phum is { inlet commands is command LetsDoIt }
          |    handler x is { ??? }
          |  }
          |  connector only is {
          |    from outlet Foo.PaymentAdapter.foo.forMyEntity
          |    to inlet Foo.MyEntity.phum.commands
          |  }
          |  adaptor PaymentAdapter to context Target is {
          |    source foo is { outlet forMyEntity is command LetsDoIt }
          |    handler sendAMessage is {
          |      on command ItWillHappen  {
          |        send command Foo.LetsDoIt(bcd = "the bcd") to outlet forMyEntity
          |      } with { described as "?" }
          |      on other { error "unexpected message" }
          |    } with { explained as "?" }
          |  } with { explained as "?" }
          | } with {
          |  explained as "?"
          | }
          |} with { explained as "?" }
          |""".stripMargin,
        td
      )
      parseAndValidateDomain(input) { (domain, _, messages) =>
        domain.isEmpty must be(false)
        domain.contexts(1).adaptors.head.id.value must be("PaymentAdapter")
      }
    }

    "flag an adaptor handler that has no 'on other' clause as an error" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain d is {
          |  context Target is { command DoIt is { x: String } }
          |  context Src is {
          |    adaptor A to context Target is {
          |      handler H is {
          |        on command Target.DoIt { do "translate" }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseAndValidateDomain(input, shouldFailOnErrors = false) { (_, _, messages) =>
        assertValidationMessage(messages, Messages.Error, "has no 'on other' clause")
      }
    }

    "accept an adaptor handler that includes an 'on other' clause" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain d is {
          |  context Target is { command DoIt is { x: String } }
          |  context Src is {
          |    adaptor A to context Target is {
          |      handler H is {
          |        on command Target.DoIt { do "translate" }
          |        on other { error "unexpected message" }
          |      }
          |    }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseAndValidateDomain(input, shouldFailOnErrors = false) { (_, _, messages) =>
        assert(
          !messages.exists(_.message.contains("has no 'on other' clause")),
          s"unexpected on-other error present:\n${messages.format}"
        )
      }
    }

    // A4: isolation-seam validation. An adaptor may only traffic in messages owned by the two
    // contexts it bridges (its parent context and its referent context) or context-less
    // root/shared types. Referencing a THIRD context's message crosses the isolation seam.

    "not flag an adaptor that only references parent, referent, and shared messages" in {
      (td: TestData) =>
        val input = RiddlParserInput(
          """domain D is {
            |  event Shared is { s: String }
            |  context PaymentContext is {
            |    event PaymentCompleted is { orderId: String }
            |  }
            |  context OrderContext is {
            |    event OrderPaymentReceived is { id: String }
            |    outlet OrderEvents is event OrderPaymentReceived
            |    adaptor PayIn from context PaymentContext is {
            |      handler H is {
            |        on event PaymentContext.PaymentCompleted {
            |          send event OrderContext.OrderPaymentReceived to outlet OrderEvents
            |        }
            |        on event D.Shared { do "shared vocabulary is fine" }
            |        on other { error "unexpected message" }
            |      }
            |    }
            |  }
            |}
            |""".stripMargin,
          td
        )
        parseAndValidateDomain(input, shouldFailOnErrors = false) { (_, _, messages) =>
          assert(
            !messages.exists(_.message.contains("isolation seam")),
            s"unexpected seam message present:\n${messages.format}"
          )
        }
    }

    "flag a third-context message referenced in an adaptor 'on' clause as a seam error" in {
      (td: TestData) =>
        val input = RiddlParserInput(
          """domain D is {
            |  context PaymentContext is { event PaymentCompleted is { x: String } }
            |  context ShippingContext is { event ShipmentQueued is { y: String } }
            |  context OrderContext is {
            |    adaptor PayIn from context PaymentContext is {
            |      handler H is {
            |        on event ShippingContext.ShipmentQueued { do "ignore" }
            |        on other { error "unexpected message" }
            |      }
            |    }
            |  }
            |}
            |""".stripMargin,
          td
        )
        parseAndValidateDomain(input, shouldFailOnErrors = false) { (_, _, messages) =>
          val seamErrors = messages.filter { m =>
            m.kind == Messages.Error && m.message.contains("isolation seam")
          }
          assert(
            seamErrors.size == 1,
            s"expected exactly one seam error, got ${seamErrors.size}:\n${messages.format}"
          )
          assert(
            seamErrors.head.message
              .contains("references message 'ShippingContext.ShipmentQueued'") &&
              seamErrors.head.message.contains("from context 'ShippingContext'"),
            s"seam error text unexpected:\n${seamErrors.head.message}"
          )
          // It is a hard Error, not a mere Warning.
          assert(
            !messages.exists(m =>
              m.kind == Messages.Warning && m.message.contains("isolation seam")
            ),
            s"seam violation should be an Error, not a Warning:\n${messages.format}"
          )
          // No double-report with the generic cross-context reference check (disabled in adaptors).
          assert(
            !messages.exists(_.message.contains("violate the 'bounded' aspect")),
            s"generic cross-context warning should not fire inside an adaptor:\n${messages.format}"
          )
        }
    }

    "flag a third-context message referenced as a send/tell target as a seam error" in {
      (td: TestData) =>
        val input = RiddlParserInput(
          """domain D is {
            |  context PaymentContext is { event PaymentCompleted is { x: String } }
            |  context ShippingContext is {
            |    command QueueShipment is { z: String }
            |  }
            |  context OrderContext is {
            |    adaptor PayIn from context PaymentContext is {
            |      handler H is {
            |        on event PaymentContext.PaymentCompleted {
            |          tell command ShippingContext.QueueShipment to context ShippingContext
            |        }
            |        on other { error "unexpected message" }
            |      }
            |    }
            |  }
            |}
            |""".stripMargin,
          td
        )
        parseAndValidateDomain(input, shouldFailOnErrors = false) { (_, _, messages) =>
          val seamErrors = messages.filter { m =>
            m.kind == Messages.Error && m.message.contains("isolation seam")
          }
          assert(
            seamErrors.size == 1,
            s"expected exactly one seam error, got ${seamErrors.size}:\n${messages.format}"
          )
          assert(
            seamErrors.head.message
              .contains("references message 'ShippingContext.QueueShipment'"),
            s"seam error text unexpected:\n${seamErrors.head.message}"
          )
          assert(
            !messages.exists(m =>
              m.kind == Messages.Warning && m.message.contains("isolation seam")
            ),
            s"seam violation should be an Error, not a Warning:\n${messages.format}"
          )
        }
    }
  }
}
