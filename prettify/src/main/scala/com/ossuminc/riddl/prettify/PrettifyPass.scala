package com.ossuminc.riddl.prettify

import com.ossuminc.riddl.command.{PassCommandOptions, TranslationCommand}
import com.ossuminc.riddl.language.AST
import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.language.parsing.Keyword
import com.ossuminc.riddl.passes.*
import com.ossuminc.riddl.passes.resolve.ResolutionPass
import com.ossuminc.riddl.passes.symbols.SymbolsPass
import com.ossuminc.riddl.passes.validate.ValidationPass

import java.nio.file.Path
import scala.annotation.unused

object PrettifyPass extends PassInfo[PrettifyPass.Options]:
  val name: String = "prettify"
  def creator(options: PrettifyPass.Options = PrettifyPass.Options()): PassCreator =
    (in: PassInput, out: PassesOutput) => PrettifyPass(in, out, options)
  end creator

  /** Options for the PrettifyPass and PrettifyCommand */
  case class Options(
    inputFile: Option[Path] = None,
    outputDir: Option[Path] = Some(Path.of(System.getProperty("java.io.tmpdir"))),
    projectName: Option[String] = None,
    singleFile: Boolean = true
  ) extends TranslationCommand.Options
      with PassOptions
      with PassCommandOptions:
    def command: String = name
  end Options
end PrettifyPass

case class PrettifyOutput(
  root: Root = Root.empty,
  messages: Messages = empty,
  state: PrettifyState
) extends PassOutput

/** This is the RIDDL Prettifier to convert an AST back to RIDDL plain text */
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