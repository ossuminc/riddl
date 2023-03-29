package com.reactific.riddl.language.passes.resolution

import com.reactific.riddl.language.{AST, CommonOptions, Messages}
import com.reactific.riddl.language.AST.Definition
import com.reactific.riddl.language.passes.PassOutput
import com.reactific.riddl.language.passes.symbols.SymbolsOutput

case class ResolutionOutput(
  root: AST.RootContainer,
  commonOptions: CommonOptions,
  messages: Messages.Accumulator,
  symbols: SymbolsOutput,
  refMap: ReferenceMap,
  uses: Map[Definition, Seq[Definition]],
  usedBy: Map[Definition, Seq[Definition]]
) extends PassOutput

