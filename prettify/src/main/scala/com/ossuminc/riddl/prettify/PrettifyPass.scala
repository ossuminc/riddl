/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.prettify

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages.{Messages, nl}
import com.ossuminc.riddl.language.parsing.{Keyword, Readability}
import com.ossuminc.riddl.language.{AST, Messages}
import com.ossuminc.riddl.passes.{HierarchyPass, PassInfo, PassInput, PassOutput, PassesOutput}
import com.ossuminc.riddl.passes.resolve.ResolutionPass
import com.ossuminc.riddl.passes.symbols.SymbolsPass
import com.ossuminc.riddl.passes.validate.ValidationPass

import java.nio.file.Path
import scala.annotation.unused

object PrettifyPass extends PassInfo {
  val name: String = "prettify"

  /** A function to translate between a definition and the keyword that introduces them.
    *
    * @param definition
    *   The definition to look up
    * @return
    *   A string providing the definition keyword, if any. Enumerators and fields don't have their own keywords
    */
  def keyword(definition: Definition): String = {
    definition match {
      case _: Adaptor    => Keyword.adaptor
      case _: UseCase    => Keyword.case_
      case _: Context    => Keyword.context
      case _: Connector  => Keyword.connector
      case _: Domain     => Keyword.domain
      case _: Entity     => Keyword.entity
      case _: Enumerator => ""
      case _: Field      => ""
      case _: Function   => Keyword.function
      case _: Handler    => Keyword.handler
      case _: Inlet      => Keyword.inlet
      case _: Invariant  => Keyword.invariant
      case _: Outlet     => Keyword.outlet
      case s: Streamlet  => s.shape.keyword
      case _: Root       => "root"
      case _: Saga       => Keyword.saga
      case _: SagaStep   => Keyword.step
      case _: State      => Keyword.state
      case _: Epic       => Keyword.epic
      case _: Term       => Keyword.term
      case _: Type       => Keyword.type_
      case _             => "unknown"
    }
  }
}

case class PrettifyOutput(
  messages: Messages = Messages.empty,
  state: PrettifyState
) extends PassOutput

/** This is the RIDDL Prettifier to convert an AST back to RIDDL plain text */
case class PrettifyPass(input: PassInput, outputs: PassesOutput, state: PrettifyState)
    extends HierarchyPass(input, outputs) {

  requires(SymbolsPass)
  requires(ResolutionPass)
  requires(ValidationPass)

  def name: String = PrettifyPass.name

  def postProcess(root: AST.Root): Unit = ()

  /** Generate the output of this Pass. This will only be called after all the calls to process have completed.
    *
    * @return
    *   an instance of the output type
    */
  override def result: PassOutput = PrettifyOutput(Messages.empty, state)

  def openContainer(container: Definition, parents: Seq[Definition]): Unit = {
    container match {
      case epic: Epic         => openEpic(epic)
      case uc: UseCase        => openUseCase(uc)
      case domain: Domain     => openDomain(domain)
      case adaptor: Adaptor   => openAdaptor(adaptor)
      case typ: Type          => state.current.emitType(typ)
      case function: Function => openFunction(function)
      case st: State          => openState(st)
      case step: SagaStep     => openSagaStep(step)
      case streamlet: Streamlet => openStreamlet(streamlet)
      case processor: Processor[_, _] =>
        state.withCurrent(_.openDef(container).emitOptions(processor).emitStreamlets(processor))
      case handler: Handler =>
        state.withCurrent(_.openDef(handler))
      case saga: Saga =>
        state.withCurrent(_.openDef(saga))
      case replica: Replica =>
        state.withCurrent(_.openDef(replica))
      case group: Group =>
        state.withCurrent(_.openDef(group))
      case output: Output =>
        state.withCurrent(_.openDef(output))
      case input: Input =>
        state.withCurrent(_.openDef(input))
      case cg: ContainedGroup =>
        state.withCurrent(_.openDef(cg))
      case _: Root       => () // ignore
      case _: Enumerator => () // not a container
      case _: Field | _: Method | _: Term | _: Author | _: Constant | _: Invariant | _: OnOtherClause |
          _: OnInitClause | _: OnMessageClause | _: OnTerminationClause | _: Inlet | _: Outlet | _: Connector |
          _: User | _: GenericInteraction | _: SelfInteraction | _: VagueInteraction =>
        () // not  containers

    }
  }

  def processLeaf(
    definition: LeafDefinition,
    parents: Seq[Definition]
  ): Unit = {
    definition match {
      case onClause: OnClause => processOnClause(onClause)
      case invariant: Invariant =>
        state.withCurrent(
          _.openDef(invariant).closeDef(invariant, withBrace = false)
        )
      case conn: Connector => doConnector(conn)
      case user: User      => doUser(user)
      case _: Field        =>
      case _               => ()
      // inlets and outlets handled by openProcessor
      /* require(
        !definition.isInstanceOf[Definition],
        s"doDefinition should not be called for ${definition.getClass.getName}"
      )*/
    }
  }

  def closeContainer(
    container: Definition,
    parents: Seq[Definition]
  ): Unit = {
    container match {
      case _: Type            => () // openContainer did all of it
      case epic: Epic         => closeEpic(epic)
      case uc: UseCase        => closeUseCase(uc)
      case st: State          => state.withCurrent(_.closeDef(st))
      case _: OnMessageClause => closeOnClause()
      case _: Root                  => () // ignore
      case container: Definition =>
        // Applies To: Domain, Context, Entity, Adaptor, Interactions, Saga,
        // Plant, Streamlet, Function, SagaStep
        state.withCurrent(_.closeDef(container))
    }
  }

  private def openDomain(
    domain: Domain
  ): Unit = {
    val s0: PrettifyState = state.withCurrent(_.openDef(domain))
    domain.authors.foldLeft[PrettifyState](s0) { (st: PrettifyState, author) =>
      val s1: PrettifyState = st.withCurrent(
        _.addIndent(s"author is {\n").indent
          .addIndent(s"name = ${author.name.format}\n")
          .addIndent(s"email = ${author.email.format}\n")
      )
      val s2: PrettifyState = author.organization
        .map[PrettifyState] { org =>
          s1.withCurrent(_.addIndent(s"organization =${org.format}\n"))
        }
        .getOrElse(s1)
      val s3: PrettifyState = author.title
        .map(title => s2.withCurrent(_.addIndent(s"title = ${title.format}\n")))
        .getOrElse(s2)
      s3.withCurrent(_.outdent.addIndent("}\n"))
    }
  }

  private def openEpic(epic: Epic): Unit = {
    state.withCurrent { st =>
      if epic.userStory.isEmpty then {
        st.openDef(epic, withBrace = false).add(" ??? ")
      } else {
        val us = epic.userStory.getOrElse(UserStory())
        val user = us.user.pathId
        st.openDef(epic)
          .addIndent("user ")
          .add(user.format)
          .add(" wants to ")
          .add(s"\"${us.capability.s}\" so that \"${us.benefit.s}\"")
          .nl
      }
    }
  }

  private def closeEpic(
    story: Epic
  ): Unit = {
    state.withCurrent(_.closeDef(story))
  }

  private def openUseCase(useCase: UseCase): Unit = {
    if useCase.isEmpty then
      state.withCurrent(
        _.add(PrettifyPass.keyword(useCase))
          .add(" ")
          .add(useCase.id.value)
          .add(" { ??? }")
          .add(nl)
      )
    else 
      useCase.contents.foreach {  
        case si: SequentialInteractions => () // FIXME
        case pi: ParallelInteractions => () // FIXME
        case oi: OptionalInteractions => () // FIXME
        case twori: TwoReferenceInteraction => () // FIXME
        case gi: GenericInteraction => () // FIXME
        case _: Comment => ()
      }
    end if
  }

  private def doInteraction(@unused interaction: Interaction): Unit = {
    // TODO: write doInteraction
  }

  private def closeUseCase(@unused useCase: UseCase): Unit = {
    // TODO: write closeUseCase
  }

  private def openAdaptor(
    adaptor: Adaptor
  ): Unit = {
    state.withCurrent(
      _.addIndent(PrettifyPass.keyword(adaptor))
        .add(" ")
        .add(adaptor.id.format)
        .add(" ")
        .add(adaptor.direction.format)
        .add(" ")
        .add(adaptor.context.format)
        .add(" is {")
    )
    if adaptor.isEmpty then {
      state.withCurrent(_.emitUndefined().add(" }\n"))
    } else
      state.withCurrent { rfe =>
        rfe.add("\n").indent.emitStreamlets(adaptor)
      }

  }

  private def openStreamlet(
    streamlet: Streamlet
  ): Unit = {
    state.withCurrent { file =>
      file.openDef(streamlet).emitStreamlets(streamlet)
    }
  }

  private def processOnClause(
    onClause: OnClause
  ): Unit = {
    onClause match {
      case omc: OnMessageClause =>
        state.withCurrent(
          _.addIndent("on ").emitMessageRef(omc.msg).emitCodeBlock(omc.statements)
        )
      case oic: OnInitClause =>
        state.withCurrent(
          _.addIndent("on init ").emitCodeBlock(oic.statements)
        )
      case otc: OnTerminationClause =>
        state.withCurrent(
          _.addIndent("on term ").emitCodeBlock(otc.statements)
        )
      case ooc: OnOtherClause =>
        state.withCurrent(
          _.addIndent("on other ").emitCodeBlock(ooc.statements)
        )
    }
  }

  private def closeOnClause(): Unit = {
    state.withCurrent(_.outdent.addIndent("}\n"))
  }

  private def doUser(user: User): Unit = {
    state.withCurrent(
      _.add(s"user ${user.id.value} is \"${user.is_a.s}\"")
        .emitBrief(user.brief)
        .emitDescription(user.description)
        .nl
    )
  }

  private def doConnector(
    conn: Connector
  ): Unit = {
    state.withCurrent { file =>
      file
        .openDef(conn)
      if conn.nonEmpty then
        file
          .addSpace()
          .add {
            val flows =
              if conn.flows.nonEmpty then s"flows ${conn.flows.get.format} "
              else ""
            val from =
              if conn.from.nonEmpty then s"from ${conn.from.get.format} "
              else ""
            val to =
              if conn.to.nonEmpty then s"to ${conn.to.get.format}"
              else ""
            flows + from + to
          }
          .nl
          .addSpace()
          .closeDef(conn)
      end if
    }
  }

  private def openFunction[TCD <: Definition](
    function: Function
  ): Unit = {
    state.withCurrent(_.openDef(function))
    function.input.foreach(te => state.withCurrent(_.addIndent("requires ").emitTypeExpression(te).nl))
    function.output.foreach(te => state.withCurrent(_.addIndent("returns  ").emitTypeExpression(te).nl))
    state.withCurrent(_.addIndent("body ").emitCodeBlock(function.statements))
  }

  private def openState(
    riddl_state: State
  ): Unit = {
    state.withCurrent { st =>
      st.addSpace()
        .add(
          s"${PrettifyPass.keyword(riddl_state)} ${riddl_state.id.format} of ${riddl_state.typ.format} is {"
        )
      if riddl_state.isEmpty then {
        st.add(" ??? }")
      } else {
        st.nl.indent
      }
    }
  }

  private def openSagaStep(
    step: SagaStep
  ): Unit = {
    state.withCurrent(
      _.openDef(step)
        .emitCodeBlock(step.doStatements)
        .add("reverted by")
        .emitCodeBlock(step.undoStatements)
    )
  }

  private def openInclude[T <: Definition](
    @unused include: Include[T]
  ): Unit = {
    if !state.options.singleFile then {
      include.source match {
        case Some(path: String) if path.startsWith("http") =>
          val url = java.net.URI.create(path).toURL
          state.current.add(s"include \"$path\"")
          val outPath = state.outPathFor(url)
          state.pushFile(RiddlFileEmitter(outPath))
        case Some(str: String) =>
          val path = Path.of(str)
          val relativePath = state.relativeToInPath(path)
          state.current.add(s"include \"$relativePath\"")
          val outPath = state.outPathFor(path)
          state.pushFile(RiddlFileEmitter(outPath))
        case None =>
          state.current.add(s"include \"<missing file filePath>\"")
      }
    }
  }

  private def closeInclude[T <: Definition](
    @unused include: Include[T]
  ): Unit = {
    if !state.options.singleFile then { state.popFile() }
  }
}
