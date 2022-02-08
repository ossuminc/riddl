package com.yoppworks.ossum.riddl

import com.yoppworks.ossum.riddl.language.Validation.ValidatingOptions
import com.yoppworks.ossum.riddl.language.{BuildInfo, FormattingOptions, Logger, ParsingOptions,
  StringLogger, SysLogger}
import com.yoppworks.ossum.riddl.translator.hugo.HugoTranslatingOptions
import pureconfig.error.{CannotConvert, ConfigReaderFailures}
import pureconfig.*
import scopt.*

import java.io.File
import java.net.URL
import java.nio.file.Path

/** Command Line Options for Riddl compiler program */
case class ParseOptions(
  parsingOptions: ParsingOptions = ParsingOptions(),
  inputPath: Option[Path] = None) {
  def log: Logger = parsingOptions.log
}

case class ValidateOptions(
  validatingOptions: ValidatingOptions =
    ValidatingOptions(showMissingWarnings = false, showStyleWarnings = false),
  inputPath: Option[Path] = None) {
  def log: Logger = validatingOptions.parsingOptions.log
}

case class HugoOptions(
  hugoTranslatingOptions: HugoTranslatingOptions = HugoTranslatingOptions(validatingOptions =
    ValidatingOptions(showMissingWarnings = false, showStyleWarnings = false)
  ))

case class D3Options(
  inputPath: Option[Path] = None)

case class RiddlOptions(
  dryRun: Boolean = false,
  verbose: Boolean = false,
  quiet: Boolean = false,
  optionsPath: Option[Path] = None,
  command: RiddlOptions.Command = RiddlOptions.Unspecified,
  validatingOptions: ValidatingOptions =
    ValidatingOptions(showStyleWarnings = false, showMissingWarnings = false),
  parseOptions: ParseOptions = ParseOptions(),
  validateOptions: ValidateOptions = ValidateOptions(),
  reformatOptions: FormattingOptions = FormattingOptions(),
  hugoOptions: HugoTranslatingOptions = HugoTranslatingOptions(),
  d3Options: D3Options = D3Options()) {
  def log: Logger = validatingOptions.parsingOptions.log
}

object RiddlOptions {

  sealed trait Command

  final case object Unspecified extends Command

  final case object Parse extends Command

  final case object Validate extends Command

  final case object Prettify extends Command

  final case object Hugo extends Command

  final case object D3 extends Command

  val setup: OParserSetup = new DefaultOParserSetup {
    override def showUsageOnError: Option[Boolean] = Option(true)

    override def renderingMode: RenderingMode.OneColumn.type = RenderingMode.OneColumn
  }

  val dontTerminate: DefaultOEffectSetup = new DefaultOEffectSetup {
    // ignore terminate
    override def terminate(exitState: Either[String, Unit]): Unit = ()
  }

  private def optional[T](
    objCur: ConfigObjectCursor,
    key: String,
    default: T)(mapIt: ConfigCursor => ConfigReader.Result[T]): ConfigReader.Result[T]
  = {
    objCur.atKeyOrUndefined(key) match {
      case stCur if stCur.isUndefined => Right[ConfigReaderFailures, T](default)
      case stCur => mapIt(stCur)
    }
  }

  implicit val poReader: ConfigReader[ParsingOptions] = {
    (cur: ConfigCursor) => {
      for {
        objCur <- cur.asObjectCursor
        showTimes <- optional(objCur, "showTimes", false)(cc => cc.asBoolean)
        logger <- optional[Logger](objCur, "logger", SysLogger()) { cc =>
          cc.asString.flatMap[ConfigReaderFailures,Logger] {
            case "String" => Right[ConfigReaderFailures, Logger](StringLogger())
            case "System" => Right[ConfigReaderFailures, Logger](SysLogger())
            case "" => Right[ConfigReaderFailures, Logger](SysLogger())
            case _ => cc.failed[Logger](CannotConvert("logger", "String",
              "this option only supports 'String' or 'System' or blank (defaults to System)"))
          }
        }
      } yield {
        ParsingOptions(showTimes = showTimes, Option(logger))
      }
    }
  }

  implicit val voReader: ConfigReader[ValidatingOptions] = {
    (cur: ConfigCursor) => {
      for {
        objCur <- cur.asObjectCursor
        parsingOptions <-
          optional(objCur, "parsingOptions", ParsingOptions()) { poCur =>
            poReader.from(poCur)
          }
        showWarnings <- optional(objCur, "showWarnings", true) { cc => cc.asBoolean }
        showStyleWarnings <- optional(objCur, "showStyleWarnings", false) { cc => cc.asBoolean }
        showMissingWarnings <- optional(objCur, "showMissingWarnings", false) { cc => cc.asBoolean }
      } yield {
        ValidatingOptions(parsingOptions, showWarnings, showMissingWarnings, showStyleWarnings)
      }
    }
  }

  private val defaultURL = "https://example.org/"
  implicit val htoReader: ConfigReader[HugoTranslatingOptions] = {
    (cur: ConfigCursor) => {
      for {
        objCur <- cur.asObjectCursor
        validatingOptions <- optional(objCur, "validatingOptions", ValidatingOptions()) { cc=>
          voReader.from(cc).flatMap(p => Right(p))
        }
        projectName <- optional(objCur, "projectName", "No Project Name Specified"){
          cur => cur.asString
        }
        inputPathRes <- objCur.atKey("inputPath")
        inputPath <- inputPathRes.asString
        outputPathRes <- objCur.atKey("outputPath")
        outputPath <- outputPathRes.asString
        baseURL <- optional(objCur, "baseURL", defaultURL) { cc => cc.asString }
        /* TODO: Support theme configuration
        themesRes <- objCur.atKeyOrUndefined("themes") match {
          case keyCur if keyCur.isUndefined => Right()
          case keyCur => keyCur.asList
        }*/
        sourceURL <- optional(objCur, "sourceURL", defaultURL) { cc =>cc.asString }
        editPath <-
          optional(objCur,"editPath", "path/to/hugo/content") { cc => cc.asString }
        siteLogoURL <- optional(objCur, "siteLogoURL", defaultURL) { cc => cc.asString }
      } yield {
        HugoTranslatingOptions(validatingOptions, Option(projectName),
          Option(Path.of(inputPath)), Option(Path.of(outputPath)), None, None,
          Option(new java.net.URL(baseURL)), Seq.empty[(String, URL)],
          Option(new java.net.URL(sourceURL)), Option(editPath),
          Option(new java.net.URL(siteLogoURL)), None
        )
      }
    }
  }



  final def loadHugoTranslatingOptions(options: RiddlOptions): ConfigReader
  .Result[HugoTranslatingOptions] = {
    loadOptions[HugoTranslatingOptions](options.optionsPath, HugoTranslatingOptions())
  }

  final def loadOptions[OPT](
    path: Option[Path],
    defaultOptions: OPT
  )(implicit reader: ConfigReader[OPT]): ConfigReader.Result[OPT] = {
    path match {
      case None =>
        Right(defaultOptions)
      case Some(p) =>
        ConfigSource.file(p).load[OPT]
    }
  }

  def usage: String = { OParser.usage(parser) }

  def parse(args: Array[String]): Option[RiddlOptions] = {
    OParser.runParser(RiddlOptions.parser, args, RiddlOptions(), setup) match {
      case (result, effects) =>
        OParser.runEffects(effects, dontTerminate)
        result match {
          case Some(options) => Option(options.copy(
              parseOptions = options.parseOptions
                .copy(parsingOptions = options.validatingOptions.parsingOptions),
              reformatOptions = options.reformatOptions
                .copy(validatingOptions = options.validatingOptions),
              hugoOptions = options.hugoOptions.copy(validatingOptions = options.validatingOptions)
            ))
          case None => None
        }
    }
  }

  val builder: OParserBuilder[RiddlOptions] = OParser.builder[RiddlOptions]
  type OptionPlacer[V] = (V, RiddlOptions) => RiddlOptions

  import builder.*

  def inputFile(f: OptionPlacer[File]): OParser[File, RiddlOptions] = {
    opt[File]('i', "input-file").required().action((v, c) => f(v, c))
      .text("required riddl input file to read")
  }

  def outputDir(f: OptionPlacer[File]): OParser[File, RiddlOptions] = {
    opt[File]('o', "output-dir").required().action((v, c) => f(v, c))
      .text("required output directory for the generated output")
  }

  def projectName(f: OptionPlacer[String]): OParser[String, RiddlOptions] = {
    opt[String]('p', "project-name").required().action((v, c) => f(v, c))
      .text("Optional project name to associate with the generated output").validate(n =>
        if (n.isBlank) Left("optional project-name cannot be blank or empty") else Right(())
      )
  }

  def baseUrl(f: OptionPlacer[URL]): OParser[URL, RiddlOptions] = {
    opt[URL]('u', "base-url").optional().action((v, c) => f(v, c))
      .text("Optional base URL for root of generated http URLs")
  }

  def optionsPath(f: OptionPlacer[File]): OParser[File, RiddlOptions] = {
    opt[File]('f', "options-file").optional().action((v, c) => f(v, c))
      .text("File from which to read options that specifies how to do the translation")
  }

  private val parser: OParser[Unit, RiddlOptions] = {
    OParser.sequence(
      programName("riddlc"),
      head(
        "RIDDL Compiler (c) 2021 Yoppworks Inc. All rights reserved.",
        "\nVersion: ",
        BuildInfo.version,
        "\n\nThis program parses, validates and translates RIDDL sources to other kinds",
        "\nof documents. RIDDL is a language for system specification based on Domain",
        "\nDrive Design, Reactive Architecture, and Agile principles.\n"
      ),
      help('h', "help").text("Print out help/usage information and exit"),
      opt[Boolean]('d', "dry-run").optional().action((_, c) => c.copy(dryRun = true))
        .text("go through the motions but don't write any changes"),
      opt[Unit]('v', "verbose").action((_, c) => c.copy(verbose = true))
        .text("Provide detailed, step-by-step, output detailing riddlc's actions"),
      opt[Unit]('q', "quiet").action((_, c) => c.copy(quiet = true))
        .text("Do not print out any output, just do the requested command"),
      opt[Unit]('w', name = "suppress-warnings").action((_, c) =>
        c.copy(validatingOptions =
          c.validatingOptions
            .copy(showWarnings = false, showMissingWarnings = false, showStyleWarnings = false)
        )
      ).text("Suppress all warning messages so only errors are shown"),
      opt[Unit]('m', name = "show-missing-warnings").action((_, c) =>
        c.copy(validatingOptions = c.validatingOptions.copy(showMissingWarnings = true))
      ).text("Show warnings about things that are missing"),
      opt[Unit]('s', name = "show-style-warnings").action((_, c) =>
        c.copy(validatingOptions = c.validatingOptions.copy(showStyleWarnings = true))
      ).text("Show warnings about questionable input style. "),
      opt[Unit]('t', name = "show-times").action((_, c) =>
        c.copy(validatingOptions =
          c.validatingOptions.copy(parsingOptions = ParsingOptions(showTimes = true))
        )
      ).text("Show compilation phase execution times "),
      optionsPath((v, c) => c.copy(optionsPath = Option(v.toPath))),
      cmd("parse").action((_, c) => c.copy(command = Parse)).children(inputFile((v, c) =>
        c.copy(parseOptions = c.parseOptions.copy(inputPath = Option(v.toPath)))
      )).text("""Parse the input for syntactic compliance with riddl language.
                |No validation or translation is done on the input""".stripMargin),
      cmd("validate").action((_, c) => c.copy(command = Validate)).children(inputFile((v, c) =>
        c.copy(validateOptions = c.validateOptions.copy(inputPath = Option(v.toPath)))
      )).text("""Parse the input and if successful validate the resulting model.
                |No translation is done on the input.""".stripMargin),
      cmd("reformat").action((_, c) => c.copy(command = Prettify)).children(
        inputFile((v, c) =>
          c.copy(reformatOptions = c.reformatOptions.copy(inputPath = Option(v.toPath)))
        ),
        outputDir((v, c) =>
          c.copy(reformatOptions = c.reformatOptions.copy(outputPath = Option(v.toPath)))
        ),
        opt[Boolean]('s', name = "single-file")
          .action((v, c) => c.copy(reformatOptions = c.reformatOptions.copy(singleFile = v)))
          .text("""Resolve all includes and imports and write a single file with the same
                  |file name as the input placed in the out-dir""".stripMargin)
      ).text("""Parse and validate the input-file and then reformat it to a
               |standard layout written to the output-dir.  """.stripMargin),
      cmd("hugo").action((_, c) => c.copy(command = Hugo)).children(
        inputFile((v, c) => c.copy(hugoOptions = c.hugoOptions.copy(inputPath = Option(v.toPath)))),
        outputDir((v, c) => c.copy(hugoOptions = c.hugoOptions.copy(outputPath = Option(v.toPath)))),
        baseUrl((v, c) => c.copy(hugoOptions = c.hugoOptions.copy(baseUrl = Option(v)))),
        projectName((v, c) => c.copy(hugoOptions = c.hugoOptions.copy(projectName = Option(v)))),
      ).text("""Parse and validate the input-file and then translate it into the input
               |needed for hugo to translate it to a functioning web site.""".stripMargin)
    )
  }
}
