package com.reactific.riddl.language.passes.resolution

import com.reactific.riddl.language.{AST, CommonOptions, Messages}
import com.reactific.riddl.language.passes.PassOutput
import com.reactific.riddl.language.passes.symbols.SymbolsOutput

case class ResolutionOutput(
  root: AST.RootContainer,
  commonOptions: CommonOptions,
  messages: Messages.Messages,
  symbols: SymbolsOutput,
  refMap: ReferenceMap,
  usage: Usages,
) extends PassOutput

