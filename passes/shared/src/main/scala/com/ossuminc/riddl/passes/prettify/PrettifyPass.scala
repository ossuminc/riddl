/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.ossuminc.riddl.passes.prettify

import com.ossuminc.riddl.language.AST
import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.passes.*
import com.ossuminc.riddl.passes.resolve.ResolutionPass
import com.ossuminc.riddl.passes.symbols.SymbolsPass
import com.ossuminc.riddl.passes.validate.ValidationPass

import scala.scalajs.js.annotation.JSExportTopLevel

@JSExportTopLevel("PrettifyPass$")
object PrettifyPass extends PassInfo[PrettifyPass.Options]:
  val name: String = "prettify"
  def creator(options: PrettifyPass.Options = PrettifyPass.Options()): PassCreator =
    (in: PassInput, out: PassesOutput) => PrettifyPass(in, out, options)
  end creator

  case class Options(flatten: Boolean = false) extends PassOptions
end PrettifyPass

@JSExportTopLevel("PrettifyOutput")
case class PrettifyOutput(
  root: Root = Root.empty,
  messages: Messages = empty,
  state: PrettifyState = PrettifyState()
) extends PassOutput

/** This is the RIDDL Prettifier to convert an AST back to RIDDL plain text */
@JSExportTopLevel("PrettifyPass")
class PrettifyPass(
  input: PassInput,
  outputs: PassesOutput,
  options: PrettifyPass.Options
) extends VisitingPass[PrettifyVisitor](input, outputs, new PrettifyVisitor(options)):
  def name: String = PrettifyPass.name

  requires(SymbolsPass)
  requires(ResolutionPass)
  requires(ValidationPass)

  /** Generate the output of this Pass. This will only be called after all the calls to process have completed.
    *
    * @return
    *   an instance of the output type
    */
  def result(root: Root): PassOutput = PrettifyOutput(root, empty, visitor.result)

end PrettifyPass
