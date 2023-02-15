package com.reactific.riddl.language.validation

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.Messages
import com.reactific.riddl.language.Messages.*

import scala.annotation.unused
import scala.collection.mutable

object StreamValidator {

  def validate(
    state: ValidationState,
    @unused definition: Definition,
    @unused parents: mutable.Stack[Definition]
  ): ValidationState = {
    val inlets = state.getInlets
    val outlets = state.getOutlets
    val pipes = state.getPipes
    val processors = state.getProcessors

    if (
      inlets.isEmpty && outlets.isEmpty && pipes.isEmpty && processors.isEmpty
    ) {
      state.add(Messages.style(
        "Models without any streaming data will exhibit minimal effect",
        definition.loc
      ))
    } else { state }

    val s1 = checkPipePersistence(state, pipes)
    checkUnattachedInlets(s1, pipes, inlets)
  }

  private def checkPipePersistence(
    state: ValidationState,
    pipes: Seq[Pipe]
  ): ValidationState = {
    pipes.foldLeft(state) { (state, pipe) =>
      val pipeParents = state.symbolTable.parentsOf(pipe)
      val maybePipeContext = state.symbolTable.contextOf(pipe)
      require(maybePipeContext.nonEmpty, "Pipe with no Context")
      val pipeContext = maybePipeContext.get
      val maybeToInlet = pipe.to.flatMap(inlet =>
        state.resolvePathIdentifier[Inlet](inlet.pathId, pipeParents)
      )
      val maybeFromOutlet = pipe.from.flatMap(outlet =>
        state.resolvePathIdentifier[Outlet](outlet.pathId, pipeParents)
      )
      val maybeInletContext = maybeToInlet
        .flatMap(inlet => state.symbolTable.contextOf(inlet))
      val maybeOutletContext = maybeFromOutlet
        .flatMap(outlet => state.symbolTable.contextOf(outlet))
      val inletIsSameContext = maybeInletContext.nonEmpty &&
        (pipeContext == maybeInletContext.get)
      val outletIsSameContext = maybeOutletContext.nonEmpty &&
        (pipeContext == maybeOutletContext.get)

      if (pipe.hasOption[PipePersistentOption]) {
        if (outletIsSameContext && inletIsSameContext) {
          val message = s"The persistence option on ${pipe.identify} is not " +
            s"needed since both ends of the pipe connect within the same " +
            s"context"
          state.add(style(message, pipe.loc))
        } else { state }
      } else {
        if (!outletIsSameContext || !inletIsSameContext) {
          val message = s"The persistence option should be specified on " +
            s"${pipe.identify} because an end of the pipe is not connected " +
            s"within the same context"
          state.add(style(message, pipe.loc))
        } else state
      }
    }
  }

  private def checkUnattachedInlets(
    state: ValidationState,
    pipes: Seq[Pipe],
    inlets: Seq[Inlet]
  ): ValidationState = {
    val inUseInlets: Seq[Inlet] = pipes.flatMap { pipe =>
      val parents = pipe +: state.symbolTable.parentsOf(pipe)
      pipe.to.flatMap[Inlet] { inletRef =>
        val maybe = state.resolvePathIdentifier[Inlet](inletRef.pathId, parents)
        maybe
      }
    }

    val unattachedInlets = inlets.toSet[Inlet] -- inUseInlets

    unattachedInlets.foldLeft(state) { (st, inlet) =>
      val message = s"${inlet.identify} is not attached to a pipe and will " +
        s"never " + s"receive any messages"
      st.add(error(message, inlet.loc))
    }
  }
}
