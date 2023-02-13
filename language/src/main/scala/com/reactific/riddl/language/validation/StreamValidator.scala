package com.reactific.riddl.language.validation

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.Messages

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
  }
}
