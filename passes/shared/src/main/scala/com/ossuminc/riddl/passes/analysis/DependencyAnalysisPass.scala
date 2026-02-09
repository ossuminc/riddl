/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.analysis

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.passes.*
import com.ossuminc.riddl.passes.resolve.{ResolutionOutput, ResolutionPass}
import com.ossuminc.riddl.passes.symbols.{SymbolsOutput, SymbolsPass}
import com.ossuminc.riddl.passes.validate.ValidationPass
import com.ossuminc.riddl.utils.PlatformContext

import scala.collection.mutable
import scala.scalajs.js.annotation.*

/** Describes a bridge between two contexts via an adaptor
  *
  * @param adaptor
  *   The adaptor creating the bridge
  * @param sourceContext
  *   The context containing the adaptor
  * @param targetContext
  *   The context being adapted to/from
  * @param direction
  *   Inbound or outbound
  * @param bridgedTypes
  *   Message types that cross the boundary
  */
@JSExportTopLevel("AdaptorBridge")
case class AdaptorBridge(
  adaptor: Adaptor,
  sourceContext: Context,
  targetContext: Context,
  direction: AdaptorDirection,
  bridgedTypes: Seq[Type]
)

/** Output of the DependencyAnalysisPass
  *
  * @param root
  *   The root of the model
  * @param messages
  *   Any messages generated during analysis
  * @param contextDeps
  *   Map from each context to the set of contexts it depends on
  * @param entityDeps
  *   Map from each entity to definitions it references
  * @param typeDeps
  *   Map from each type to types it references
  * @param adaptorBridges
  *   All adaptor bridges discovered
  */
@JSExportTopLevel("DependencyOutput")
case class DependencyOutput(
  root: PassRoot = Root.empty,
  messages: Messages.Messages = Messages.empty,
  contextDeps: Map[Context, scala.collection.immutable.Set[Context]] =
    Map.empty,
  entityDeps: Map[Entity, scala.collection.immutable.Set[Definition]] =
    Map.empty,
  typeDeps: Map[Type, scala.collection.immutable.Set[Type]] = Map.empty,
  adaptorBridges: Seq[AdaptorBridge] = Seq.empty
) extends PassOutput

@JSExportTopLevel("DependencyAnalysisPass$")
object DependencyAnalysisPass extends PassInfo[PassOptions] {
  val name: String = "DependencyAnalysis"
  def creator(
    options: PassOptions = PassOptions.empty
  )(using PlatformContext): PassCreator = {
    (in: PassInput, out: PassesOutput) =>
      DependencyAnalysisPass(in, out)
  }
}

/** A pass that builds cross-context and cross-entity dependency
  * graphs showing which definitions reference which others. It
  * analyzes all resolved references to determine source/target
  * contexts and builds adjacency sets.
  */
@JSExportTopLevel("DependencyAnalysisPass")
case class DependencyAnalysisPass(
  input: PassInput,
  outputs: PassesOutput
)(using PlatformContext)
    extends Pass(input, outputs) {

  requires(SymbolsPass)
  requires(ResolutionPass)
  requires(ValidationPass)

  override def name: String = DependencyAnalysisPass.name

  private lazy val refMap = outputs.refMap
  private lazy val symTab = outputs.symbols

  private val contextDeps: mutable.HashMap[Context, mutable.Set[Context]] =
    mutable.HashMap.empty
  private val entityDeps: mutable.HashMap[Entity, mutable.Set[Definition]] =
    mutable.HashMap.empty
  private val typeDepsMap: mutable.HashMap[Type, mutable.Set[Type]] =
    mutable.HashMap.empty
  private val bridges: mutable.ListBuffer[AdaptorBridge] =
    mutable.ListBuffer.empty

  protected def process(
    definition: RiddlValue,
    parents: ParentStack
  ): Unit = {
    definition match
      case adaptor: Adaptor =>
        processAdaptor(adaptor, parents.toParents)
      case tell: TellStatement =>
        processTellStatement(tell, parents.toParents)
      case send: SendStatement =>
        processSendStatement(send, parents.toParents)
      case _ => ()
  }

  private def processAdaptor(
    adaptor: Adaptor,
    parents: Parents
  ): Unit = {
    val maybeSourceContext = parents.collectFirst {
      case c: Context => c
    }
    val maybeTargetContext =
      refMap.definitionOf[Context](adaptor.referent, adaptor)

    (maybeSourceContext, maybeTargetContext) match
      case (Some(source), Some(target)) =>
        // Add context dependency
        contextDeps.getOrElseUpdate(
          source, mutable.Set.empty
        ) += target

        // Collect bridged types
        val bridgedTypes = adaptor.handlers.flatMap { handler =>
          handler.clauses.flatMap {
            case omc: OnMessageClause =>
              refMap.definitionOf[Type](omc.msg, omc).toSeq
            case _ => Seq.empty
          }
        }

        bridges.addOne(
          AdaptorBridge(
            adaptor = adaptor,
            sourceContext = source,
            targetContext = target,
            direction = adaptor.direction,
            bridgedTypes = bridgedTypes
          )
        )
      case _ => ()
  }

  private def processTellStatement(
    tell: TellStatement,
    parents: Parents
  ): Unit = {
    // Find the context containing this tell statement
    val sourceContext = parents.collectFirst {
      case c: Context => c
    }
    val sourceEntity = parents.collectFirst {
      case e: Entity => e
    }

    // Find the OnMessageClause or handler containing this
    val parentClause = parents.collectFirst {
      case omc: OnMessageClause => omc
    }

    parentClause.foreach { omc =>
      val maybeTarget =
        refMap.definitionOf[Processor[?]](tell.processorRef, omc)
      maybeTarget.foreach { target =>
        val targetContext = symTab.contextOf(target)

        // Record context dependency if cross-context
        (sourceContext, targetContext) match
          case (Some(src), Some(tgt)) if src != tgt =>
            contextDeps.getOrElseUpdate(
              src, mutable.Set.empty
            ) += tgt
          case _ => ()

        // Record entity dependency
        sourceEntity.foreach { entity =>
          entityDeps.getOrElseUpdate(
            entity, mutable.Set.empty
          ) += target
        }

        // Record type dependency for the message
        val maybeType = refMap.definitionOf[Type](tell.msg, omc)
        maybeType.foreach { msgType =>
          val sourceType = parents.collectFirst {
            case t: Type => t
          }
          sourceType.foreach { src =>
            typeDepsMap.getOrElseUpdate(
              src, mutable.Set.empty
            ) += msgType
          }
        }
      }
    }
  }

  private def processSendStatement(
    send: SendStatement,
    parents: Parents
  ): Unit = {
    val sourceContext = parents.collectFirst {
      case c: Context => c
    }
    val sourceEntity = parents.collectFirst {
      case e: Entity => e
    }
    val parentClause = parents.collectFirst {
      case omc: OnMessageClause => omc
    }

    parentClause.foreach { omc =>
      val maybePortlet =
        refMap.definitionOf[Portlet](send.portlet, omc)
      maybePortlet.foreach { portlet =>
        val targetContext = symTab.contextOf(portlet)

        (sourceContext, targetContext) match
          case (Some(src), Some(tgt)) if src != tgt =>
            contextDeps.getOrElseUpdate(
              src, mutable.Set.empty
            ) += tgt
          case _ => ()

        sourceEntity.foreach { entity =>
          entityDeps.getOrElseUpdate(
            entity, mutable.Set.empty
          ) += portlet
        }
      }
    }
  }

  override def result(root: PassRoot): DependencyOutput = {
    DependencyOutput(
      root = root,
      messages = messages.toMessages,
      contextDeps = contextDeps.map { case (k, v) =>
        k -> v.toSet
      }.toMap,
      entityDeps = entityDeps.map { case (k, v) =>
        k -> v.toSet
      }.toMap,
      typeDeps = typeDepsMap.map { case (k, v) =>
        k -> v.toSet
      }.toMap,
      adaptorBridges = bridges.toSeq
    )
  }
}
