package com.reactific.riddl.language.validation

import com.reactific.riddl.language.AST.*

import scala.annotation.unused
import scala.collection.mutable

object StreamValidation {

  def validate(
    state: ValidationState,
    @unused definition: Definition,
    @unused parents: mutable.Stack[Definition]
  ): ValidationState = {
    state
  }
}
