/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.ossuminc.riddl.prettify

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.parsing.Keyword
import com.ossuminc.riddl.passes.PassVisitor

import java.nio.file.Path
import scala.annotation.unused

class PrettifyVisitor(options: PrettifyPass.Options) extends PassVisitor:
  val state: PrettifyState = PrettifyState(options)

  def result: PrettifyState = state

  inline private def open(definition: Definition): Unit = state.withCurrent(_.openDef(definition))

  inline private def close(definition: Definition): Unit = state.withCurrent(_.closeDef(definition))

  def openType(typ: Type, parents: Parents): Unit = state.current.emitType(typ)
  def closeType(typ: Type, parents: Parents): Unit = () // handled by open

  def openDomain(domain: Domain, parents: Parents): Unit = open(domain)
  def closeDomain(domain: Domain, parents: Parents): Unit = close(domain)

  def openContext(context: Context, parents: Parents): Unit = open(context)
  def closeContext(context: Context, parents: Parents): Unit = close(context)

  def openEntity(entity: Entity, parents: Parents): Unit = open(entity)
  def closeEntity(entity: Entity, parents: Parents): Unit = close(entity)

  def openAdaptor(adaptor: Adaptor, parents: Parents): Unit =
    state.withCurrent { (rfe: RiddlFileEmitter) =>
      rfe
        .addIndent(keyword(adaptor))
        .add(" ")
        .add(adaptor.id.format)
        .add(" ")
        .add(adaptor.direction.format)
        .add(" ")
        .add(adaptor.context.format)
        .add(" is { ")
      if adaptor.isEmpty then rfe.emitUndefined().add(" }").nl
      else rfe.nl.incr
      end if
    }
  end openAdaptor

  def closeAdaptor(adaptor: Adaptor, parents: Parents): Unit = close(adaptor)

  def openEpic(epic: Epic, parents: Parents): Unit =
    state.withCurrent { rfe =>
      rfe
        .openDef(epic)
        .addLine(epic.userStory.format)
    }
  end openEpic
  def closeEpic(epic: Epic, parents: Parents): Unit = close(epic)

  def openUseCase(useCase: UseCase, parents: Parents): Unit =
    state.withCurrent { (rfe: RiddlFileEmitter) =>
      rfe.addIndent(s"${keyword(useCase)} ${useCase.id.format} is {").nl.incr
      rfe.addLine(useCase.userStory.format)
      if useCase.isEmpty then rfe.addLine("???")
    }
  end openUseCase

  def closeUseCase(useCase: UseCase, parents: Parents): Unit =
    state.withCurrent { (rfe: RiddlFileEmitter) =>
      rfe.decr.addLine("}")
    }
  end closeUseCase

  def openFunction(function: Function, parents: Parents): Unit =
    state.withCurrent { rfe =>
      rfe.addIndent(s"${keyword(function)} ${function.id.format} is { ").nl.incr
      function.input.foreach(te => rfe.addIndent("requires ").emitTypeExpression(te).nl)
      function.output.foreach(te => rfe.addIndent("returns  ").emitTypeExpression(te).nl)
    }
  end openFunction
  def closeFunction(function: Function, parents: Parents): Unit =
    state.withCurrent { rfe =>
      rfe.decr.addLine("}")
    }
  end closeFunction

  def openSaga(saga: Saga, parents: Parents): Unit = open(saga)
  def openStreamlet(streamlet: Streamlet, parents: Parents): Unit = open(streamlet)
  def openRepository(repository: Repository, parents: Parents): Unit = open(repository)
  def openProjector(projector: Projector, parents: Parents): Unit = open(projector)
  def openHandler(handler: Handler, parents: Parents): Unit = open(handler)
  def openOnClause(onClause: OnClause, parents: Parents): Unit = open(onClause)
  def openApplication(application: Application, parents: Parents): Unit = open(application)
  def openGroup(group: Group, parents: Parents): Unit = open(group)
  def openOutput(output: Output, parents: Parents): Unit =
    state.withCurrent { rfe =>
      rfe
        .addIndent(output.nounAlias)
        .add(" ")
        .add(output.identify)
        .add(" ")
        .add(output.verbAlias)
        .add(" ")
        .add(output.putOut.format)
      if output.isEmpty then rfe.nl else rfe.add(" {").nl.incr
    }
  end openOutput

  def openInput(input: Input, parents: Parents): Unit =
    state.withCurrent { rfe =>
      // form Identity takes record Whatever.Identity is { .. }
      rfe
        .addIndent(input.nounAlias)
        .add(" ")
        .add(input.id.format)
        .add(" ")
        .add(input.verbAlias)
        .add(" ")
        .add(input.takeIn.format)
      if input.isEmpty then rfe.nl else rfe.add(" {").nl.incr
    }
  end openInput

  // Close for each type of container definition

  def closeSaga(saga: Saga, parents: Parents): Unit = close(saga)
  def closeStreamlet(streamlet: Streamlet, parents: Parents): Unit = close(streamlet)
  def closeRepository(repository: Repository, parents: Parents): Unit = close(repository)
  def closeProjector(projector: Projector, parents: Parents): Unit = close(projector)
  def closeHandler(handler: Handler, parents: Parents): Unit = close(handler)
  def closeOnClause(onClause: OnClause, parents: Parents): Unit = close(onClause)
  def closeApplication(application: Application, parents: Parents): Unit = close(application)
  def closeGroup(group: Group, parents: Parents): Unit = close(group)
  def closeOutput(output: Output, parents: Parents): Unit =
    if output.nonEmpty then
      state.withCurrent { rfe =>
        rfe.decr.addLine("}")
      }
    end if
  end closeOutput

  def closeInput(input: Input, parents: Parents): Unit =
    if input.nonEmpty then
      state.withCurrent { rfe =>
        rfe.decr.addLine("}")
      }
    end if
  end closeInput

  // LeafDefinitions
  def doField(field: Field): Unit = ()
  def doMethod(method: Method): Unit = ()

  def doTerm(term: Term): Unit =
    state.withCurrent { rfe =>
      rfe
        .addIndent("term ")
        .add(term.id.format)
        .add(" is ")
        .emitBrief(term.brief)
        .emitDescription(term.description)
    }
  end doTerm

  def doAuthor(author: Author): Unit =
    state.withCurrent { rfe =>
      rfe
        .addLine(s"author ${author.id.format} is {")
        .incr
        .addIndent(s"name = ${author.name.format}\n")
        .addIndent(s"email = ${author.email.format}\n")
      author.organization.map(org => rfe.addIndent(s"organization =${org.format}\n"))
      author.title.map(title => rfe.addIndent(s"title = ${title.format}\n"))
      rfe.decr.addIndent("}").nl
    }
  end doAuthor

  def doConstant(constant: Constant): Unit =
    state.withCurrent { rfe =>
      rfe
        .addIndent("constant ")
        .add(constant.id.format)
        .add(" is ")
        .add(constant.value.format)
        .emitBrief(constant.brief)
        .emitDescription(constant.description)
        .nl
    }
  end doConstant

  def doInvariant(invariant: Invariant): Unit =
    state.withCurrent { rfe =>
      rfe
        .addIndent("invariant ")
        .add(invariant.id.format)
        .add(" is ")
        .add(invariant.condition.format)
        .add(" ")
        .emitBrief(invariant.brief)
        .emitDescription(invariant.description)
        .nl
    }
  end doInvariant

  def doSagaStep(sagaStep: SagaStep): Unit =
    state.withCurrent { rfe =>
      rfe
        .openDef(sagaStep)
        .emitCodeBlock(sagaStep.doStatements)
        .add("reverted by")
        .emitCodeBlock(sagaStep.undoStatements)
        .closeDef(sagaStep)
    }
  end doSagaStep

  def doInlet(inlet: Inlet): Unit =
    state.withCurrent { rfe => rfe.addLine(inlet.format) }
  end doInlet

  def doOutlet(outlet: Outlet): Unit =
    state.withCurrent { rfe => rfe.addLine(outlet.format) }
  end doOutlet

  def doConnector(connector: Connector): Unit =
    state.withCurrent { rfe =>
      rfe.addIndent(s"${keyword(connector)} ${connector.id.format} is ")
      rfe
        .add {
          val from = if connector.from.nonEmpty then s"from ${connector.from.format} " else "from empty "
          val to = if connector.to.nonEmpty then s"to ${connector.to.format}" else "to empty"
          from + to
        }
        .nl
        .emitBrief(connector.brief)
        .emitDescription(connector.description)
    }
  end doConnector

  def doUser(user: User): Unit =
    state.withCurrent { rfe =>
      rfe
        .addIndent(s"user ${user.id.value} is \"${user.is_a.s}\"")
        .emitBrief(user.brief)
        .emitDescription(user.description)
        .nl
    }
  end doUser

  def doSchema(schema: Schema): Unit =
    state.withCurrent { rfe =>
      rfe
        .addIndent("schema ")
        .add(schema.id.format)
        .add(" is ")
        .emitSchemaKind(schema.schemaKind)
        .nl
      schema.data.toSeq.sortBy(_._1.value).foreach { (id: Identifier, typeRef: TypeRef) =>
        rfe.addIndent("of ").add(id.format).add(" as ").add(typeRef.format).nl
      }
      schema.connectors.toSeq.sortBy(_._1.value).foreach { (id: Identifier, tr: (TypeRef, TypeRef)) =>
        rfe.addIndent("with ").add(id.format).add(" as ").add(tr._1.format).add(" to ").add(tr._2.format).nl
      }
      schema.indices.foreach { fieldRef =>
        rfe.addIndent("index on ").add(fieldRef.format).nl
      }
    }
  end doSchema

  def doState(riddl_state: State): Unit =
    state.withCurrent { rfe =>
      rfe.addLine(s"${keyword(riddl_state)} ${riddl_state.id.format} of ${riddl_state.typ.format}")
    }
  end doState

  def doEnumerator(enumerator: Enumerator): Unit = ()

  def doContainedGroup(containedGroup: ContainedGroup): Unit =
    state.withCurrent { rfe =>
      rfe
        .addIndent(s"${keyword(containedGroup)} ${containedGroup.id.format} as ")
        .add(containedGroup.group.format)
        .emitBrief(containedGroup.brief)
        .emitDescription(containedGroup.description)
    }
  end doContainedGroup

  // Non Definition values
  def doComment(comment: Comment): Unit =
    state.withCurrent(_.emitComment(comment))
  end doComment

  def doAuthorRef(authorRef: AuthorRef): Unit =
    state.withCurrent { rfe =>
      rfe.addIndent("by ").add(authorRef.format).nl
    }
  end doAuthorRef

  def doBriefDescription(brief: BriefDescription): Unit =
    state.withCurrent { rfe =>
      rfe.emitBrief(Some(brief))
    }
  end doBriefDescription

  def doDescription(description: Description): Unit =
    state.withCurrent { rfe =>
      rfe.emitDescription(Some(description))
    }
  end doDescription

  def doStatement(statement: Statements): Unit =
    state.withCurrent { rfe =>
      statement match
        case IfThenElseStatement(_, cond, thens, elses) =>
          rfe.addIndent(s"if ${cond.format} then").nl.incr
          thens.foreach(doStatement)
          rfe.decr.addLine("else").incr
          elses.foreach(doStatement)
          rfe.decr.addLine("end")
        case ForEachStatement(_, ref, statements) =>
          rfe.addIndent(s"foreach ${ref.format} do").incr
          statements.foreach(doStatement)
          rfe.decr.addLine("end")
        case SendStatement(_, msg, portlet) =>
          rfe.addLine(s"send ${msg.format} to ${portlet.format}")
        case TellStatement(_, msg, to) =>
          rfe.addLine(s"tell ${msg.format} to ${to.format}")
        case statement: Statement => rfe.addLine(statement.format)
        case comment: Comment     => rfe.addLine(comment.format)
      end match
    }
  end doStatement

  def doInteraction(interaction: Interaction): Unit =
    state.withCurrent { rfe =>
      interaction match
        case _: SequentialInteractions     => () // TODO: implement
        case _: ParallelInteractions       => () // TODO: implement
        case _: OptionalInteractions       => () // TODO: implement
        case _: TwoReferenceInteraction => () // TODO: implement
        case _: GenericInteraction         => () // TODO: implement
      end match
    }
  end doInteraction

  def doOptionValue(option: OptionValue): Unit =
    state.withCurrent { rfe =>
      rfe.emitOption(option)
    }
  end doOptionValue

  def openInclude(include: Include[?], parents: Parents): Unit =
    if !state.options.singleFile then
      include.origin.toExternalForm match
        case path: String if path.startsWith("http") =>
          val url = java.net.URI.create(path).toURL
          state.current.add(s"include \"$path\"")
          val outPath = state.outPathFor(url)
          state.pushFile(RiddlFileEmitter(outPath))
        case str: String =>
          val path = Path.of(str)
          val relativePath = state.relativeToInPath(path)
          state.current.add(s"include \"$relativePath\"")
          val outPath = state.outPathFor(path)
          state.pushFile(RiddlFileEmitter(outPath))
      end match
    end if
  end openInclude

  def closeInclude(@unused include: Include[?], parents: Parents): Unit =
    if !state.options.singleFile then state.popFile()
    end if
  end closeInclude
end PrettifyVisitor

/** A function to translate between a definition and the keyword that introduces them.
 *
 * @param definition
 *   The definition to look up
 * @return
 *   A string providing the definition keyword, if any. Enumerators and fields don't have their own keywords
 */
def keyword(definition: Definition): String =
  definition match
    case _: Adaptor     => Keyword.adaptor
    case _: Application => Keyword.application
    case _: UseCase     => Keyword.case_
    case _: Context     => Keyword.context
    case _: Connector   => Keyword.connector
    case _: Domain      => Keyword.domain
    case _: Entity      => Keyword.entity
    case _: Enumerator  => ""
    case _: Field       => ""
    case _: Function    => Keyword.function
    case group: Group   => group.alias
    case input: Input   => input.nounAlias
    case output: Output => output.nounAlias
    case _: Handler     => Keyword.handler
    case _: Inlet       => Keyword.inlet
    case _: Invariant   => Keyword.invariant
    case _: Outlet      => Keyword.outlet
    case s: Streamlet   => s.shape.keyword
    case _: Root        => "root"
    case _: Saga        => Keyword.saga
    case _: SagaStep    => Keyword.step
    case _: State       => Keyword.state
    case _: Epic        => Keyword.epic
    case _: Term        => Keyword.term
    case typ: Type =>
      typ.typEx match
        case AggregateUseCaseTypeExpression(_, useCase, _) =>
          useCase match
            case CommandCase => Keyword.command
            case EventCase   => Keyword.event
            case QueryCase   => Keyword.query
            case ResultCase  => Keyword.result
            case RecordCase  => Keyword.record
            case TypeCase    => Keyword.type_
          end match
        case _ => Keyword.type_
      end match
    case _: ContainedGroup => Keyword.contains
    case _: OnClause       => Keyword.on
    case _: Projector      => Keyword.projector
    case _: Repository     => Keyword.repository
    case _: Definition     => "unknown"
  end match
end keyword

