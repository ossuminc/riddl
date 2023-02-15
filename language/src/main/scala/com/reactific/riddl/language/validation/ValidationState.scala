package com.reactific.riddl.language.validation
import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.*


case class ValidationState(
  symbolTable: SymbolTable,
  root: Definition = RootContainer.empty,
  commonOptions: CommonOptions = CommonOptions())
    extends TypeValidationState
      with ExampleValidationState
      with StreamingValidationState
