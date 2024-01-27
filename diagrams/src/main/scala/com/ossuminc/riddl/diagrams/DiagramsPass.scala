/*
 * Copyright 2023 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.diagrams

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{AST, Messages}
import com.ossuminc.riddl.passes.*
import com.ossuminc.riddl.passes.resolve.ResolutionPass
import com.ossuminc.riddl.passes.symbols.SymbolsPass
import com.ossuminc.riddl.passes.validate.ValidationPass

import scala.collection.mutable

/** The information needed to generate a Data Flow Diagram. DFDs are generated for each
  * [[com.ossuminc.riddl.language.AST.Context]] and consist of the streaming components that that are connected.
  */
case class DataFlowDiagramData()

/** The information needed to generate a Use Case Diagram. The diagram for a use case is very similar to a Sequence
  * Diagram showing the interactions between involved components of the model.
  */
case class UseCaseDiagramData(
  name: String,
  actors: Map[String, Definition],
  interactions: Seq[Interaction]
)

type ContextRelationship = (Context, String)

/** The information needed to generate a Context Diagram showing the relationships between bounded contexts
  *
  * @param domain
  *   The domain or subdomain to which the context map pertains
  * @param aggregates
  *   The aggregate entities involved in the context relationships
  * @param relationships
  *   The relationships between contexts
  */
case class ContextDiagramData(
  domain: Domain,
  aggregates: Seq[Entity] = Seq.empty,
  relationships: Seq[ContextRelationship]
)

/** The information needed to generate a Context Diagram at the Domain level to show the relationships between its
  * constituent bounded contexts
  */
type DomainDiagramData = Seq[(Context, ContextDiagramData)]

/** The output of the DiagramsPass encompassing all the generated data for the various diagrams
  *
  * @param messages
  *   The messages generated by this pass
  * @param dataFlowDiagrams
  *   The data necessary for the various data flow diagrams per context
  * @param userCaseDiagrams
  *   The data necessary for the various use cases defined by
  * @param contextDiagrams
  *   The data necessary for the context diagrams
  * @param domainDiagrams
  *   The data necessary for the domain diagrams
  */
case class DiagramsPassOutput(
  messages: Messages.Messages = Messages.empty,
  dataFlowDiagrams: Map[Context, DataFlowDiagramData] = Map.empty,
  userCaseDiagrams: Map[UseCase, UseCaseDiagramData] = Map.empty,
  contextDiagrams: Map[Context, ContextDiagramData] = Map.empty,
  domainDiagrams: Map[Domain, DomainDiagramData] = Map.empty
) extends PassOutput

class DiagramsPass(input: PassInput, outputs: PassesOutput) extends Pass(input, outputs) {

  def name: String = DiagramsPass.name

  requires(SymbolsPass)
  requires(ResolutionPass)
  requires(ValidationPass)

  private val refMap = outputs.refMap
  private val symTab = outputs.symbols

  private val dataFlowDiagrams: mutable.HashMap[Context, DataFlowDiagramData] = mutable.HashMap.empty
  private val useCaseDiagrams: mutable.HashMap[UseCase, UseCaseDiagramData] = mutable.HashMap.empty
  private val contextDiagrams: mutable.HashMap[Context, ContextDiagramData] = mutable.HashMap.empty

  protected def process(definition: RiddlValue, parents: mutable.Stack[Definition]): Unit = {
    definition match
      case c: Context =>
        val aggregates = c.entities.filter(_.hasOption[EntityIsAggregate])
        val relationships = makeRelationships(c)
        val domain = parents.head.asInstanceOf[Domain]
        contextDiagrams.put(c, ContextDiagramData(domain, aggregates, relationships))
      case epic: Epic =>
        epic.cases.foreach { uc =>
          val data = captureUseCase(uc)
          useCaseDiagrams.put(uc, data)
        }

      case _ => ()
  }

  private def makeRelationships(context: Context): Seq[ContextRelationship] = {
    val allProcessors = findProcessors(context)
    for {
      processor <- allProcessors
      relationship <- makeProcessorRelationships(context, processor)
    } yield {
      relationship
    }
  }

  private def findProcessors(processor: Processor[?, ?]): Seq[Processor[?, ?]] = {
    val containedProcessors = processor match {
      case a: Adaptor     => a.contents.processors
      case a: Application => a.contents.processors
      case c: Context     => c.contents.processors
      case e: Entity      => e.contents.processors
      case p: Projector   => p.contents.processors
      case r: Repository  => r.contents.processors
      case s: Streamlet   => s.contents.processors
    }
    val includedProcessors = processor.includes.processors
    val nestedProcessors = (containedProcessors ++ includedProcessors).flatMap(findProcessors)

    includedProcessors ++ nestedProcessors :+ processor
  }

  private def makeProcessorRelationships(
    context: Context,
    processor: Processor[?, ?]
  ): Seq[ContextRelationship] = {
    val rel1 = makeTypeRelationships(context, processor.types, processor)
    val rel2 = makeFunctionRelationships(context, processor.functions)
    val rel3 = makeHandlerRelationships(context, processor.handlers)
    val rel4 = makeInletRelationships(context, processor.inlets)
    val rel5 = makeOutletRelationships(context, processor.outlets)
    val rel6 = processor match {
      case a: Adaptor     => inferRelationship(context, a)
      case _: Application => Seq.empty[ContextRelationship]
      case _: Context     => Seq.empty[ContextRelationship]
      case e: Entity      => makeHandlerRelationships(context, e.states.flatMap(_.handlers))
      case _: Projector   => Seq.empty[ContextRelationship]
      case _: Repository  => Seq.empty[ContextRelationship]
      case _: Streamlet   => Seq.empty[ContextRelationship]
    }
    rel1 ++ rel2 ++ rel3 ++ rel4 ++ rel5 ++ rel6
  }

  private def makeFunctionRelationships(context: Context, functions: Seq[Function]): Seq[ContextRelationship] = {
    for {
      f <- functions
      inputFields = f.input.map(_.fields).getOrElse(Seq.empty)
      outputFields = f.output.map(_.fields).getOrElse(Seq.empty)
      relationship <- makeFieldRelationships(context, inputFields ++ outputFields, f)
    } yield {
      relationship
    }
  }

  private def makeOutletRelationships(context: Context, outlets: Seq[Outlet]): Seq[ContextRelationship] = {
    for {
      o <- outlets
      r = o.type_
      t <- this.refMap.definitionOf[Type](r, o)
      relationship <- inferRelationship(context, t)
    } yield {
      relationship
    }
  }

  private def makeInletRelationships(context: Context, inlets: Seq[Inlet]): Seq[ContextRelationship] = {
    for {
      i <- inlets
      r = i.type_
      t <- this.refMap.definitionOf[Type](r, i)
      relationship <- inferRelationship(context, t)
    } yield {
      relationship
    }
  }

  private def makeHandlerRelationships(context: Context, handlers: Seq[Handler]): Seq[ContextRelationship] = {
    for {
      h <- handlers
      oc: OnClause <- h.clauses
      omc: OnMessageClause = oc.asInstanceOf[OnMessageClause] if oc.isInstanceOf[OnMessageClause]
      relationship <- makeStatementRelationships(context, omc, omc.statements)
    } yield {
      relationship
    }
  }

  private def makeStatementRelationships(
    context: Context,
    parent: OnMessageClause,
    statements: Seq[Statement]
  ): Seq[ContextRelationship] = {
    for {
      statement <- statements
      ref <- getStatementReferences(statement)
      definition <- this.refMap.definitionOf[Definition](ref, parent)
      relationship <- inferRelationship(context, definition)
    } yield {
      relationship
    }
  }

  private def getStatementReferences(statement: Statement): Seq[Reference[Definition]] = {
    statement match
      case SendStatement(_, msg, portlet)   => Seq(msg, portlet)
      case TellStatement(_, msg, processor) => Seq(msg, processor)
      case SetStatement(_, field, _)        => Seq(field)
      case ReplyStatement(_, message)       => Seq(message)
      case CallStatement(_, function)       => Seq(function)
      case _                                => Seq.empty
  }

  private def getTypeReferences(typEx: TypeExpression): Seq[Reference[Definition]] = {
    typEx match {
      case EntityReferenceTypeExpression(loc, pid)     => Seq(EntityRef(loc, pid))
      case AliasedTypeExpression(loc, keyword, pathId) => Seq(TypeRef(loc, keyword, pathId))
      case aucte: AggregateUseCaseTypeExpression =>
        aucte.fields.foldLeft(Seq.empty) { case (s, f) => s ++ getTypeReferences(f.typeEx) }
      case ate: AggregateTypeExpression =>
        ate.fields.foldLeft(Seq.empty) { case (s, f) => s ++ getTypeReferences(f.typeEx) }
      case _: TypeExpression => Seq.empty
    }
  }

  private def makeTypeRelationships(
    context: Context,
    types: Seq[Type],
    parent: Definition
  ): Seq[ContextRelationship] = {
    for {
      typ <- types
      typEx = typ.typ
      ref: Reference[Definition] <- getTypeReferences(typEx)
      definition <- refMap.definitionOf[Definition](ref, parent)
      relationship <- inferRelationship(context, definition)
    } yield {
      relationship
    }
  }

  private def makeFieldRelationships(
    context: Context,
    fields: Seq[Field],
    parent: Definition
  ): Seq[ContextRelationship] = {
    for {
      f <- fields
      ref: Reference[Definition] <- getTypeReferences(f.typeEx)
      definition: Definition <- this.refMap.definitionOf[Definition](ref, parent)
      relationship <- inferRelationship(context, definition)
    } yield {
      relationship
    }
  }

  private def inferRelationship(context: Context, definition: Definition): Option[ContextRelationship] = {
    this.symTab.contextOf(definition) match {
      case Some(foreignContext) =>
        if foreignContext != context then
          definition match {
            case a: Adaptor =>
              refMap.definitionOf[Context](a.context, a) match {
                case Some(foreignContext) =>
                  if foreignContext != context then Some(foreignContext -> s"adaptation ${a.direction.format}")
                  else None
                case None => None
              }
            case m: Type            => Some(foreignContext -> s"Uses ${m.identify} from")
            case e: Entity          => Some(foreignContext -> s"References ${e.identify} in")
            case f: Field           => Some(foreignContext -> s"Sets ${f.identify} in")
            case i: Inlet           => Some(foreignContext -> s"Sends to ${i.identify} in")
            case o: Outlet          => Some(foreignContext -> s"Takes from ${o.identify} in")
            case p: Processor[?, ?] => Some(foreignContext -> s"Tells to ${p.identify} in")
            case _                  => None
          }
        else None
        end if
      case None => None
    }
  }

  private def actorsFirst(a: (String, Definition), b: (String, Definition)): Boolean = {
    a._2 match
      case _: User if b._2.isInstanceOf[User]       => a._1 < b._1
      case _: User                                  => true
      case _: Definition if b._2.isInstanceOf[User] => false
      case _: Definition                            => a._1 < b._1
  }

  private def captureUseCase(uc: UseCase): UseCaseDiagramData = {
    val actors: Map[String, Definition] = {
      uc.contents
        .map {
          case tri: TwoReferenceInteraction =>
            val fromDef = refMap.definitionOf[Definition](tri.from.pathId, uc)
            val toDef = refMap.definitionOf[Definition](tri.to.pathId, uc)
            Seq(
              tri.from.pathId.format -> fromDef,
              tri.to.pathId.format -> toDef
            )
          case _: InteractionContainer | _: Interaction | _: Comment => Seq.empty
        }
        .filterNot(_.isEmpty) // ignore None values generated when ref not found
        .flatten // get rid of seq of seq
        .filterNot(_._1.isEmpty) // drop empty things
        .map(x => x._1 -> x._2.getOrElse(Root.empty)) // get rid of no definition case
        .distinctBy(_._1) // eliminate duplicates
        .sortWith(actorsFirst) // always list actors first (left side of diagram)
        .toMap
    }
    val title = uc.identify + " in " + symTab.parentOf(uc).map(_.identify).getOrElse(" an Epic")
    UseCaseDiagramData(title, actors, uc.contents.filter[Interaction])
  }

  def postProcess(root: Root): Unit = {}

  def result: DiagramsPassOutput = {
    val domains = contextDiagrams.values.toSeq.map(_.domain).distinct
    val domainDiagrams: Map[Domain, DomainDiagramData] = {
      for {
        d <- domains
        l = contextDiagrams.filter(_._2.domain == d).toSeq
      } yield {
        d -> l
      }
    }.toMap
    DiagramsPassOutput(
      messages.toMessages,
      dataFlowDiagrams.toMap,
      useCaseDiagrams.toMap,
      contextDiagrams.toMap,
      domainDiagrams
    )
  }
}

object DiagramsPass extends PassInfo {
  val name: String = "Diagrams"
  val creator: PassCreator = { (in: PassInput, out: PassesOutput) => DiagramsPass(in, out) }
}
