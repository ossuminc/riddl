/*
 * Copyright 2019-2026 Ossum Inc.
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
  *   Map from each type to the types it references.
  *
  * **[4.2], RULED 2026-08-17 by Reid.** This is a TYPE-DEPENDENCY graph, so that a consumer can
  * find loops and walk the type hierarchy. Reid's example is the contract: a record referencing a
  * set whose value references a named integer type yields `record -> set` AND
  * `set -> namedInteger`; the edges are DIRECT and the chain is walked by the consumer.
  *
  * It ALSO carries one message-flow edge per `tell`: the handled message depends on the message
  * told. **Know the consequence before using this for cycle detection** — two processors that tell
  * each other's messages produce a cycle here that is a legitimate protocol, not a structural type
  * loop. If a consumer needs purely structural edges, the fix is to split this into two maps rather
  * than to filter, and that is a change worth asking for rather than guessing at.
  *
  * Empty for every model until 2026-08-17, because its only writer sat behind a guard that could
  * not succeed.
  * @param adaptorBridges
  *   All adaptor bridges discovered
  */
@JSExportTopLevel("DependencyOutput")
case class DependencyOutput(
  root: PassRoot = Root.empty,
  messages: Messages.Messages = Messages.empty,
  contextDeps: Map[Context, scala.collection.immutable.Set[Context]] = Map.empty,
  entityDeps: Map[Entity, scala.collection.immutable.Set[Definition]] = Map.empty,
  typeDeps: Map[Type, scala.collection.immutable.Set[Type]] = Map.empty,
  adaptorBridges: Seq[AdaptorBridge] = Seq.empty
) extends PassOutput

@JSExportTopLevel("DependencyAnalysisPass$")
object DependencyAnalysisPass extends PassInfo[PassOptions] {
  val name: String = "DependencyAnalysis"
  def creator(
    options: PassOptions = PassOptions.empty
  )(using PlatformContext): PassCreator = { (in: PassInput, out: PassesOutput) =>
    DependencyAnalysisPass(in, out)
  }
}

/** A pass that builds cross-context and cross-entity dependency graphs showing which definitions
  * reference which others. It analyzes all resolved references to determine source/target contexts
  * and builds adjacency sets.
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
  private lazy val usages = outputs.usage

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
      // [4.2], RULED 2026-08-17 by Reid: `typeDeps` is a TYPE-DEPENDENCY graph -- *"a map from each
      // type to types it references"*, as its own documentation always said -- so that a consumer
      // can find loops and walk the type hierarchy. Message flow is not the question it answers.
      case typ: Type =>
        processTypeDependencies(typ)
      case _ => ()
  }

  private def processAdaptor(
    adaptor: Adaptor,
    parents: Parents
  ): Unit = {
    val maybeSourceContext = parents.collectFirst { case c: Context =>
      c
    }
    val maybeTargetContext =
      refMap.definitionOf[Context](adaptor.referent, adaptor)

    (maybeSourceContext, maybeTargetContext) match
      case (Some(source), Some(target)) =>
        // Add context dependency
        contextDeps.getOrElseUpdate(
          source,
          mutable.Set.empty
        ) += target

        // Collect bridged types
        val bridgedTypes = adaptor.handlers.flatMap { handler =>
          handler.clauses.flatMap {
            case omc: OnMessageLikeClause =>
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
    val sourceContext = parents.collectFirst { case c: Context =>
      c
    }
    val sourceEntity = parents.collectFirst { case e: Entity =>
      e
    }

    // Find the OnMessageClause or OnEventClause containing this
    val parentClause = parents.collectFirst { case omc: OnMessageLikeClause =>
      omc
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
              src,
              mutable.Set.empty
            ) += tgt
          case _ => ()

        // Record entity dependency
        sourceEntity.foreach { entity =>
          entityDeps.getOrElseUpdate(
            entity,
            mutable.Set.empty
          ) += target
        }

        // Record type dependency for the message.
        //
        // The source is the HANDLED message, resolved from the enclosing clause. It used to be
        // `parents.collectFirst { case t: Type => t }`, which CANNOT succeed: a tell's parents are
        // its on-clause, handler, processor, context and domain — never a Type. So `typeDeps`, a
        // public output documented as "map from each type to types it references", was empty for
        // every model ever analyzed, and an empty analysis result looks exactly like a model that
        // does not use the construct. There were no tests on this pass at all;
        // `DependencyAnalysisPassTest` is now that gate.
        //
        // "Handling PlaceOrder leads to telling ShipOrder" is the edge the field documents, and the
        // handled message is the only Type in a tell's surroundings, so it is the only candidate.
        val maybeType = DeliverableTypes.of(outputs, tell, tell.msg, omc)
        val sourceType = refMap.definitionOf[Type](omc.msg, omc)
        (sourceType, maybeType) match
          case (Some(src), Some(msgType)) =>
            typeDepsMap.getOrElseUpdate(src, mutable.Set.empty) += msgType
          case _ => () // one end unresolved: MessageFlowPass reports it, this pass stays quiet
      }
    }
  }

  private def processSendStatement(
    send: SendStatement,
    parents: Parents
  ): Unit = {
    val sourceContext = parents.collectFirst { case c: Context =>
      c
    }
    val sourceEntity = parents.collectFirst { case e: Entity =>
      e
    }
    val parentClause = parents.collectFirst { case omc: OnMessageLikeClause =>
      omc
    }

    parentClause.foreach { omc =>
      val maybePortlet =
        refMap.definitionOf[Portlet](send.portlet, omc)
      maybePortlet.foreach { portlet =>
        val targetContext = symTab.contextOf(portlet)

        (sourceContext, targetContext) match
          case (Some(src), Some(tgt)) if src != tgt =>
            contextDeps.getOrElseUpdate(
              src,
              mutable.Set.empty
            ) += tgt
          case _ => ()

        sourceEntity.foreach { entity =>
          entityDeps.getOrElseUpdate(
            entity,
            mutable.Set.empty
          ) += portlet
        }
      }
    }
  }

  /** [4.2]: every named [[Type]] that `typ` depends on, structurally.
    *
    * Reid's example is the contract: *"if a record references a set that has a value that
    * references a named integer type, then record -> set -> named-integer-type must be represented
    * in that map."* So the edges are DIRECT — record→set and set→namedInt — and a consumer walks
    * the chain, which is also what makes cycle detection possible.
    *
    * **Reuses the resolution ResolutionPass already did** rather than re-resolving path
    * identifiers here. That matters for correctness, not just effort: re-resolution would need to
    * reconstruct the right parent scope for every reference, and a second resolver that disagrees
    * with the first is worse than none.
    *
    * The subtlety is WHERE those usages are recorded. `resolveTypeExpression` associates an
    * aggregate's field types with the FIELD, not with the enclosing Type -- so for
    * `record R is { thing: MySet }` the recorded edge is `thing -> MySet`, and asking
    * `getUses(R)` alone answers NOTHING. Field uses are therefore folded up into their owning
    * type, recursively, which is what makes the record→set half of Reid's example appear at all.
    */
  // NB `scala.collection.immutable.Set`, spelled out: `AST.Set` is the RIDDL collection TYPE
  // and shadows the Scala one on the wildcard import. `case Set(_, of)` in `fieldsWithin` below
  // is the AST one, and is correct there -- which is exactly why this is easy to get wrong.
  private def typeDependenciesOf(typ: Type): scala.collection.immutable.Set[Type] =
    val holders: Seq[Definition] = typ +: fieldsWithin(typ.typEx)
    holders
      .flatMap(holder => usages.getUses(holder))
      .collect { case t: Type => t }
      // A type is not its own dependency. Self-reference is legal (a recursive record) and would
      // otherwise show up as a one-node cycle in every consumer looking for loops.
      .filterNot(_ eq typ)
      .toSet
  end typeDependenciesOf

  /** Every [[Field]] inside a type expression, at any depth.
    *
    * Recurses through the collection and cardinality wrappers as well as nested aggregates,
    * because a field's uses are recorded against the field wherever it sits -- `set of record {…}`
    * puts them two levels down.
    */
  private def fieldsWithin(te: TypeExpression): Seq[Field] =
    te match
      case agg: AggregateTypeExpression =>
        agg.fields.toSeq ++ agg.fields.toSeq.flatMap(f => fieldsWithin(f.typeEx))
      case Sequence(_, of)     => fieldsWithin(of)
      case Set(_, of)          => fieldsWithin(of)
      case Graph(_, of)        => fieldsWithin(of)
      case Replica(_, of)      => fieldsWithin(of)
      case Table(_, of, _)     => fieldsWithin(of)
      case Mapping(_, from, to) => fieldsWithin(from) ++ fieldsWithin(to)
      case c: Cardinality      => fieldsWithin(c.typeExp)
      case _                   => Seq.empty
  end fieldsWithin

  private def processTypeDependencies(typ: Type): Unit =
    val deps = typeDependenciesOf(typ)
    if deps.nonEmpty then typeDepsMap.getOrElseUpdate(typ, mutable.Set.empty) ++= deps
  end processTypeDependencies

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
