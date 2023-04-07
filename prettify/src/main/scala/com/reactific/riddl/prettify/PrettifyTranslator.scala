/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.prettify

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.Folding.Folder
import com.reactific.riddl.language.Messages.Messages
import com.reactific.riddl.language.CommonOptions
import com.reactific.riddl.language.Folding
import com.reactific.riddl.language.Translator
import com.reactific.riddl.language.parsing.Terminals.*
import com.reactific.riddl.language.passes.PassesResult
import com.reactific.riddl.utils.Logger

import java.nio.file.Path
import scala.annotation.unused

/** This is the RIDDL Prettifier to convert an AST back to RIDDL plain text */
object PrettifyTranslator extends Translator[PrettifyCommand.Options] {

  /** A function to translate between a definition and the keyword that
    * introduces them.
    *
    * @param definition
    *   The definition to look up
    * @return
    *   A string providing the definition keyword, if any. Enumerators and
    *   fields don't have their own keywords
    */
  def keyword(definition: Definition): String = {
    definition match {
      case _: Adaptor       => Keywords.adaptor
      case _: Context       => Keywords.context
      case _: Connector     => Keywords.connector
      case _: Domain        => Keywords.domain
      case _: Entity        => Keywords.entity
      case _: Enumerator    => ""
      case _: Example       => Keywords.example
      case _: Field         => ""
      case _: Function      => Keywords.function
      case _: Handler       => Keywords.handler
      case _: Inlet         => Keywords.inlet
      case _: Invariant     => Keywords.invariant
      case _: Outlet        => Keywords.outlet
      case s: Streamlet     => s.shape.keyword
      case _: RootContainer => "root"
      case _: Saga          => Keywords.saga
      case _: SagaStep      => Keywords.step
      case _: State         => Keywords.state
      case _: Epic          => Keywords.epic
      case _: Term          => Keywords.term
      case _: Type          => Keywords.`type`
      case _                => "unknown"
    }
  }

  def translate(
    results: PassesResult,
    @unused log: Logger,
    commonOptions: CommonOptions,
    options: PrettifyCommand.Options
  ): Either[Messages, PrettifyState] = {
    Right(doTranslation(results, log, commonOptions, options))
  }

  private def doTranslation(
    results: PassesResult,
    @unused log: Logger,
    commonOptions: CommonOptions,
    options: PrettifyCommand.Options
  ): PrettifyState = {
    val state = PrettifyState(commonOptions, options)
    val folder = new ReformatFolder
    Folding.foldAround(state, results.root, folder)
  }

  def translateToString(
    results: PassesResult,
    logger: Logger,
    commonOptions: CommonOptions,
    options: PrettifyCommand.Options
  ): String = {
    val state = doTranslation(
      results,
      logger,
      commonOptions,
      options.copy(singleFile = true)
    )
    state.filesAsString
  }

  private class ReformatFolder extends Folder[PrettifyState] {
    override def openContainer(
      state: PrettifyState,
      container: Definition,
      parents: Seq[Definition]
    ): PrettifyState = {
      container match {
        case epic: Epic => openEpic(state, epic)
        case uc: UseCase => openUseCase(state, uc)
        case domain: Domain      => openDomain(state, domain)
        case adaptor: Adaptor    => openAdaptor(state, adaptor)
        case typ: Type           => state.current.emitType(typ); state
        case function: Function  => openFunction(state, function)
        case st: State           => openState(state, st)
        case oc: OnMessageClause => openOnClause(state, oc)
        case step: SagaStep      => openSagaStep(state, step)
        case include: Include[Definition] @unchecked =>
          openInclude(state, include)
        case streamlet: Streamlet => openStreamlet(state, streamlet)
        case _: RootContainer     =>
          // ignore
          state
        case container: Definition with WithOptions[?] =>
          // Applies To: Context, Entity, Interactions
          state.withCurrent(_.openDef(container).emitOptions(container))
        case container: Definition =>
          // Applies To: Saga, Plant, Handler, Streamlet
          state.withCurrent(_.openDef(container))
      }
    }

    override def doDefinition(
      state: PrettifyState,
      definition: Definition,
      parents: Seq[Definition]
    ): PrettifyState = {
      definition match {
        case example: Example => state.withCurrent(_.emitExample(example))
        case invariant: Invariant =>
          state.withCurrent(
            _.openDef(invariant).closeDef(invariant, withBrace = false)
          )
        case conn: Connector => doConnector(state, conn)
        case actor: Actor    => doActor(state, actor)
        case i: Interaction => doInteraction(state, i)
        case _: Field => state // was handled by Type case in openContainer
        case _        =>
          // inlets and outlets handled by openProcessor
          /* require(
            !definition.isInstanceOf[Definition],
            s"doDefinition should not be called for ${definition.getClass.getName}"
          )*/
          state
      }
    }

    override def closeContainer(
      state: PrettifyState,
      container: Definition,
      parents: Seq[Definition]
    ): PrettifyState = {
      container match {
        case _: Type            => state // openContainer did all of it
        case epic: Epic        => closeEpic(state, epic)
        case uc: UseCase      => closeUseCase(state, uc)
        case st: State          => state.withCurrent(_.closeDef(st))
        case _: OnMessageClause => closeOnClause(state)
        case include: Include[Definition] @unchecked =>
          closeInclude(state, include)
        case _: RootContainer =>
          // ignore
          state
        case container: Definition =>
          // Applies To: Domain, Context, Entity, Adaptor, Interactions, Saga,
          // Plant, Processor, Function, SagaStep
          state.withCurrent(_.closeDef(container))
      }
    }

    private def openDomain(
      state: PrettifyState,
      domain: Domain
    ): PrettifyState = {
      val s0: PrettifyState = state.withCurrent(_.openDef(domain))
      domain.authorDefs.foldLeft[PrettifyState](s0) {
        (st: PrettifyState, author) =>
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
            .map(title =>
              s2.withCurrent(_.addIndent(s"title = ${title.format}\n"))
            )
            .getOrElse(s2)
          s3.withCurrent(_.outdent.addIndent("}\n"))
      }
    }

    private def openEpic(state: PrettifyState, story: Epic): PrettifyState = {
      state.withCurrent { st =>
        if (story.userStory.isEmpty) {
          st.openDef(story, withBrace = false).add(" ??? ")
        } else {
          val us = story.userStory.get
          val actor = us.actor.pathId
          st.openDef(story)
            .addIndent("actor")
            .add(actor.format)
            .add(" ")
            .add(Readability.wants)
            .add(" ")
            .add(Readability.to)
            .add(s"\"${us.capability.s}\" so that \"${us.benefit.s}\"")
            .nl
        }
      }
    }

    private def closeEpic(
      state: PrettifyState,
      story: Epic
    ): PrettifyState = {
      state.withCurrent(_.closeDef(story))
    }

    private def openUseCase(state: PrettifyState, @unused useCase: UseCase ): PrettifyState = {
      // TODO: write openUseCase
      state
    }
    private def closeUseCase(state: PrettifyState, @unused useCase: UseCase ): PrettifyState = {
      state
    }

    private def openAdaptor(
      state: PrettifyState,
      adaptor: Adaptor
    ): PrettifyState = {
      state
        .withCurrent(
          _.addIndent(keyword(adaptor))
            .add(" ")
            .add(adaptor.id.format)
            .add(" ")
            .add(adaptor.direction.format)
            .add(" ")
            .add(adaptor.context.format)
            .add(" is {")
        )
        .step { s2 =>
          if (adaptor.isEmpty) { s2.withCurrent(_.emitUndefined().add(" }\n")) }
          else s2.withCurrent(_.add("\n").indent)
        }
    }

    private def openStreamlet(
      state: PrettifyState,
      streamlet: Streamlet
    ): PrettifyState = {
      state.withCurrent { file =>
        file.openDef(streamlet)
        streamlet.inlets.foreach(doInlet(state, _))
        streamlet.outlets.foreach(doOutlet(state, _))
      }
    }

    private def openOnClause(
      state: PrettifyState,
      onClause: OnMessageClause
    ): PrettifyState = {
      state.withCurrent(
        _.addIndent("on ").emitMessageRef(onClause.msg).add(" {\n").indent
      )
    }

    private def closeOnClause(state: PrettifyState): PrettifyState = {
      state.withCurrent(_.outdent.addIndent("}\n"))
    }

    private def doActor(state: PrettifyState, actor: Actor): PrettifyState = {
      state.withCurrent(
        _.add(s"actor ${actor.id.value} is \"${actor.is_a.s}\"")
          .emitBrief(actor.brief)
          .emitDescription(actor.description)
          .nl
      )
    }

    private def doConnector(
      state: PrettifyState,
      conn: Connector
    ): PrettifyState = {
      state.withCurrent { file =>
        file
          .openDef(conn)
          .add {
            val flows =
              if (conn.flows.nonEmpty)
                s"flows ${conn.flows.get.format} "
              else ""
            val from =
              if (conn.from.nonEmpty)
                s"from ${conn.from.get.format} "
              else ""
            val to =
              if (conn.to.nonEmpty)
                s"to ${conn.to.get.format}"
              else ""
            flows + from + to
          }
          .closeDef(conn)
      }
    }

    private def doInlet(state: PrettifyState, inlet: Inlet): PrettifyState = {
      state.withCurrent(
        _.addLine(s"inlet ${inlet.id.format} is ${inlet.type_.format}")
      )
    }

    private def doOutlet(
      state: PrettifyState,
      outlet: Outlet
    ): PrettifyState = {
      state.withCurrent(
        _.addLine(s"outlet ${outlet.id.format} is ${outlet.type_.format}")
      )
    }

    private def doInteraction(state: PrettifyState, @unused interaction: Interaction ): PrettifyState = {
      // TODOO: write dooInteraction
      state
    }

    private def openFunction[TCD <: Definition](
      state: PrettifyState,
      function: Function
    ): PrettifyState = {
      val s1 = state.withCurrent(_.openDef(function))
      val s2 = function.input.fold[state.type](s1)(te =>
        s1.withCurrent(_.addIndent("requires ").emitTypeExpression(te).nl)
      )
      function.output.fold(s2)(te =>
        s2.withCurrent(_.addIndent("returns  ").emitTypeExpression(te).nl)
      )
    }

    private def openState(
      reformatState: PrettifyState,
      state: State
    ): PrettifyState = {
      reformatState.withCurrent { st =>
        st.addSpace()
          .add(
            s"${keyword(state)} ${state.id.format} of ${state.typ.format} is {"
          )
        if (state.isEmpty) {
          st.add(" ??? }")
        } else {
          st.nl.indent
        }
      }
    }

    private def openSagaStep(
      state: PrettifyState,
      step: SagaStep
    ): PrettifyState = {
      state.withCurrent(
        _.openDef(step)
          .emitExamples(step.doAction)
          .add("reverted by")
          .emitExamples(step.undoAction)
      )
    }

    private def openInclude[T <: Definition](
      state: PrettifyState,
      @unused include: Include[T]
    ): PrettifyState = {
      if (!state.options.singleFile) {
        include.source match {
          case Some(path: String) if path.startsWith("http") =>
            val url = new java.net.URL(path)
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
            state
        }
      } else { state }
    }

    private def closeInclude[T <: Definition](
      state: PrettifyState,
      @unused include: Include[T]
    ): PrettifyState = {
      if (!state.options.singleFile) { state.popFile() }
      else { state }
    }
  }
}
