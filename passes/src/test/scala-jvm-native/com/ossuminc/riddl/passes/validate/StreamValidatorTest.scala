/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.{At, Contents, Messages, *}
import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.passes.Riddl
import com.ossuminc.riddl.utils.pc
import com.ossuminc.riddl.utils.CommonOptions

import org.scalatest.TestData

/** Test cases for the StreamValidator */
class StreamValidatorTest extends AbstractValidatingTest {

  "StreamValidator" must {
    "error on connector type mismatch" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain uno {
        | type Typ1 = Integer
        | type Typ2 = Real
        | context a {
        |  flow foo is {
        |   inlet in is type uno.Typ1
        |   outlet out is type uno.Typ2
        |  }
        |  connector c1 is { from outlet a.foo.out to inlet a.foo.in }
        | }
        |} """.stripMargin,
        td
      )
      pc.withOptions(CommonOptions.noMinorWarnings) { _ =>
        parseAndValidateDomain(input, shouldFailOnErrors = false) { case (domain, _, messages) =>
          domain.isEmpty must be(false)
          messages.isEmpty must be(false)
          messages.hasErrors must be(true)
          val errors = messages.justErrors
          info(errors.format)
          errors.exists { (msg: Messages.Message) =>
            msg.message.startsWith("Type mismatch in Connector 'c1':")
          } must be(true)
        }
      }
    }
    "error on unattached inlets" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain solo {
          | type T = Integer
          | context a {
          |  merge confluence is {
          |    inlet one is type T
          |    inlet two is type T
          |    outlet out is type T
          |  }
          |  connector c1 {
          |    from outlet a.confluence.out to inlet a.confluence.two
          |  }
          | }
          |} """.stripMargin,
        td
      )
      parseAndValidateDomain(input) { case (domain, _, messages) =>
        domain.isEmpty mustBe false
        messages.isEmpty mustBe false
        messages.hasErrors mustBe false
        messages.exists(
          _.message.startsWith("Inlet 'one' is not connected")
        ) mustBe true
      }
    }
    "error on unattached outlets" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain solo {
          | type T = Integer
          | context a is {
          |  source from is {
          |    outlet out is type T
          |  }
          | }
          |} """.stripMargin,
        td
      )
      parseAndValidateDomain(input, shouldFailOnErrors = true) { case (domain, _, messages) =>
        domain.isEmpty mustBe false
        messages.isEmpty mustBe false
        messages.hasErrors mustBe false
        messages.exists(
          _.message == "Outlet 'out' is not connected"
        ) mustBe true
      }
    }

    // ---- Connectors at Domain scope: placement rules + persistence (2.0) ----

    "completeness-warn a domain-scoped cross-context connector lacking persistence" in {
      (td: TestData) =>
        val input = RiddlParserInput(
          """domain uno is {
            | type T = Integer
            | context a is { outlet out is type uno.T }
            | context b is { inlet in is type uno.T }
            | connector c1 is { from outlet uno.a.out to inlet uno.b.in }
            |}""".stripMargin,
          td
        )
        parseAndValidateDomain(input, shouldFailOnErrors = false) { case (_, _, messages) =>
          messages.hasErrors mustBe false // domain-scoped cross-context is CORRECT placement
          messages
            .filter(_.kind == Messages.CompletenessWarning)
            .exists(
              _.message.contains("spans a context boundary but is not 'persistent'")
            ) mustBe true
        }
    }

    "accept a domain-scoped cross-context connector that is persistent" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain uno is {
          | type T = Integer
          | context a is { outlet out is type uno.T }
          | context b is { inlet in is type uno.T }
          | connector c1 is { from outlet uno.a.out to inlet uno.b.in } with { option persistent }
          |}""".stripMargin,
        td
      )
      parseAndValidateDomain(input, shouldFailOnErrors = false) { case (_, _, messages) =>
        messages.hasErrors mustBe false
        messages.exists(_.message.contains("spans a context boundary")) mustBe false
      }
    }

    // ---- Boundary encapsulation (Reid's ruling, 2026-08-18): C, an Error ----
    //
    // A cross-context connector must terminate on the CONTEXT'S OWN portlet at each end. Reaching
    // past the boundary onto a contained definition's portlet contradicts the bounded context
    // rather than under-stating it: a context publishes its message set and keeps its
    // representations private, so binding a peer to a contained entity's existence and to its
    // current command/query set means that entity can no longer change without breaking a
    // stranger. Hence an Error, not a CompletenessWarning.

    "error on a cross-context connector that reaches past the target's boundary" in {
      (td: TestData) =>
        val input = RiddlParserInput(
          """domain uno is {
            | type T = Integer
            | context a is { outlet out is type uno.T }
            | context b is { sink snk is { inlet in is type uno.T } }
            | connector c1 is { from outlet uno.a.out to inlet uno.b.snk.in }
            |}""".stripMargin,
          td
        )
        parseAndValidateDomain(input, shouldFailOnErrors = false) { case (_, _, messages) =>
          messages.hasErrors mustBe true
          messages.exists(
            _.message.contains("arrives at an inlet of")
          ) mustBe true
        }
    }

    "error on a cross-context connector that reaches past the source's boundary" in {
      (td: TestData) =>
        val input = RiddlParserInput(
          """domain uno is {
            | type T = Integer
            | context a is { source src is { outlet out is type uno.T } }
            | context b is { inlet in is type uno.T }
            | connector c1 is { from outlet uno.a.src.out to inlet uno.b.in }
            |}""".stripMargin,
          td
        )
        parseAndValidateDomain(input, shouldFailOnErrors = false) { case (_, _, messages) =>
          messages.hasErrors mustBe true
          messages.exists(
            _.message.contains("leaves from an outlet of")
          ) mustBe true
        }
    }

    "not apply boundary encapsulation within a single context" in { (td: TestData) =>
      // Intra-context, anything may talk to anything: a connector may drive a contained
      // definition's own portlet directly, and the boundary rule does not engage at all.
      val input = RiddlParserInput(
        """domain uno is {
          | type T = Integer
          | context a is {
          |   source src is { outlet out is type uno.T }
          |   sink snk is { inlet in is type uno.T }
          |   connector c1 is { from outlet uno.a.src.out to inlet uno.a.snk.in }
          | }
          |}""".stripMargin,
        td
      )
      parseAndValidateDomain(input, shouldFailOnErrors = false) { case (_, _, messages) =>
        messages.exists(m =>
          m.message.contains("arrives at an inlet of") || m.message.contains(
            "leaves from an outlet of"
          )
        ) mustBe false
      }
    }

    // ---- Crossing, not touching; and do not advise an adaptor that exists ----
    //
    // Both filed by riddl-models 2026-08-18 as consequences of the boundary Error. The first was a
    // TRAP: a connector wholly inside one external context got an Error demanding `persistent` and,
    // once given it, a Warning saying persistence was unneeded -- no legal spelling existed.

    "not demand persistence for a connector wholly inside one external context" in {
      (td: TestData) =>
        val input = RiddlParserInput(
          """domain uno is {
            | type T = Integer
            | external context Ext is {
            |   inlet ein is type uno.T
            |   source Src is { outlet Emitted is type uno.T }
            |   connector Inside is { from outlet uno.Ext.Src.Emitted to inlet uno.Ext.ein }
            | }
            |}""".stripMargin,
          td
        )
        pc.withOptions(CommonOptions.default) { _ =>
          parseAndValidateDomain(input, shouldFailOnErrors = false) { case (_, _, messages) =>
            // Neither half of the old contradiction may fire.
            messages.exists(_.message.contains("must be 'persistent'")) mustBe false
            messages.exists(_.message.contains("is not needed")) mustBe false
          }
        }
    }

    "still demand persistence when a connector actually CROSSES into an external context" in {
      (td: TestData) =>
        val input = RiddlParserInput(
          """domain uno is {
            | type T = Integer
            | external context Ext is { outlet eout is type uno.T }
            | context Home is { inlet hin is type uno.T }
            | connector Crossing is { from outlet uno.Ext.eout to inlet uno.Home.hin }
            |}""".stripMargin,
          td
        )
        pc.withOptions(CommonOptions.default) { _ =>
          parseAndValidateDomain(input, shouldFailOnErrors = false) { case (_, _, messages) =>
            messages.exists(_.message.contains("must be 'persistent'")) mustBe true
          }
        }
    }

    "not advise an adaptor when the peer context already has one for that external context" in {
      (td: TestData) =>
        val input = RiddlParserInput(
          """domain uno is {
            | type T = Integer
            | external context Ext is { outlet eout is type uno.T }
            | context Home is {
            |   inlet hin is type uno.T
            |   adaptor Defender from context uno.Ext is { ??? }
            | }
            | persistent connector Crossing is { from outlet uno.Ext.eout to inlet uno.Home.hin }
            |}""".stripMargin,
          td
        )
        pc.withOptions(CommonOptions.default) { _ =>
          parseAndValidateDomain(input, shouldFailOnErrors = false) { case (_, _, messages) =>
            messages.exists(_.message.contains("Consider an adaptor")) mustBe false
          }
        }
    }

    "still advise an adaptor when the peer context has none for that external context" in {
      (td: TestData) =>
        val input = RiddlParserInput(
          """domain uno is {
            | type T = Integer
            | external context Ext is { outlet eout is type uno.T }
            | external context Other is { outlet oout is type uno.T }
            | context Home is {
            |   inlet hin is type uno.T
            |   adaptor Defender from context uno.Other is { ??? }
            | }
            | persistent connector Crossing is { from outlet uno.Ext.eout to inlet uno.Home.hin }
            |}""".stripMargin,
          td
        )
        pc.withOptions(CommonOptions.default) { _ =>
          parseAndValidateDomain(input, shouldFailOnErrors = false) { case (_, _, messages) =>
            // Defended against `Other`, not against `Ext` -- the advisory is still useful here.
            messages.exists(_.message.contains("Consider an adaptor")) mustBe true
          }
        }
    }

    "error on a context-scoped connector that crosses contexts (under-scoped)" in {
      (td: TestData) =>
        val input = RiddlParserInput(
          """domain uno is {
            | type T = Integer
            | context a is {
            |   source src is { outlet out is type uno.T }
            |   connector c1 is { from outlet uno.a.src.out to inlet uno.b.snk.in }
            | }
            | context b is { sink snk is { inlet in is type uno.T } }
            |}""".stripMargin,
          td
        )
        parseAndValidateDomain(input, shouldFailOnErrors = false) { case (_, _, messages) =>
          messages.justErrors.exists(_.message.contains("under-scoped")) mustBe true
        }
    }

    "error on a domain-scoped connector whose ends are in the same context (over-scoped)" in {
      (td: TestData) =>
        val input = RiddlParserInput(
          """domain uno is {
            | type T = Integer
            | context a is { flow f is { inlet in is type uno.T outlet out is type uno.T } }
            | connector c1 is { from outlet uno.a.f.out to inlet uno.a.f.in }
            |}""".stripMargin,
          td
        )
        parseAndValidateDomain(input, shouldFailOnErrors = false) { case (_, _, messages) =>
          messages.justErrors.exists(_.message.contains("over-scoped")) mustBe true
        }
    }

    // INVERTED 2026-09-03, and kept pointing the other way rather than deleted. This fixture is
    // `d1` and `d2` SIBLINGS under `parent` -- precisely the shape Reid ruled permitted, because
    // the rule's target is a connector between UNRELATED domains and a shared ancestor rules that
    // out. It had to change: A6 reachability became an Error the day before, and reactive-bbq's
    // `Corporate -> Restaurant` tell then had no legal spelling -- omit the connector and A6
    // errors, add it and this check errored. Two rules, no model satisfying both.
    // Rejection of genuinely unrelated domains is pinned (and canaried) in
    // `RelatedDomainConnectorTest`, which can express top-level domains as this helper cannot.
    "ACCEPT a connector between sibling domains under a common parent" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain parent is {
          | domain d1 is { type T = Integer context a is { source src is { outlet out is type parent.d1.T } } }
          | domain d2 is { type U = Integer context b is { sink snk is { inlet in is type parent.d2.U } } }
          | connector c1 is { from outlet parent.d1.a.src.out to inlet parent.d2.b.snk.in }
          |}""".stripMargin,
        td
      )
      parseAndValidateDomain(input, shouldFailOnErrors = false) { case (_, _, messages) =>
        messages.justErrors.exists(_.message.contains("UNRELATED domains")) mustBe false
      }
    }
    "warn about useless persistence option" in { (td: TestData) =>
      pc.withOptions(CommonOptions.default) { _ =>
        val input = RiddlParserInput(
          """domain uno {
            | type T = Integer
            | context a {
            |  flow through is {
            |    inlet in is type T
            |    outlet out is type T
            |  }
            |  connector c1 {
            |    from outlet a.through.out to inlet uno.a.through.in
            |  } with {
            |   option persistent
            |  }
            | }
            |} """.stripMargin,
          td
        )
        parseAndValidateDomain(input) { case (domain, _, messages) =>
          domain.isEmpty must be(false)
          domain.contents.size must be(2)
          messages.isEmpty must be(false)
          messages.hasErrors must be(false)
          messages.filter(_.message.contains("is not needed since both end")) match
            case List(message) =>
              // Reworded 2026-08-13: persistence can now come from the `persistent` INTENTION as
              // well as the deprecated option, so the message no longer says "option".
              message.message must include("Persistence on Connector 'c1' is not")
              succeed
            case Nil => fail("Missing message")
            case _   => fail("Unexpected message count")
        }
      }
    }

    def pid(name: String): PathIdentifier = PathIdentifier(At(), Seq("domain", "context", name))

    def inlet(name: String, pidName: String): Inlet =
      Inlet(At(), Identifier(At(), name), TypeRef(At(), "type", pid(pidName)))

    def outlet(name: String, pidName: String): Outlet =
      Outlet(At(), Identifier(At(), name), TypeRef(At(), "type", pid(pidName)))

    def root(streamlets: Seq[Streamlet]): Root = {
      Root(
        At(),
        Contents(
          Domain(
            At(),
            Identifier(At(), "domain"),
            Contents(
              Context(
                At(),
                Identifier(At(), "context"),
                Contents(
                  Type(At(), Identifier(At(), "Int"), Integer(At()))
                ) ++ (streamlets)
              )
            )
          )
        )
      )
    }

    "validate Streamlet types" in { _ =>
      // A32/Task 10: the AST no longer throws IllegalArgumentException on a shape/arity
      // mismatch (crashing on user input is wrong). Instead ValidationPass emits an Error
      // when the ascribed shape disagrees with the arity-derived shape. Each case below
      // constructs a streamlet whose ascribed shape contradicts its ports and asserts a
      // validation Error mentioning the ascription and the arity.
      def mismatch(streamlet: Streamlet): Messages.Messages = {
        pc.withOptions(CommonOptions.noMinorWarnings) { _ =>
          Riddl.validate(root(Seq(streamlet)), shouldFailOnError = false) match {
            case Left(messages)   => messages
            case Right(passesRes) => passesRes.messages
          }
        }
      }
      def assertShapeError(streamlet: Streamlet, ascribed: String): Unit = {
        val messages = mismatch(streamlet)
        messages.hasErrors must be(true)
        messages.justErrors.exists { (m: Messages.Message) =>
          m.message.contains(s"is ascribed 'as $ascribed'") && m.message.contains("arity")
        } must be(true)
      }

      // source with 2 inlets, 0 outlets -> arity is not a source
      assertShapeError(
        Streamlet(
          At(),
          Identifier(At(), "src"),
          Some(Source(At())),
          Contents[StreamletContents](inlet("in1", "Int"), inlet("in2", "Int"))
        ),
        "source"
      )
      // sink with 1 inlet + 1 outlet -> arity is a flow
      assertShapeError(
        Streamlet(
          At(),
          Identifier(At(), "snk"),
          Some(Sink(At())),
          Contents[StreamletContents](inlet("in1", "Int"), outlet("out1", "Int"))
        ),
        "sink"
      )
      // flow with 2 outlets, 0 inlets
      assertShapeError(
        Streamlet(
          At(),
          Identifier(At(), "flw"),
          Some(Flow(At())),
          Contents[StreamletContents](outlet("out1", "Int"), outlet("out2", "Int"))
        ),
        "flow"
      )
      // split with 2 outlets, 0 inlets
      assertShapeError(
        Streamlet(
          At(),
          Identifier(At(), "spl"),
          Some(Split(At())),
          Contents[StreamletContents](outlet("out1", "Int"), outlet("out2", "Int"))
        ),
        "split"
      )
      // merge with 2 inlets, 0 outlets
      assertShapeError(
        Streamlet(
          At(),
          Identifier(At(), "mrg"),
          Some(Merge(At())),
          Contents[StreamletContents](inlet("in1", "Int"), inlet("in2", "Int"))
        ),
        "merge"
      )
      // router with 1 outlet, 0 inlets
      assertShapeError(
        Streamlet(
          At(),
          Identifier(At(), "rtr"),
          Some(Router(At())),
          Contents[StreamletContents](outlet("out2", "Int"))
        ),
        "router"
      )
      // void with 1 inlet + 1 outlet -> arity is a flow
      assertShapeError(
        Streamlet(
          At(),
          Identifier(At(), "vd"),
          Some(Void(At())),
          Contents[StreamletContents](inlet("in1", "Int"), outlet("out2", "Int"))
        ),
        "void"
      )
    }

    // ---- A31 (Task 9): exactly one connector per inlet and per outlet ----

    "error when more than one connector attaches to a single inlet" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain d is {
          | type T = Integer
          | context c is {
          |  source s1 is { outlet o1 is type T }
          |  source s2 is { outlet o2 is type T }
          |  sink k is { inlet in is type T }
          |  connector a is { from outlet c.s1.o1 to inlet c.k.in }
          |  connector b is { from outlet c.s2.o2 to inlet c.k.in }
          | }
          |}""".stripMargin,
        td
      )
      parseAndValidateDomain(input, shouldFailOnErrors = false) { case (_, _, messages) =>
        val matching = messages.justErrors.filter { m =>
          m.message.contains("Inlet 'in' is connected by 2 connectors")
        }
        matching.size must be(1)
      }
    }

    "not error when exactly one connector attaches to each portlet" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain d is {
          | type T = Integer
          | context c is {
          |  source s is { outlet o is type T }
          |  sink k is { inlet in is type T }
          |  connector a is { from outlet c.s.o to inlet c.k.in }
          | }
          |}""".stripMargin,
        td
      )
      parseAndValidateDomain(input, shouldFailOnErrors = false) { case (_, _, messages) =>
        messages.justErrors.exists(_.message.contains("exactly one is allowed")) must be(false)
      }
    }

    // ---- A32 (Task 10): as-shape vs arity ascription + omitted-shape nudge ----

    "style-warn a ported processor with no 'as' ascription" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain d is {
          | type T = Integer
          | context c is {
          |  processor P is { inlet i is type T outlet o is type T handler h is { ??? } }
          | }
          |}""".stripMargin,
        td
      )
      pc.withOptions(CommonOptions.default) { _ =>
        parseAndValidateDomain(input, shouldFailOnErrors = false) { case (_, _, messages) =>
          messages
            .filter(_.kind == Messages.StyleWarning)
            .exists(_.message.contains("has ports but no 'as <shape>' ascription")) must be(true)
        }
      }
    }

    "not style-warn a ported processor that ascribes a matching shape" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain d is {
          | type T = Integer
          | context c is {
          |  processor P as flow is { inlet i is type T outlet o is type T handler h is { ??? } }
          | }
          |}""".stripMargin,
        td
      )
      pc.withOptions(CommonOptions.default) { _ =>
        parseAndValidateDomain(input, shouldFailOnErrors = false) { case (_, _, messages) =>
          messages.exists(_.message.contains("has ports but no 'as <shape>' ascription")) must be(
            false
          )
          messages.justErrors.exists(_.message.contains("is ascribed 'as")) must be(false)
        }
      }
    }

    "error when a processor's 'as' ascription contradicts its arity" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain d is {
          | type T = Integer
          | context c is {
          |  processor P as source is { inlet i is type T handler h is { ??? } }
          | }
          |}""".stripMargin,
        td
      )
      pc.withOptions(CommonOptions.noMinorWarnings) { _ =>
        parseAndValidateDomain(input, shouldFailOnErrors = false) { case (_, _, messages) =>
          messages.justErrors.exists { m =>
            m.message.contains("is ascribed 'as source'") && m.message.contains("arity")
          } must be(true)
        }
      }
    }

    // ---- A7-ext: async over-parallelization warning (fully-async pipeline) ----

    def asyncWarnings(messages: Messages.Messages): Seq[Messages.Message] =
      messages.filter(m => m.kind == Messages.StyleWarning && m.message.contains("fused anywhere"))

    "style-warn a streaming pipeline whose every portlet is 'async'" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain d is {
          | type T = Integer
          | context c is {
          |  source s is { outlet o is type T with { option async } }
          |  flow f is {
          |    inlet fin is type T with { option async }
          |    outlet fout is type T with { option async }
          |  }
          |  sink k is { inlet in is type T with { option async } }
          |  connector a is { from outlet c.s.o to inlet c.f.fin }
          |  connector b is { from outlet c.f.fout to inlet c.k.in }
          | }
          |}""".stripMargin,
        td
      )
      pc.withOptions(CommonOptions.default) { _ =>
        parseAndValidateDomain(input, shouldFailOnErrors = false) { case (_, _, messages) =>
          asyncWarnings(messages).size must be(1)
        }
      }
    }

    "not async-warn a pipeline that has at least one non-async portlet" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain d is {
          | type T = Integer
          | context c is {
          |  source s is { outlet o is type T with { option async } }
          |  flow f is {
          |    inlet fin is type T with { option async }
          |    outlet fout is type T
          |  }
          |  sink k is { inlet in is type T with { option async } }
          |  connector a is { from outlet c.s.o to inlet c.f.fin }
          |  connector b is { from outlet c.f.fout to inlet c.k.in }
          | }
          |}""".stripMargin,
        td
      )
      pc.withOptions(CommonOptions.default) { _ =>
        parseAndValidateDomain(input, shouldFailOnErrors = false) { case (_, _, messages) =>
          asyncWarnings(messages) must be(empty)
        }
      }
    }

    "not async-warn a single async portlet that forms no full pipeline" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain d is {
          | type T = Integer
          | context c is {
          |  source s is { outlet o is type T with { option async } }
          | }
          |}""".stripMargin,
        td
      )
      pc.withOptions(CommonOptions.default) { _ =>
        parseAndValidateDomain(input, shouldFailOnErrors = false) { case (_, _, messages) =>
          asyncWarnings(messages) must be(empty)
        }
      }
    }

    "not async-warn a fully non-async pipeline" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain d is {
          | type T = Integer
          | context c is {
          |  source s is { outlet o is type T }
          |  sink k is { inlet in is type T }
          |  connector a is { from outlet c.s.o to inlet c.k.in }
          | }
          |}""".stripMargin,
        td
      )
      pc.withOptions(CommonOptions.default) { _ =>
        parseAndValidateDomain(input, shouldFailOnErrors = false) { case (_, _, messages) =>
          asyncWarnings(messages) must be(empty)
        }
      }
    }
  }
}
