package com.yoppworks.ossum.riddl

import com.yoppworks.ossum.riddl.RIDDLC.log
import com.yoppworks.ossum.riddl.language.ValidatingOptions
import com.yoppworks.ossum.riddl.language.{BuildInfo, CommonOptions, FormattingOptions}
import com.yoppworks.ossum.riddl.translator.hugo.{HugoTranslatingOptions, HugoTranslator}
import pureconfig.error.ConfigReaderFailures
import pureconfig.*
import scopt.*

import java.io.File
import java.net.URL
import java.nio.file.Path
import scala.util.control.NonFatal

/** Command Line Options for Riddl compiler program */

case class FromOptions(
  configFile: Option[Path] = None,
  inputFile: Option[Path] = None,
  outputDir: Option[Path] = None
)
case class ParseOptions(inputFile: Option[Path] = None)
case class ValidateOptions(inputFile: Option[Path] = None)
case class RiddlOptions(
  command: RiddlOptions.Command = RiddlOptions.Unspecified,
  fromOptions: FromOptions = FromOptions(),
  commonOptions: CommonOptions = CommonOptions(),
  validatingOptions: ValidatingOptions =
    ValidatingOptions(showStyleWarnings = false, showMissingWarnings = false),
  parseOptions: ParseOptions = ParseOptions(),
  validateOptions: ValidateOptions = ValidateOptions(),
  reformatOptions: FormattingOptions = FormattingOptions(),
  hugoOptions: HugoTranslatingOptions = HugoTranslatingOptions(),
)

object RiddlOptions {

  sealed trait Command

  final case object Unspecified extends Command
  final case object From extends Command
  final case object Parse extends Command
  final case object Validate extends Command
  final case object Prettify extends Command
  final case object Git extends Command
  final case object Hugo extends Command
  final case object D3 extends Command

  def str2Command(str: String): Command = {
    str match {
      case "from" => From
      case "parse" => Parse
      case "validate" => Validate
      case "prettify" => Prettify
      case "git" => Git
      case "hugo" => Hugo
      case "d3" => D3
      case _ => Unspecified
    }
  }

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
    default: T)(mapIt: ConfigCursor => ConfigReader.Result[T]
  ): ConfigReader.Result[T] = {
    objCur.atKeyOrUndefined(key) match {
      case stCur if stCur.isUndefined => Right[ConfigReaderFailures, T](default)
      case stCur => mapIt(stCur)
    }
  }

  implicit val coReader: ConfigReader[CommonOptions] = {
    (cur: ConfigCursor) => {
      for {
        objCur <- cur.asObjectCursor
        showTimes <- optional(objCur, "show-times", false)(cc => cc.asBoolean)
        verbose <- optional(objCur, "verbose", false)(cc => cc.asBoolean)
        quiet <- optional(objCur, "quiet", false)(cc => cc.asBoolean)
        dryRun <- optional(objCur, "dry-run", false)(cc => cc.asBoolean)
      } yield {
        CommonOptions(showTimes = showTimes, verbose = verbose, quiet = quiet, dryRun = dryRun)
      }
    }
  }

  implicit val voReader: ConfigReader[ValidatingOptions] = {
    (cur: ConfigCursor) => {
      for {
        objCur <- cur.asObjectCursor
        showWarnings <- optional(objCur, "show-warnings", true) { cc => cc.asBoolean }
        showStyleWarnings <- optional(objCur, "show-style-warnings", false) { cc => cc.asBoolean }
        showMissingWarnings <- optional(objCur, "show-missing-warnings", false) { cc => cc.asBoolean }
      } yield {
        ValidatingOptions(showWarnings, showMissingWarnings, showStyleWarnings)
      }
    }
  }

  implicit val htoReader: ConfigReader[HugoTranslatingOptions] = {
    (cur: ConfigCursor) => {
      for {
        objCur <- cur.asObjectCursor
        eraseOutput <- optional(objCur, "erase-output",
          true) { cc => cc.asBoolean }
        projectName <- optional(objCur, "project-name",
          "No Project Name Specified") { cur => cur.asString }
        inputPathRes <- objCur.atKey("input-file")
        inputPath <- inputPathRes.asString
        outputPathRes <- objCur.atKey("output-dir")
        outputPath <- outputPathRes.asString
        baseURL <- optional(
          objCur, "base-url", Option.empty[String]) { cc =>
          cc.asString.map(Option[String])
        }
        themesMap <- optional(objCur, "themes",
          Map.empty[String, ConfigCursor]) { cc => cc.asMap }
        sourceURL <- optional(objCur, "source-url", Option.empty[String]) {
          cc => cc.asString.map(Option[String])
        }
        editPath <- optional(objCur, "edit-path",
          "path/to/hugo/content") { cc => cc.asString }
        siteLogoURL <- optional(objCur, "site-logo-url", Option.empty[String]) {
          cc => cc.asString.map(Option[String])
        }
        siteLogoPath <- optional(objCur, "site-logo-path",
          "static/somewhere") { cc => cc.asString }
      } yield {
        def handleURL(url: Option[String]): Option[URL] = {
          if (url.isEmpty || url.get.isEmpty) {
            None
          } else {
            try {
              Option(new java.net.URL(url.get))
            } catch {
              case NonFatal(x) =>
                log.warn(s"Malformed URL: ${x.toString}")
                None
            }
          }
        }

        val themes = if (themesMap.isEmpty) {
          Seq("hugo-geekdoc" -> Option(HugoTranslator.geekDoc_url))
        } else {
          val themesEither = themesMap.toSeq.map(x => x._1 -> x._2.asString)
          themesEither.map { case (name, maybeUrl) =>
            name -> (maybeUrl match {
              case Right(s) => handleURL(Option(s))
              case Left(x) =>
                val errs = stringifyConfigReaderErrors(x).mkString("\n")
                log.error(errs)
                None
          })}}
          HugoTranslatingOptions(
            Option(Path.of(inputPath)),
            Option(Path.of(outputPath)),
            eraseOutput, Option(projectName),
            handleURL(baseURL), themes,
            handleURL(sourceURL), Option(editPath),
            handleURL(siteLogoURL), Option(siteLogoPath)
          )
        }
      }
    }

  implicit val riddlReader: ConfigReader[RiddlOptions] = {
    (cur: ConfigCursor) => {
      for {
        objCur <- cur.asObjectCursor
        commandRes <- objCur.atKey("command")
        commandStr <- commandRes.asString
        common <- optional[CommonOptions](
          objCur, "common", CommonOptions()){ cur => coReader.from(cur) }
        validation <- optional[ValidatingOptions](
          objCur, "validation", ValidatingOptions()){ cur =>
          voReader.from(cur)}
        hugo <- optional[HugoTranslatingOptions](
          objCur, "hugo", HugoTranslatingOptions()){
          cur => htoReader.from(cur) }
      } yield {
        RiddlOptions(
          command = str2Command(commandStr),
          commonOptions = common,
          validatingOptions = validation,
          hugoOptions = hugo
        )
      }
    }
  }

  final def loadRiddlOptions(
    options: RiddlOptions,
    path: Path
  ): Option[RiddlOptions] = {
    ConfigSource.file(path.toFile).load[RiddlOptions] match {
      case Right(loadedOptions) =>
        if (loadedOptions.command == Unspecified) {
          log.error("A command must be specified, but wasn't.")
          None
        } else {
          val o = options.copy(
            command = loadedOptions.command,
            commonOptions = loadedOptions.commonOptions,
            validatingOptions = loadedOptions.validatingOptions,
            reformatOptions = loadedOptions.reformatOptions,
            hugoOptions = loadedOptions.hugoOptions
          )
          loadedOptions.command match {
            case RiddlOptions.From =>
              log.error("The 'from' command is not supported from a config file")
              None
            case RiddlOptions.Parse =>
              val p = if (o.fromOptions.inputFile.nonEmpty) {
                o.copy(parseOptions = o.parseOptions.copy(
                  inputFile = o.fromOptions.inputFile
                ))
              } else { o }
              Option(p)
            case RiddlOptions.Validate =>
              val p = if (o.fromOptions.inputFile.nonEmpty) {
                o.copy(validateOptions = o.validateOptions.copy(
                  inputFile = o.fromOptions.inputFile
                ))
              } else { o }
              Option(p)
            case RiddlOptions.Prettify =>
              val p = if (o.fromOptions.inputFile.nonEmpty) {
                o.copy(reformatOptions = o.reformatOptions.copy(
                  inputPath = o.fromOptions.inputFile
                ))
              } else { o }
              val q = if (o.fromOptions.outputDir.nonEmpty) {
                p.copy(reformatOptions = o.reformatOptions.copy(
                  outputPath = o.fromOptions.outputDir
                ))
              } else { p }
              Option(q)
            case RiddlOptions.Hugo =>
              val p = if (o.fromOptions.inputFile.nonEmpty) {
                o.copy(hugoOptions = o.hugoOptions.copy(
                  inputPath = o.fromOptions.inputFile
                ))
              } else { o }
              val q = if (o.fromOptions.outputDir.nonEmpty) {
                p.copy(hugoOptions = o.hugoOptions.copy(
                  outputPath = o.fromOptions.outputDir
                ))
              } else { p }
              Option(q)
            case RiddlOptions.Git => Option(o)
            case RiddlOptions.D3 => Option(o)
            case _ => Option(o)
          }
        }
      case Left(errors) =>
        RIDDLC.log.error(s"Failed to load options from $path because:")
        val failures = RiddlOptions.stringifyConfigReaderErrors(errors).mkString("", "\n", "\n")
        RIDDLC.log.error(failures)
        None
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
      cmd("from")
        .action((_, c) => c.copy(command = From))
        .children(
          arg[File]("config-file").action { (file, ro) =>
            ro.copy(fromOptions =
              ro.fromOptions.copy(configFile = Some(file.toPath)))
          }.text("A HOCON configuration file with riddlc options"),
          inputFile((v, c) => c.copy(fromOptions =
            c.fromOptions.copy(inputFile = Option(v.toPath)))),
          outputDir((v, c) => c.copy(fromOptions =
            c.fromOptions.copy(outputDir = Option(v.toPath))
          ))
        )
        .text("Load riddlc options from a config file"),
      cmd("parse").action((_, c) => c.copy(command = Parse))
        .children(
          inputFile((v, c) => c.copy(parseOptions =
            c.parseOptions.copy(inputFile = Option(v.toPath))))
        )
        .text(
          """Parse the input for syntactic compliance with riddl language.
            |No validation or translation is done on the input""".stripMargin),
      cmd("validate").action((_, c) => c.copy(command = Validate))
        .children(
          inputFile((v, c) => c.copy(validateOptions =
            c.validateOptions.copy(inputFile = Option(v.toPath))))
        )
        .text(
          """Parse the input and if successful validate the resulting model.
            |No translation is done on the input.""".stripMargin),
      cmd("reformat").action((_, c) => c.copy(command = Prettify))
        .children(
          inputFile((v, c) => c.copy(reformatOptions =
            c.reformatOptions.copy(Option(v.toPath)))),
          outputDir((v, c) => c.copy(reformatOptions =
            c.reformatOptions.copy(outputPath = Option(v.toPath))
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
          opt[Boolean]('e', name = "erase-output").text("Erase entire output directory before putting out files"),
          inputFile((v, c) => c.copy(
            hugoOptions = c.hugoOptions.copy(inputPath = Option(v.toPath)))),
          outputDir((v, c) => c.copy(
            hugoOptions = c.hugoOptions.copy(outputPath = Option(v.toPath)))),
          projectName((v, c) => c.copy(
            hugoOptions = c.hugoOptions.copy(projectName = Option(v)))),
          baseUrl((v, c) => c.copy(
            hugoOptions = c.hugoOptions.copy(baseUrl = Option(v)))),
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
