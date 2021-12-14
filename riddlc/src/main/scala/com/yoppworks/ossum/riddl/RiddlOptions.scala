package com.yoppworks.ossum.riddl

import java.io.File

import com.yoppworks.ossum.riddl.language.Riddl
import scopt.OParserBuilder
import scala.language.postfixOps

/** Command Line Options for Riddl compiler program */
case class RiddlOptions(
  dryRun: Boolean = false,
  verbose: Boolean = false,
  quiet: Boolean = false,
  showTimes: Boolean = false,
  showWarnings: Boolean = true,
  showMissingWarnings: Boolean = false,
  showStyleWarnings: Boolean = false,
  command: RiddlOptions.Command = RiddlOptions.Unspecified,
  inputFile: Option[File] = None,
  outputDir: Option[File] = None,
  configFile: Option[File] = None,
  outputKind: Kinds.Kinds = Kinds.Paradox)
    extends Riddl.Options

object Kinds extends Enumeration {
  type Kinds = Value
  val Paradox, Prettify = Value
}

object RiddlOptions {
  import scopt.OParser

  implicit val kindsRead: scopt.Read[Kinds.Value] = scopt.Read.reads(Kinds withName)

  sealed trait Command
  case object Unspecified extends Command
  case object Parse extends Command
  case object Translate extends Command
  case object Validate extends Command

  val builder: OParserBuilder[RiddlOptions] = scopt.OParser.builder[RiddlOptions]

  val parser: OParser[Unit, RiddlOptions] = {
    import builder._
    OParser.sequence(
      programName("riddlc"),
      head(
        "RIDDL Compiler (c) 2019 Yoppworks Inc. All rights reserved.",
        "\nVersion: ",
        BuildInfo.version
      ),
      help('h', "help"),
      opt[Unit]('v', "verbose").action((_, c) => c.copy(verbose = true)),
      opt[Unit]('q', "quiet").action((_, c) => c.copy(quiet = true)),
      opt[Unit]('w', name = "suppress-warnings").action((_, c) => c.copy(showWarnings = false)),
      opt[Unit]('m', name = "suppress-missing-warnings")
        .action((_, c) => c.copy(showMissingWarnings = false)),
      opt[Unit]('s', name = "suppress-style-warnings")
        .action((_, c) => c.copy(showStyleWarnings = false)),
      opt[Unit]('t', name = "show-times").action((_, c) => c.copy(showTimes = true)),
      cmd("parse").action((_, c) => c.copy(command = Parse))
        .text("Parse the input for syntactic compliance with riddl language").children(
          opt[File]('i', "input-file").required().action((x, c) => c.copy(inputFile = Some(x)))
            .text("required riddl input file to compile")
        ),
      cmd("validate").action((_, c) => c.copy(command = Validate)).children(
        opt[File]('i', "input-file").required().action((x, c) => c.copy(inputFile = Some(x)))
          .text("required riddl input file to compile")
      ),
      cmd("translate").action((_, c) => c.copy(command = Translate))
        .text("translate riddl as specified in configuration file ").children(
          arg[Kinds.Kinds]("kind").action((k, c) => c.copy(outputKind = k))
            .text("The kind of output to generate during translation"),
          opt[File]('i', "input-file").required().action((x, c) => c.copy(inputFile = Some(x)))
            .text("required riddl input file to compile"),
          opt[File]('c', "configuration-file").required()
            .action((v, c) => c.copy(configFile = Some(v)))
            .text("configuration that specifies how to do the translation"),
          opt[Boolean]('d', "dry-run").hidden().action((_, c) => c.copy(dryRun = true))
            .text("go through the motions but don't write any changes")
        )
    )
  }

}
