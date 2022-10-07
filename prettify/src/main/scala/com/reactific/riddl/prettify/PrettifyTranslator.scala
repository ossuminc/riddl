/*
 * Copyright 2019 Reactific Software LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
      case _: Adaptor           => Keywords.adaptor
      case _: EventActionA8n    => Keywords.adapt
      case _: EventCommandA8n   => Keywords.adapt
      case _: CommandCommandA8n => Keywords.adapt
      case _: Context           => Keywords.context
      case _: Domain            => Keywords.domain
      case _: Entity            => Keywords.entity
      case _: Enumerator        => ""
      case _: Example           => Keywords.example
      case _: Field             => ""
      case _: Function          => Keywords.function
      case _: Handler           => Keywords.handler
      case _: Inlet             => Keywords.inlet
      case _: Invariant         => Keywords.invariant
      case _: Joint             => Keywords.joint
      case _: Outlet            => Keywords.outlet
      case _: Pipe              => Keywords.pipe
      case _: Plant             => Keywords.plant
      case p: Processor         => p.shape.keyword
      case _: RootContainer     => "root"
      case _: Saga              => Keywords.saga
      case _: SagaStep          => Keywords.step
      case _: State             => Keywords.state
      case _: Story             => Keywords.story
      case _: Term              => Keywords.term
      case _: Type              => Keywords.`type`
      case _                    => "unknown"
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
        case s: Story           => openStory(state, s)
        case domain: Domain     => openDomain(state, domain)
        case adaptor: Adaptor   => openAdaptor(state, adaptor)
        case typ: Type          => state.current.emitType(typ); state
        case function: Function => openFunction(state, function)
        case st: State          => openState(state, st)
        case oc: OnClause       => openOnClause(state, oc)
        case step: SagaStep     => openSagaStep(state, step)
        case include: Include[Definition] @unchecked =>
          openInclude(state, include)
        case adaptation: Adaptation => openAdaptation(state, adaptation)
        case processor: Processor   => openProcessor(state, processor)
        case _: RootContainer       =>
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
        case _: OnClause  => closeOnClause(state)
        case include: Include[Definition] @unchecked =>
          closeInclude(state, include)
        case adaptation: AdaptorDefinition => closeAdaptation(state, adaptation)
        case _: RootContainer              =>
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
        domain.authors.foldLeft(s1) { (st, author) =>
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
          val actor = us.actor
          st.openDef(story).add(Keywords.actor).add(actor.id.format).add(" is ")
            .add(actor.is_a.map(_.format).getOrElse("\"\"")).nl
            .add(Keywords.capability).add(" is ").add(us.capability.format).nl
            .add(Keywords.benefit).add(" is ").add(us.benefit.format).nl
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
        _.addIndent(keyword(adaptor)).add(" ").add(adaptor.id.format)
          .add(" for ").add(adaptor.ref.format).add(" is {")
      ).step { s2 =>
        if (adaptor.isEmpty) { s2.withCurrent(_.emitUndefined().add(" }\n")) }
        else s2.withCurrent(_.add("\n").indent)
      }
    }

    def openAdaptation(
      state: PrettifyState,
      adaptation: Adaptation
    ): PrettifyState = {
      adaptation match {
        case ec8: EventCommandA8n => state.withCurrent(
            _.addIndent(s"adapt ${adaptation.id.format} is {\n").indent
              .addIndent("from ").emitMessageRef(ec8.messageRef).add(" to ")
              .emitMessageRef(ec8.command).add(" as {\n").indent
          )

        case cc8: CommandCommandA8n => state.withCurrent(
            _.addIndent(s"adapt ${adaptation.id.format} is {\n").indent
              .addIndent("from ").emitMessageRef(cc8.messageRef).add(" to ")
              .emitMessageRef(cc8.command).add(" as {\n").indent
          )

        case ea8: EventActionA8n => state.withCurrent(
            _.addIndent(s"adapt ${adaptation.id.format} is {\n").indent
              .addIndent("from ").emitMessageRef(ea8.messageRef).add(" to ")
              .emitActions(ea8.actions).add(" as {\n").indent
          )
      }
    }

    def closeAdaptation(
      state: PrettifyState,
      adaptation: AdaptorDefinition
    ): PrettifyState = {
      state.withCurrent(
        _.outdent.addIndent("}\n").outdent.addIndent("}\n")
          .emitBrief(adaptation.brief).emitDescription(adaptation.description)
      )
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
      onClause: OnClause
    ): PrettifyState = {
      state.withCurrent(
        _.addIndent("on ").emitMessageRef(onClause.msg).add(" {\n").indent
      )
    }

    def closeOnClause(state: PrettifyState): PrettifyState = {
      state.withCurrent(_.outdent.addIndent("}\n"))
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
            _.addIndent(s"inlet ${inletRef.id.format} from")
              .add(s" pipe ${pipeRef.id.format}\n")
          )
        case OutletJoint(_, _, outletRef, pipeRef, _, _) => s.withCurrent(
            _.addIndent(s"outlet ${outletRef.id.format} to")
              .add(s" pipe ${pipeRef.id.format}\n")
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
        _.openDef(step).emitAction(step.doAction).add("reverted by")
          .emitAction(step.undoAction)
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
