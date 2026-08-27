/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.utils.pc

import org.scalatest.Assertion
import org.scalatest.TestData

/** The streaming reachability graph in [[StreamingValidation]] must be built over EVERY
  * [[com.ossuminc.riddl.language.AST.Processor]] kind, not only the `Streamlet` case class.
  *
  * Since the unified processor model, ports (`inlets`/`outlets`) and stream shape
  * (`ascribedShape`/`arityShape`/`effectiveShape`) live on `Processor`, so an Adaptor, Entity,
  * Projector, Repository or Context carrying ports is a real participant in a stream path. The
  * graph was left typed over the concrete `Streamlet` case class, which silently DROPPED the other
  * five kinds from the walk: a connector endpoint owned by any of them contributed no edge, so a
  * sink downstream of one was reported as having no upstream source.
  *
  * That produced a double bind the author could not escape. Rule 5
  * (`checkExternalContextConnectors`) advises inserting an adaptor between an external context and
  * a processor in another context, but taking that advice severed the reachability walk — while a
  * `processor as flow` in the same position kept the walk intact yet still drew the advisory,
  * because the advisory clears only for an `Adaptor`. No processor kind satisfied both checks.
  */
class ProcessorStreamGraphTest extends AbstractValidatingTest {

  /** External source -> adaptor -> sink: following Rule 5's advice exactly. */
  private val viaAdaptor: String =
    """domain Demo is {
      |  external context Ext is {
      |    event Happened is { what: String(1,50) }
      |    processor EventSource as source is {
      |      outlet Results is type Happened
      |    }
      |  }
      |  context Inside is {
      |    adaptor FromExt from context Demo.Ext as flow is {
      |      inlet FromOutside is type Ext.Happened
      |      outlet ToInside is type Ext.Happened
      |      handler Forward is {
      |        on event Ext.Happened is { do "forward it" }
      |      }
      |    }
      |    processor ResultSink as sink is {
      |      inlet Incoming is type Ext.Happened
      |      handler H is {
      |        on event Ext.Happened is { do "handle it" }
      |      }
      |    }
      |    connector AdaptorToSink is from outlet Inside.FromExt.ToInside to inlet Inside.ResultSink.Incoming
      |  }
      |  connector ExtToAdaptor is from outlet Ext.EventSource.Results to inlet Inside.FromExt.FromOutside with {
      |    option persistent()
      |  }
      |}
      |""".stripMargin

  /** Source -> entity -> sink, entirely within one context. No external context, no adaptor, no
    * Rule 5 anywhere: the general case of a non-`Streamlet` processor in a stream path.
    */
  private val viaEntity: String =
    """domain Demo is {
      |  context Inside is {
      |    event Happened is { what: String(1,50) }
      |    processor EventSource as source is {
      |      outlet Results is type Happened
      |    }
      |    entity Middle as flow is {
      |      inlet FromSource is type Happened
      |      outlet ToSink is type Happened
      |      handler H is {
      |        on event Happened is { do "relay it" }
      |      }
      |    }
      |    processor ResultSink as sink is {
      |      inlet Incoming is type Happened
      |      handler H is {
      |        on event Happened is { do "handle it" }
      |      }
      |    }
      |    connector SourceToEntity is from outlet Inside.EventSource.Results to inlet Inside.Middle.FromSource
      |    connector EntityToSink is from outlet Inside.Middle.ToSink to inlet Inside.ResultSink.Incoming
      |  }
      |}
      |""".stripMargin

  /** The same topology with a `processor as flow` in the middle. This ALREADY worked, because a
    * `processor` is a `Streamlet`; it guards against the fix regressing the case that was fine.
    */
  private val viaFlow: String =
    """domain Demo is {
      |  context Inside is {
      |    event Happened is { what: String(1,50) }
      |    processor EventSource as source is {
      |      outlet Results is type Happened
      |    }
      |    processor Middle as flow is {
      |      inlet FromSource is type Happened
      |      outlet ToSink is type Happened
      |      handler H is {
      |        on event Happened is { do "relay it" }
      |      }
      |    }
      |    processor ResultSink as sink is {
      |      inlet Incoming is type Happened
      |      handler H is {
      |        on event Happened is { do "handle it" }
      |      }
      |    }
      |    connector SourceToFlow is from outlet Inside.EventSource.Results to inlet Inside.Middle.FromSource
      |    connector FlowToSink is from outlet Inside.Middle.ToSink to inlet Inside.ResultSink.Incoming
      |  }
      |}
      |""".stripMargin

  /** A sink fed by a chain that genuinely originates at no source. Widening the graph must NOT
    * silence the real diagnostic — this is the check's whole purpose.
    */
  private val sinkWithNoSource: String =
    """domain Demo is {
      |  context Inside is {
      |    event Happened is { what: String(1,50) }
      |    processor Relay as flow is {
      |      inlet Unfed is type Happened
      |      outlet Onward is type Happened
      |      handler H is {
      |        on event Happened is { do "relay it" }
      |      }
      |    }
      |    processor ResultSink as sink is {
      |      inlet Incoming is type Happened
      |      handler H is {
      |        on event Happened is { do "handle it" }
      |      }
      |    }
      |    connector RelayToSink is from outlet Inside.Relay.Onward to inlet Inside.ResultSink.Incoming
      |  }
      |}
      |""".stripMargin

  /** A direct external-port -> sink link, with no intermediary at all. Rule 5's advisory SHOULD
    * fire here; pins the behaviour `ContextIntentionTest` also covers.
    */
  private val directToSink: String =
    """domain Demo is {
      |  external context Ext is {
      |    event Happened is { what: String(1,50) }
      |    processor EventSource as source is {
      |      outlet Results is type Happened
      |    }
      |  }
      |  context Inside is {
      |    processor ResultSink as sink is {
      |      inlet Incoming is type Ext.Happened
      |      handler H is {
      |        on event Ext.Happened is { do "handle it" }
      |      }
      |    }
      |  }
      |  connector ExtToInside is from outlet Ext.EventSource.Results to inlet Inside.ResultSink.Incoming with {
      |    option persistent()
      |  }
      |}
      |""".stripMargin

  private def noMessageContaining(msgs: Messages, fragment: String): Assertion =
    withClue(s"expected no message containing '$fragment':\n${msgs.format}\n") {
      msgs.filter(_.message.contains(fragment)) mustBe empty
    }

  "Streaming reachability over all Processor kinds" should {

    "reach a source through an Adaptor in the path" in { (td: TestData) =>
      parseAndValidate(viaAdaptor, td.name, shouldFailOnErrors = false) {
        case (_, _, msgs: Messages) =>
          noMessageContaining(msgs, "no upstream path")
          noMessageContaining(msgs, "no downstream path")
      }
    }

    "clear Rule 5's advisory once the adaptor it asked for is in place" in { (td: TestData) =>
      parseAndValidate(viaAdaptor, td.name, shouldFailOnErrors = false) {
        case (_, _, msgs: Messages) =>
          noMessageContaining(msgs, "Consider an adaptor")
      }
    }

    "reach a source through an Entity in the path" in { (td: TestData) =>
      parseAndValidate(viaEntity, td.name, shouldFailOnErrors = false) {
        case (_, _, msgs: Messages) =>
          noMessageContaining(msgs, "no upstream path")
          noMessageContaining(msgs, "no downstream path")
      }
    }

    "still reach a source through a Streamlet in the path (regression)" in { (td: TestData) =>
      parseAndValidate(viaFlow, td.name, shouldFailOnErrors = false) {
        case (_, _, msgs: Messages) =>
          noMessageContaining(msgs, "no upstream path")
          noMessageContaining(msgs, "no downstream path")
      }
    }

    /** Reid ruled 2026-08-14 that a chain head need only bear an OUTLET -- "a chain of
      * outlet-connector-inlet MUST start with an outlet (Source, Merge, Flow, Split, Router), never
      * a Sink". `Relay` is a flow, so it is a legitimate head, and this fixture no longer draws the
      * sink-reachability warning.
      *
      * **Nothing is lost, which is the point of asserting it here.** The fixture's actual defect is
      * that `Relay`'s inlet is fed by nothing, and that is reported PRECISELY -- "Inlet 'Unfed' is
      * not connected" -- naming the port at fault. The old warning said the SINK had no upstream
      * source, which pointed at the wrong definition and, on reactive-bbq, was simply false: data
      * enters that pipeline through an application context fed by users, so a correctly wired model
      * was reported. A duplicate, less accurate diagnostic was removed; the accurate one remains.
      */
    "report the UNFED INLET, not the sink, when a chain head has nothing feeding it" in {
      (td: TestData) =>
        parseAndValidate(sinkWithNoSource, td.name, shouldFailOnErrors = false) {
          case (_, _, msgs: Messages) =>
            noMessageContaining(msgs, "is a sink but has no upstream path from any source")
            assertValidationMessage(msgs, CompletenessWarning, "Inlet 'Unfed' is not connected")
        }
    }

    "still advise an adaptor for a direct external-to-sink connector" in { (td: TestData) =>
      parseAndValidate(directToSink, td.name, shouldFailOnErrors = false) {
        case (_, _, msgs: Messages) =>
          assertValidationMessage(
            msgs,
            StyleWarning,
            "Consider an adaptor between external context 'Ext'"
          )
      }
    }
  }
}
