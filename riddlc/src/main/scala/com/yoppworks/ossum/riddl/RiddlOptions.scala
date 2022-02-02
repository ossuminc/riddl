package com.yoppworks.ossum.riddl

import java.io.File
import com.yoppworks.ossum.riddl.language.Riddl
import scopt.{DefaultOEffectSetup, OParserBuilder}

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
  outputKind: Kinds.Kinds = Kinds.Prettify,
  projectName: String = "Project")
    extends Riddl.Options

object Kinds extends Enumeration {
  type Kinds = Value
  val Hugo, Prettify, D3 = Value
}

object RiddlOptions {
  import scopt.OParser
  import scopt.{ OParserSetup, DefaultOParserSetup, RenderingMode }

  implicit val kindsRead: scopt.Read[Kinds.Value] = scopt.Read.reads(Kinds withName)

  sealed trait Command
  final case object Unspecified extends Command
  final case object Parse extends Command
  final case object Translate extends Command
  final case object Validate extends Command

  val setup: OParserSetup = new DefaultOParserSetup {
    override def showUsageOnError: Option[Boolean] = Option(true)
    override def renderingMode: RenderingMode.OneColumn.type = RenderingMode.OneColumn
  }
  val dontTerminate = new DefaultOEffectSetup {
    // ignore terminate
    override def terminate(exitState: Either[String, Unit]): Unit = ()
  }

  def parse(args: Array[String]): Option[RiddlOptions] = {
    OParser.runParser(RiddlOptions.parser, args, RiddlOptions(), setup) match {
      case (result, effects) =>
        OParser.runEffects(effects, dontTerminate)
        result
    }
  }

  val builder: OParserBuilder[RiddlOptions] = scopt.OParser.builder[RiddlOptions]

  val parser: OParser[Unit, RiddlOptions] = {
    import builder.*
    OParser.sequence(
      programName("riddlc"),
      head(
        "RIDDL Compiler (c) 2021 Yoppworks Inc. All rights reserved.",
        "\nVersion: ", BuildInfo.version,
        "\n\nThis program parses, validates and translates RIDDL sources to other kinds",
        "\nof documents. RIDDL is a language for system specification based on Domain",
        "\nDrive Design, Reactive Architecture, and Agile principles.\n"
      ),
      help('h', "help")
        .text("Print out help/usage information and exit"),
      opt[Unit]('v', "verbose").action((_, c) => c.copy(verbose = true))
        .text("Provide detailed, step-by-step, output detailing riddlc's actions"),
      opt[Unit]('q', "quiet").action((_, c) => c.copy(quiet = true))
        .text("Do not print out any output, just do the requested command"),
      opt[Unit]('w', name = "suppress-warnings")
        .action((_, c) => c.copy(showWarnings = false, showMissingWarnings = false,
          showStyleWarnings = false))
        .text("Suppress all warning messages so only errors are shown"),
      opt[Unit]('m', name = "show-missing-warnings")
        .action((_, c) => c.copy(showMissingWarnings = true))
        .text("Show warnings about things that are missing"),
      opt[Unit]('s', name = "show-style-warnings")
        .action((_, c) => c.copy(showStyleWarnings = true))
        .text("Show warnings about questionable input style. "),
      opt[Unit]('t', name = "show-times").action((_, c) => c.copy(showTimes = true))
        .text("Show compilation phase execution times "),
      cmd("parse")
        .action((_, c) => c.copy(command = Parse))
        .text(
          """Parse the input for syntactic compliance with riddl language.
            |No validation or translation is done on the input""".stripMargin).children(
          opt[File]('i', "input-file").required().action((x, c) => c.copy(inputFile = Option(x)))
            .text("required riddl input file to compile")
        ),
      cmd("validate")
        .action((_, c) => c.copy(command = Validate))
        .children(
          opt[File]('i', "input-file").required().action((x, c) => c.copy(inputFile = Option(x)))
            .text("required riddl input file to compile")
        ),
      cmd("translate")
        .action((_, c) => c.copy(command = Translate))
        .text("translate riddl as specified by kind argument and configuration file")
        .children(
          arg[Kinds.Kinds]("<kind>")
            .required()
            .action((k, c) => c.copy(outputKind = k))
            .text(s"The kind of output to generate during translation, one of:${
              Kinds.values.mkString(", ")}"),
          opt[File]('i', "input-file")
            .required()
            .action((x, c) => c.copy(inputFile = Option(x)))
            .text("required riddl input file to compile"),
          opt[File]('o', "output-dir")
            .required()
            .action((x, c) => c.copy(outputDir = Option(x)))
            .text("required output directory for the generated documentation"),
          opt[String]('p', "project-name")
            .optional()
            .action((n, c) => c.copy(projectName = n))
            .text("optional project name for the generated Hugo documentation")
            .validate(n =>
              if (n.isBlank) Left("optional project-name cannot be blank or empty") else Right(())
            ),
          opt[File]('c', "configuration-file")
            .optional()
            .action((v, c) => c.copy(configFile = Option(v)))
            .text("optional configuration file that specifies how to do the translation"),
          opt[Boolean]('d', "dry-run")
            .optional()
            .action((_, c) => c.copy(dryRun = true))
            .text("go through the motions but don't write any changes")
        )
    )
  }
}
