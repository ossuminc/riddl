package com.yoppworks.ossum.riddl

import com.yoppworks.ossum.riddl.RIDDLC.log
import com.yoppworks.ossum.riddl.language.ValidatingOptions
import com.yoppworks.ossum.riddl.language.{BuildInfo, CommonOptions, FormattingOptions}
import com.yoppworks.ossum.riddl.translator.hugo.HugoTranslatingOptions
import pureconfig.error.ConfigReaderFailures
import pureconfig.*
import scopt.*

import java.io.File
import java.net.URL
import java.nio.file.Path

/** Command Line Options for Riddl compiler program */

case class RiddlOptions(
  command: RiddlOptions.Command = RiddlOptions.Unspecified,
  optionsPath: Option[Path] = None,
  inputFile: Option[Path] = None,
  commonOptions: CommonOptions = CommonOptions(),
  validatingOptions: ValidatingOptions =
    ValidatingOptions(showStyleWarnings = false, showMissingWarnings = false),
  reformatOptions: FormattingOptions = FormattingOptions(),
  hugoOptions: HugoTranslatingOptions = HugoTranslatingOptions(),
)

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

  implicit val poReader: ConfigReader[CommonOptions] = {
    (cur: ConfigCursor) => {
      for {
        objCur <- cur.asObjectCursor
        showTimes <- optional(objCur, "showTimes", false)(cc => cc.asBoolean)
        verbose <- optional(objCur, "verbose", false)(cc => cc.asBoolean)
        quiet <- optional(objCur, "quiet", false)(cc => cc.asBoolean)
        dryRun <- optional(objCur, "dryRun", false)(cc => cc.asBoolean)
      } yield {
        CommonOptions(showTimes = showTimes, verbose = verbose, quiet = quiet, dryRun = dryRun)
      }
    }
  }

  implicit val voReader: ConfigReader[ValidatingOptions] = {
    (cur: ConfigCursor) => {
      for {
        objCur <- cur.asObjectCursor
        showWarnings <- optional(objCur, "showWarnings", true) { cc => cc.asBoolean }
        showStyleWarnings <- optional(objCur, "showStyleWarnings", false) { cc => cc.asBoolean }
        showMissingWarnings <- optional(objCur, "showMissingWarnings", false) { cc => cc.asBoolean }
      } yield {
        ValidatingOptions(showWarnings, showMissingWarnings, showStyleWarnings)
      }
    }
  }

  private val defaultURL = "https://example.org/"
  implicit val htoReader: ConfigReader[HugoTranslatingOptions] = {
    (cur: ConfigCursor) => {
      for {
        objCur <- cur.asObjectCursor
        eraseOutput <- optional(objCur, "eraseOutput", true) { cc => cc.asBoolean }
        projectName <- optional(objCur, "projectName", "No Project Name Specified") {
          cur => cur.asString
        }
        outputPathRes <- objCur.atKey("outputPath")
        outputPath <- outputPathRes.asString
        baseURL <- optional(objCur, "baseURL", defaultURL) { cc => cc.asString }
        /* TODO: Support theme configuration
        themesRes <- objCur.atKeyOrUndefined("themes") match {
          case keyCur if keyCur.isUndefined => Right()
          case keyCur => keyCur.asList
        }*/
        sourceURL <- optional(objCur, "sourceURL", defaultURL) { cc => cc.asString }
        editPath <-
          optional(objCur, "editPath", "path/to/hugo/content") { cc => cc.asString }
        siteLogoURL <- optional(objCur, "siteLogoURL", defaultURL) { cc => cc.asString }
      } yield {
        HugoTranslatingOptions(eraseOutput, Option(projectName),
          Option(Path.of(outputPath)),
          Option(new java.net.URL(baseURL)), Seq.empty[(String, URL)],
          Option(new java.net.URL(sourceURL)), Option(editPath),
          Option(new java.net.URL(siteLogoURL)), None
        )
      }
    }
  }

  def stringifyConfigReaderErrors(errors: ConfigReaderFailures): Seq[String] = {
    errors.toList.map { crf =>
      val location = crf.origin match {
        case Some(origin) => origin.description
        case None => "unknown location"
      }
      s"At $location: ${crf.description}"
    }
  }

  final def loadHugoTranslatingOptions(options: RiddlOptions): Option[HugoTranslatingOptions] = {
    loadOptions[HugoTranslatingOptions](options.optionsPath)
  }

  final def loadOptions[OPT](path: Option[Path])(implicit reader: ConfigReader[OPT]): Option[OPT] = {
    path match {
      case None =>
        None
      case Some(p) =>
        ConfigSource.file(p).load[OPT] match {
          case Right(loadedOptions) =>
            Some(loadedOptions)
          case Left(errors) =>
            RIDDLC.log.error(s"Failed to load options from $p because:\n")
            val failures = RiddlOptions.stringifyConfigReaderErrors(errors).mkString("", "\n", "\n")
            RIDDLC.log.error(failures)
            None
        }
    }
  }

  def resolve(options: RiddlOptions): Option[RiddlOptions] = {
    val newOptions: Option[RiddlOptions] = options.optionsPath match {
      case Some(path) =>
        require(options.command == Hugo, "Loading options from config file is only supported for hugo translations")
        loadOptions[HugoTranslatingOptions](Option(path)) match {
          case None => None
          case Some(loadedOptions) =>
            Some(options.copy(hugoOptions = loadedOptions))
        }
      case None => Some(options)
    }
    newOptions match {
      case None => None
      case Some(nOptions) =>
        if (nOptions.inputFile.isEmpty) {
          log.error("An input file must be specified but wasn't.")
          None
        } else if (nOptions.command == Unspecified) {
          log.error("A command was expected to be chosen.")
          None
        } else {
          newOptions
        }
    }
  }


  def usage: String = {
    OParser.usage(parser)
  }

  def parse(args: Array[String]): Option[RiddlOptions] = {
    val (result, effects) = OParser.runParser(RiddlOptions.parser, args, RiddlOptions(), setup)
    OParser.runEffects(effects, dontTerminate)
    result
  }

  val builder: OParserBuilder[RiddlOptions] = OParser.builder[RiddlOptions]
  type OptionPlacer[V] = (V, RiddlOptions) => RiddlOptions

  import builder.*

  def inputFile(f: OptionPlacer[File]): OParser[File, RiddlOptions] = {
    opt[File]('i', "input-file").optional().action((v, c) => f(v, c))
      .text("required riddl input file to read")
  }

  def outputDir(f: OptionPlacer[File]): OParser[File, RiddlOptions] = {
    opt[File]('o', "output-dir").optional().action((v, c) => f(v, c))
      .text("required output directory for the generated output")
  }

  def projectName(f: OptionPlacer[String]): OParser[String, RiddlOptions] = {
    opt[String]('p', "project-name").optional().action((v, c) => f(v, c))
      .text("Optional project name to associate with the generated output").validate(n =>
      if (n.isBlank) Left("optional project-name cannot be blank or empty") else Right(())
    )
  }

  def baseUrl(f: OptionPlacer[URL]): OParser[URL, RiddlOptions] = {
    opt[URL]('b', "base-url").optional().action((v, c) => f(v, c))
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
      opt[Unit]('t', name = "show-times").action((_, c) =>
        c.copy(commonOptions = c.commonOptions.copy(showTimes = true))
      ).text("Show compilation phase execution times "),
      opt[Boolean]('d', "dry-run").optional().action((_, c) =>
        c.copy(commonOptions = c.commonOptions.copy(dryRun = true)))
        .text("go through the motions but don't write any changes"),
      opt[Unit]('v', "verbose").action((_, c) =>
        c.copy(commonOptions = c.commonOptions.copy(verbose = true)))
        .text("Provide detailed, step-by-step, output detailing riddlc's actions"),
      opt[Unit]('q', "quiet").action((_, c) =>
        c.copy(commonOptions = c.commonOptions.copy(quiet = true)))
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
      cmd("parse").action((_, c) => c.copy(command = Parse))
        .children(
          inputFile((v, c) => c.copy(inputFile = Option(v.toPath)))
        )
        .text(
          """Parse the input for syntactic compliance with riddl language.
            |No validation or translation is done on the input""".stripMargin),
      cmd("validate").action((_, c) => c.copy(command = Validate))
        .children(
          inputFile((v, c) => c.copy(inputFile = Option(v.toPath)))
        )
        .text(
          """Parse the input and if successful validate the resulting model.
            |No translation is done on the input.""".stripMargin),
      cmd("reformat").action((_, c) => c.copy(command = Prettify))
        .children(
          inputFile((v, c) => c.copy(inputFile = Option(v.toPath))),
          outputDir((v, c) =>
            c.copy(reformatOptions = c.reformatOptions.copy(outputPath = Option(v.toPath))
            )),
          opt[Boolean]('s', name = "single-file")
            .action((v, c) => c.copy(reformatOptions = c.reformatOptions.copy(singleFile = v)))
            .text(
              """Resolve all includes and imports and write a single file with the same
                |file name as the input placed in the out-dir""".stripMargin)
        ).text(
        """Parse and validate the input-file and then reformat it to a
          |standard layout written to the output-dir.  """.stripMargin),
      cmd("hugo").action((_, c) => c.copy(command = Hugo))
        .children(
          optionsPath((v, c) => c.copy(optionsPath = Option(v.toPath))),
          opt[Boolean]('e', name = "erase-output").text("Erase entire output directory before putting out files"),
          inputFile((v, c) => c.copy(inputFile = Option(v.toPath))),
          outputDir((v, c) => c.copy(hugoOptions = c.hugoOptions.copy(outputPath = Option(v.toPath)))),
          projectName((v, c) => c.copy(hugoOptions = c.hugoOptions.copy(projectName = Option(v)))),
          baseUrl((v, c) => c.copy(hugoOptions = c.hugoOptions.copy(baseUrl = Option(v)))),
          // TODO: themes
          opt[URL]('s', name = "source-url")
            .action((u, c) => c.copy(hugoOptions = c.hugoOptions.copy(baseUrl = Option(u))))
            .text("URL to the input file's Git Repository"),
          opt[String]('h', name = "edit-path")
            .action((h, c) => c.copy(hugoOptions = c.hugoOptions.copy(editPath = Option(h))))
            .text("Path to add to source-url to allow editing"),
          opt[URL]('l', name = "site-logo")
            .action((u, c) => c.copy(hugoOptions = c.hugoOptions.copy(siteLogo = Option(u))))
            .text("URL to the site's logo image for use by site")
        ).text(
          """Parse and validate the input-file and then translate it into the input
            |needed for hugo to translate it to a functioning web site.""".stripMargin)
      )
  }
}
