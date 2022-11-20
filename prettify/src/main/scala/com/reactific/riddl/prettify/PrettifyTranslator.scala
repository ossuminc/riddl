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
import com.reactific.riddl.language.Validation
import com.reactific.riddl.utils.Logger

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
      case _: Domain        => Keywords.domain
      case _: Entity        => Keywords.entity
      case _: Enumerator    => ""
      case _: Example       => Keywords.example
      case _: Field         => ""
      case _: Function      => Keywords.function
      case _: Handler       => Keywords.handler
      case _: Inlet         => Keywords.inlet
      case _: Invariant     => Keywords.invariant
      case _: Joint         => Keywords.joint
      case _: Outlet        => Keywords.outlet
      case _: Pipe          => Keywords.pipe
      case _: Plant         => Keywords.plant
      case p: Processor     => p.shape.keyword
      case _: RootContainer => "root"
      case _: Saga          => Keywords.saga
      case _: SagaStep      => Keywords.step
      case _: State         => Keywords.state
      case _: Story         => Keywords.story
      case _: Term          => Keywords.term
      case _: Type          => Keywords.`type`
      case _                => "unknown"
    }
  }

  def translate(
    results: Validation.Result,
    @unused log: Logger,
    commonOptions: CommonOptions,
    options: PrettifyCommand.Options
  ): Either[Messages, PrettifyState] = {
    Right(doTranslation(results, log, commonOptions, options))
  }

  def doTranslation(
    results: Validation.Result,
    @unused log: Logger,
    commonOptions: CommonOptions,
    options: PrettifyCommand.Options
  ): PrettifyState = {
    val state = PrettifyState(commonOptions, options)
    val folder = new ReformatFolder
    Folding.foldAround(state, results.root, folder)
  }

  def translateToString(
    results: Validation.Result,
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

  class ReformatFolder extends Folder[PrettifyState] {
    override def openContainer(
      state: PrettifyState,
      container: Definition,
      parents: Seq[Definition]
    ): PrettifyState = {
      container match {
        case story: Story => openStory(state, story)
        // FIXME: Implement StoryCase, Interactions, etc.
        case domain: Domain     => openDomain(state, domain)
        case adaptor: Adaptor   => openAdaptor(state, adaptor)
        case typ: Type          => state.current.emitType(typ); state
        case function: Function => openFunction(state, function)
        case st: State          => openState(state, st)
        case oc: OnMessageClause       => openOnClause(state, oc)
        case step: SagaStep     => openSagaStep(state, step)
        case include: Include[Definition] @unchecked =>
          openInclude(state, include)
        case processor: Processor => openProcessor(state, processor)
        case _: RootContainer     =>
          // ignore
          state
        case container: Definition with WithOptions[?] =>
          // Applies To: Context, Entity, Interactions
          state.withCurrent(_.openDef(container).emitOptions(container))
        case container: Definition =>
          // Applies To: Saga, Plant, Handler, Processor
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
        case invariant: Invariant => state.withCurrent(
            _.openDef(invariant).closeDef(invariant, withBrace = false)
          )
        case pipe: Pipe   => doPipe(state, pipe)
        case joint: Joint => doJoint(state, joint)
        case actor: Actor => doActor(state, actor)
        case _: Field     => state // was handled by Type case in openContainer
        case _            =>
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
        case _: Type      => state // openContainer did all of it
        case story: Story => closeStory(state, story)
        case st: State    => state.withCurrent(_.closeDef(st))
        case _: OnMessageClause  => closeOnClause(state)
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

    def openDomain(
      state: PrettifyState,
      domain: Domain
    ): PrettifyState = {
      state.withCurrent(_.openDef(domain)).step { s1: PrettifyState =>
        domain.authorDefs.foldLeft(s1) { (st, author) =>
          st.withCurrent(
            _.addIndent(s"author is {\n").indent
              .addIndent(s"name = ${author.name.format}\n")
              .addIndent(s"email = ${author.email.format}\n")
          ).step { s2 =>
            author.organization.map(org =>
              s2.withCurrent(_.addIndent(s"organization =${org.format}\n"))
            ).orElse(Option(s2)).get
          }.step { s3 =>
            author.title.map(title =>
              s3.withCurrent(_.addIndent(s"title = ${title.format}\n"))
            ).orElse(Option(s3)).get
          }.withCurrent(_.outdent.addIndent("}\n"))
        }
      }
    }

    def openStory(state: PrettifyState, story: Story): PrettifyState = {
      state.withCurrent { st =>
        if (story.userStory.isEmpty) {
          st.openDef(story, withBrace = false).add(" ??? ")
        } else {
          val us = story.userStory.get
          val actor = us.actor.pathId
          st.openDef(story).addIndent("actor").add(actor.format).add(" ")
            .add(Readability.wants).add(" ").add(Readability.to)
            .add(s"\"${us.capability.s}\" so that \"${us.benefit.s}\"").nl
        }
      }
    }

    def closeStory(state: PrettifyState, story: Story): PrettifyState = {
      state.withCurrent(_.closeDef(story))
    }

    def openAdaptor(
      state: PrettifyState,
      adaptor: Adaptor
    ): PrettifyState = {
      state.withCurrent(
        _.addIndent(keyword(adaptor)).add(" ").add(adaptor.id.format).add(" ")
          .add(adaptor.direction.format).add(" ").add(adaptor.context.format)
          .add(" is {")
      ).step { s2 =>
        if (adaptor.isEmpty) { s2.withCurrent(_.emitUndefined().add(" }\n")) }
        else s2.withCurrent(_.add("\n").indent)
      }
    }

    def openProcessor(
      state: PrettifyState,
      processor: Processor
    ): PrettifyState = {
      state.withCurrent { file =>
        file.openDef(processor)
        processor.inlets.foreach(doInlet(state, _))
        processor.outlets.foreach(doOutlet(state, _))
      }
    }
    def openOnClause(
      state: PrettifyState,
      onClause: OnMessageClause
    ): PrettifyState = {
      state.withCurrent(
        _.addIndent("on ").emitMessageRef(onClause.msg).add(" {\n").indent
      )
    }

    def closeOnClause(state: PrettifyState): PrettifyState = {
      state.withCurrent(_.outdent.addIndent("}\n"))
    }

    def doActor(state: PrettifyState, actor: Actor): PrettifyState = {
      state.withCurrent(
        _.add(s"actor ${actor.id.value} is \"${actor.is_a.s}\"")
          .emitBrief(actor.brief).emitDescription(actor.description).nl
      )
    }

    def doPipe(state: PrettifyState, pipe: Pipe): PrettifyState = {
      state.withCurrent(_.openDef(pipe)).step { state =>
        pipe.transmitType match {
          case Some(typ) => state
              .withCurrent(_.addIndent("transmit ").emitTypeRef(typ))
          case None => state.withCurrent(_.addSpace().emitUndefined())
        }
      }.withCurrent(_.closeDef(pipe))
    }

    def doJoint(state: PrettifyState, joint: Joint): PrettifyState = {
      val s = state
        .withCurrent(_.addIndent(s"${keyword(joint)} ${joint.id.format} is "))
      joint match {
        case InletJoint(_, _, inletRef, pipeRef, _, _) => s.withCurrent(
            _.addIndent(s"inlet ${inletRef.pathId.format} from")
              .add(s" pipe ${pipeRef.pathId.format}\n")
          )
        case OutletJoint(_, _, outletRef, pipeRef, _, _) => s.withCurrent(
            _.addIndent(s"outlet ${outletRef.pathId.format} to")
              .add(s" pipe ${pipeRef.pathId.format}\n")
          )
      }
    }

    def doInlet(state: PrettifyState, inlet: Inlet): PrettifyState = {
      state.withCurrent(
        _.addLine(s"inlet ${inlet.id.format} is ${inlet.type_.format}")
      )
    }

    def doOutlet(state: PrettifyState, outlet: Outlet): PrettifyState = {
      state.withCurrent(
        _.addLine(s"outlet ${outlet.id.format} is ${outlet.type_.format}")
      )
    }

    def openFunction[TCD <: Definition](
      state: PrettifyState,
      function: Function
    ): PrettifyState = {
      state.withCurrent(_.openDef(function)).step { s =>
        function.input.fold(s)(te =>
          s.withCurrent(_.addIndent("requires ").emitTypeExpression(te).nl)
        )
      }.step { s =>
        function.output.fold(s)(te =>
          s.withCurrent(_.addIndent("returns  ").emitTypeExpression(te).nl)
        )
      }
    }

    def openState(reformatState: PrettifyState, state: State): PrettifyState = {
      reformatState.withCurrent { st =>
        val s1 = st.openDef(state)
        if (state.nonEmpty) {
          if (state.aggregation.isEmpty) { s1.add("fields { ??? } ").nl }
          else {
            s1.addIndent("fields ").emitFields(state.aggregation.fields).nl
          }
        }
      }
    }

    def openSagaStep(
      state: PrettifyState,
      step: SagaStep
    ): PrettifyState = {
      state.withCurrent(
        _.openDef(step).emitExamples(step.doAction)
          .add("reverted by")
          .emitExamples(step.undoAction)
      )
    }

    def openInclude[T <: Definition](
      state: PrettifyState,
      @unused include: Include[T]
    ): PrettifyState = {
      if (!state.options.singleFile) {
        include.path match {
          case Some(path) =>
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

    def closeInclude[T <: Definition](
      state: PrettifyState,
      @unused include: Include[T]
    ): PrettifyState = {
      if (!state.options.singleFile) { state.popFile() }
      else { state }
    }
  }
}
