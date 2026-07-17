/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.analysis

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{Finder, Messages}
import com.ossuminc.riddl.passes.*
import com.ossuminc.riddl.passes.resolve.{ResolutionOutput, ResolutionPass}
import com.ossuminc.riddl.passes.symbols.{SymbolsOutput, SymbolsPass}
import com.ossuminc.riddl.passes.validate.ValidationPass
import com.ossuminc.riddl.utils.PlatformContext

import scala.collection.mutable
import scala.scalajs.js.annotation.*

/** The mechanism by which a message flows between producer and consumer */
enum FlowMechanism:
  case Tell, Send, AdaptorBridge, ConnectorPipe

/** An edge in the message flow graph representing a message sent from a
  * producer to a consumer
  *
  * @param producer
  *   The definition that produces/sends the message
  * @param consumer
  *   The definition that consumes/receives the message
  * @param messageType
  *   The type of message being transmitted
  * @param mechanism
  *   How the message is delivered (tell, send, adaptor, connector)
  */
@JSExportTopLevel("MessageFlowEdge")
case class MessageFlowEdge(
  producer: Definition,
  consumer: Definition,
  messageType: Option[Type],
  mechanism: FlowMechanism
)

/** Output of the MessageFlowPass containing the directed graph of message
  * flows across the entire model
  *
  * @param root
  *   The root of the model
  * @param messages
  *   Any messages generated during analysis
  * @param edges
  *   All message flow edges discovered
  * @param producerIndex
  *   Edges indexed by producer definition
  * @param consumerIndex
  *   Edges indexed by consumer definition
  * @param messageIndex
  *   Edges indexed by message type
  */
@JSExportTopLevel("MessageFlowOutput")
case class MessageFlowOutput(
  root: PassRoot = Root.empty,
  messages: Messages.Messages = Messages.empty,
  edges: Seq[MessageFlowEdge] = Seq.empty,
  producerIndex: Map[Definition, Seq[MessageFlowEdge]] = Map.empty,
  consumerIndex: Map[Definition, Seq[MessageFlowEdge]] = Map.empty,
  messageIndex: Map[Type, Seq[MessageFlowEdge]] = Map.empty
) extends PassOutput:

  /** Edges where producer or consumer is within the given domain */
  def edgesForDomain(domain: Domain, symbols: SymbolsOutput): Seq[MessageFlowEdge] =
    edges.filter: edge =>
      isWithin(edge.producer, domain, symbols) ||
        isWithin(edge.consumer, domain, symbols)

  /** Edges where producer or consumer is within the given context */
  def edgesForContext(context: Context, symbols: SymbolsOutput): Seq[MessageFlowEdge] =
    edges.filter: edge =>
      isWithin(edge.producer, context, symbols) ||
        isWithin(edge.consumer, context, symbols)

  private def isWithin(defn: Definition, ancestor: Definition, symbols: SymbolsOutput): Boolean =
    (defn == ancestor) || symbols.parentsOf(defn).contains(ancestor)
end MessageFlowOutput

@JSExportTopLevel("MessageFlowPass$")
object MessageFlowPass extends PassInfo[PassOptions] {
  val name: String = "MessageFlow"
  def creator(
    options: PassOptions = PassOptions.empty
  )(using PlatformContext): PassCreator = {
    (in: PassInput, out: PassesOutput) => MessageFlowPass(in, out)
  }
}

/** A pass that builds a directed graph of message producers and consumers
  * across the entire model. This is the core data structure both the
  * simulator and generator need for understanding message routing.
  */
@JSExportTopLevel("MessageFlowPass")
case class MessageFlowPass(
  input: PassInput,
  outputs: PassesOutput
)(using PlatformContext)
    extends Pass(input, outputs) {

  requires(SymbolsPass)
  requires(ResolutionPass)
  requires(ValidationPass)

  override def name: String = MessageFlowPass.name

  private lazy val refMap = outputs.refMap
  private lazy val symTab = outputs.symbols

  private val collectedEdges: mutable.ListBuffer[MessageFlowEdge] =
    mutable.ListBuffer.empty

  protected def process(
    definition: RiddlValue,
    parents: ParentStack
  ): Unit = {
    val parentsSeq = parents.toParents
    definition match
      case handler: Handler =>
        val processorParent = parentsSeq.headOption.collect {
          case p: Processor[?] => p
        }
        processorParent.foreach { processor =>
          handler.clauses.foreach {
            case omc: OnMessageClause =>
              processOnMessageClause(omc, processor, parentsSeq)
            case _ => ()
          }
        }
      case adaptor: Adaptor =>
        processAdaptor(adaptor, parentsSeq)
      case connector: Connector =>
        processConnector(connector, parentsSeq)
      case _ => ()
  }

  private def processOnMessageClause(
    omc: OnMessageClause,
    processor: Processor[?],
    parents: Parents
  ): Unit = {
    val finder = Finder(omc.contents)
    val tells = finder.recursiveFindByType[TellStatement]
    val sends = finder.recursiveFindByType[SendStatement]

    tells.foreach { tell =>
      val maybeTarget =
        refMap.definitionOf[Processor[?]](tell.processorRef, omc)
      val maybeType = refMap.definitionOf[Type](tell.msg, omc)
      (maybeTarget, maybeType) match
        case (Some(target), Some(msgType)) =>
          collectedEdges.addOne(
            MessageFlowEdge(
              producer = processor,
              consumer = target,
              messageType = Some(msgType),
              mechanism = FlowMechanism.Tell
            )
          )
        case _ =>
          if maybeTarget.isEmpty then
            messages.addWarning(
              tell.loc,
              s"MessageFlowPass: could not resolve tell target" +
                s" '${tell.processorRef.format}' in ${processor.identify}"
            )
          if maybeType.isEmpty then
            messages.addWarning(
              tell.loc,
              s"MessageFlowPass: could not resolve message type" +
                s" '${tell.msg.format}' in ${processor.identify}"
            )
    }

    sends.foreach { send =>
      val maybePortlet =
        refMap.definitionOf[Portlet](send.portlet, omc)
      val maybeType = refMap.definitionOf[Type](send.msg, omc)
      (maybePortlet, maybeType) match
        case (Some(portlet), Some(msgType)) =>
          val portletParent = symTab.parentOf(portlet).collect {
            case p: Processor[?] => p
          }
          portletParent.foreach { target =>
            collectedEdges.addOne(
              MessageFlowEdge(
                producer = processor,
                consumer = target,
                messageType = Some(msgType),
                mechanism = FlowMechanism.Send
              )
            )
          }
        case _ =>
          if maybePortlet.isEmpty then
            messages.addWarning(
              send.loc,
              s"MessageFlowPass: could not resolve send portlet" +
                s" '${send.portlet.format}' in ${processor.identify}"
            )
          if maybeType.isEmpty then
            messages.addWarning(
              send.loc,
              s"MessageFlowPass: could not resolve message type" +
                s" '${send.msg.format}' in ${processor.identify}"
            )
    }
  }

  private def processAdaptor(
    adaptor: Adaptor,
    parents: Parents
  ): Unit = {
    val maybeReferentContext =
      refMap.definitionOf[Context](adaptor.referent, adaptor)
    val maybeSourceContext = parents.headOption.collect {
      case c: Context => c
    }
    (maybeSourceContext, maybeReferentContext) match
      case (Some(source), Some(referent)) =>
        // Determine producer/consumer based on adaptor direction
        val (producer, consumer) = adaptor.direction match
          case _: InboundAdaptor  => (referent, source)
          case _: OutboundAdaptor => (source, referent)

        // Always create a declaration-level edge (no message type)
        collectedEdges.addOne(
          MessageFlowEdge(
            producer = producer,
            consumer = consumer,
            messageType = None,
            mechanism = FlowMechanism.AdaptorBridge
          )
        )

        // Create additional typed edges from handler clauses
        adaptor.handlers.foreach { handler =>
          handler.clauses.foreach {
            case omc: OnMessageClause =>
              val maybeType =
                refMap.definitionOf[Type](omc.msg, omc)
              maybeType.foreach { msgType =>
                collectedEdges.addOne(
                  MessageFlowEdge(
                    producer = producer,
                    consumer = consumer,
                    messageType = Some(msgType),
                    mechanism = FlowMechanism.AdaptorBridge
                  )
                )
              }
            case _ => ()
          }
        }
      case _ =>
        messages.addWarning(
          adaptor.loc,
          s"MessageFlowPass: could not resolve adaptor context references" +
            s" for ${adaptor.identify}"
        )
  }

  private def processConnector(
    connector: Connector,
    parents: Parents
  ): Unit = {
    if connector.nonEmpty then
      // Find the parent context/processor that contains the connector
      val parentContainer = parents.headOption.collect {
        case b: Branch[?] => b
      }.getOrElse(Root.empty)

      val connParents = symTab.parentsOf(connector)
      val maybeOutlet =
        refMap.definitionOf[Outlet](connector.from, parentContainer)
      val maybeInlet =
        refMap.definitionOf[Inlet](connector.to, parentContainer)

      (maybeOutlet, maybeInlet) match
        case (Some(outlet), Some(inlet)) =>
          // Walk up from outlet/inlet to find their parent processor
          val fromProcessor = symTab.parentOf(outlet).collect {
            case s: Streamlet => s: Processor[?]
          }.orElse(
            symTab.parentOf(outlet).flatMap { p =>
              symTab.parentOf(p).collect {
                case proc: Processor[?] => proc
              }
            }
          )
          val toProcessor = symTab.parentOf(inlet).collect {
            case s: Streamlet => s: Processor[?]
          }.orElse(
            symTab.parentOf(inlet).flatMap { p =>
              symTab.parentOf(p).collect {
                case proc: Processor[?] => proc
              }
            }
          )

          // Resolve the outlet's type reference using
          // its parent as context
          val outletParent = symTab.parentOf(outlet).collect {
            case b: Branch[?] => b
          }.getOrElse(parentContainer)
          val outletType =
            refMap.definitionOf[Type](outlet.type_, outletParent)

          (fromProcessor, toProcessor, outletType) match
            case (Some(from), Some(to), Some(msgType)) =>
              collectedEdges.addOne(
                MessageFlowEdge(
                  producer = from,
                  consumer = to,
                  messageType = Some(msgType),
                  mechanism = FlowMechanism.ConnectorPipe
                )
              )
            case _ => ()
        case _ => ()
  }

  override def result(root: PassRoot): MessageFlowOutput = {
    val edges = collectedEdges.toSeq
    MessageFlowOutput(
      root = root,
      messages = messages.toMessages,
      edges = edges,
      producerIndex = edges.groupBy(_.producer),
      consumerIndex = edges.groupBy(_.consumer),
      messageIndex = edges.collect {
        case e if e.messageType.isDefined => e
      }.groupBy(_.messageType.get)
    )
  }
}
