/*
 * Copyright 2019 Reactific Software LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.reactific.riddl

import com.reactific.riddl.RIDDLC.log
import com.reactific.riddl.language.{CommonOptions, ReformattingOptions}
import com.reactific.riddl.translator.hugo.{HugoTranslatingOptions, HugoTranslator}
import com.reactific.riddl.translator.hugo_git_check.HugoGitCheckOptions
import pureconfig.*
import pureconfig.error.ConfigReaderFailures
import scopt.*
import scopt.RenderingMode.OneColumn

import java.io.File
import java.net.URL
import java.nio.file.Path
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.util.control.NonFatal

/** Command Line Options for Riddl compiler program */

case class FromOptions(
  configFile: Option[Path] = None,
  inputFile: Option[Path] = None,
  outputDir: Option[Path] = None,
  hugoPath: Option[Path] = None
)
case class InputFileOptions(inputFile: Option[Path] = None)

object RepeatOptions {
  val defaultMaxLoops: Int = 1024
}
case class RepeatOptions(
  configFile: Option[Path] = None,
  refreshRate: FiniteDuration = 10.seconds,
  maxCycles: Int = RepeatOptions.defaultMaxLoops,
  interactive: Boolean = false
)

case class RiddlOptions(
  command: RiddlOptions.Command = RiddlOptions.Unspecified,
  fromOptions: FromOptions = FromOptions(),
  repeatOptions: RepeatOptions = RepeatOptions(),
  commonOptions: CommonOptions = CommonOptions(),
  parseOptions: InputFileOptions = InputFileOptions(),
  validateOptions: InputFileOptions = InputFileOptions(),
  reformatOptions: ReformattingOptions = ReformattingOptions(),
  hugoOptions: HugoTranslatingOptions = HugoTranslatingOptions(),
  hugoGitCheckOptions: HugoGitCheckOptions = HugoGitCheckOptions()
)

object RiddlOptions {

  sealed trait Command

  final case object Unspecified extends Command
  final case object Parse extends Command
  final case object Validate extends Command
  final case object Prettify extends Command
  final case object Hugo extends Command
  final case object HugoGitCheck extends Command
  final case object Help extends Command
  final case object From extends Command
  final case object Repeat extends Command
  final case object D3 extends Command
  final case object Info extends Command

  def str2Command(str: String): Command = {
    str match {
      case "from" => From
      case "repeat" => Repeat
      case "parse" => Parse
      case "validate" => Validate
      case "prettify" => Prettify
      case "hugo-git-check" => HugoGitCheck
      case "hugo" => Hugo
      case "d3" => D3
      case "info" => Info
      case _ => Unspecified
    }
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
        showWarnings <- optional(objCur, "show-warnings", true) { cc => cc.asBoolean }
        showStyleWarnings <- optional(objCur, "show-style-warnings", false) { cc => cc.asBoolean }
        showMissingWarnings <- optional(objCur, "show-missing-warnings", false) { cc => cc.asBoolean }
      } yield {
        CommonOptions(showTimes, verbose, dryRun, quiet,
          showWarnings, showMissingWarnings, showStyleWarnings)
      }
    }
  }

  implicit val ifReader: ConfigReader[InputFileOptions] = {
    (cur: ConfigCursor) => {
      for {
        objCur <- cur.asObjectCursor
        inFileRes <- objCur.atKey("input-file").map(_.asString)
        inFile <- inFileRes
      } yield {
        InputFileOptions(inputFile = Some(Path.of(inFile)))
      }
    }
  }

  implicit val reformatReader: ConfigReader[ReformattingOptions] = {
    (cur: ConfigCursor) => {
      for {
        objCur <- cur.asObjectCursor
        inputPathRes <- objCur.atKey("input-file")
        inputPath <- inputPathRes.asString
        outputPathRes <- objCur.atKey("output-dir")
        outputPath <- outputPathRes.asString
        projectName <- optional(objCur, "project-name",
          "No Project Name Specified") { cur => cur.asString }
        singleFileRes <- objCur.atKey("single-file")
        singleFile <- singleFileRes.asBoolean
      } yield
        ReformattingOptions(
          Option(Path.of(inputPath)),
          Option(Path.of(outputPath)),
          Option(projectName),
          singleFile
        )
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

  implicit val hugoGitCheckReader: ConfigReader[HugoGitCheckOptions] = {
    (cur: ConfigCursor) => {
      for {
        objCur <- cur.asObjectCursor
        hugo <- optional[HugoTranslatingOptions](
          objCur, "hugo", HugoTranslatingOptions()) {
          cur => htoReader.from(cur)
        }
        gitCloneDir <- optional[File](objCur, "git-clone-dir",
          new File(".")) { cc =>
          cc.asString.map(s => new File(s))
        }
        userNameRes <- objCur.atKey("user-name")
        userNameStr <- userNameRes.asString
        accessTokenRes <- objCur.atKey("access-token")
        accessTokenStr <- accessTokenRes.asString
      } yield {
        HugoGitCheckOptions(
          hugoOptions = hugo,
          gitCloneDir = Some(gitCloneDir.toPath),
          userName = userNameStr,
          accessToken = accessTokenStr
        )
      }
    }
  }

  private def noBool = Option.empty[Boolean]

  implicit val riddlReader: ConfigReader[RiddlOptions] = {
    (cur: ConfigCursor) => {
      for {
        objCur <- cur.asObjectCursor
        commandRes <- objCur.atKey("command")
        commandStr <- commandRes.asString
        showTimes <- optional(objCur, "show-times", noBool)(c =>
          c.asBoolean.map(Option(_)))
        verbose <- optional(objCur, "verbose", noBool)(cc =>
          cc.asBoolean.map(Option(_)))
        dryRun <- optional(objCur, "dry-run", noBool)(cc => cc.asBoolean.map(Option(_)))
        quiet <- optional(objCur, "quiet", noBool)(cc => cc.asBoolean.map(Option(_)))
        suppressWarnings <- optional(objCur, "suppress-warnings", noBool) {
          cc => cc.asBoolean.map(Option(_)) }
        suppressStyleWarnings <- optional(objCur, "suppress-style-warnings",
          noBool) { cc => cc.asBoolean.map(Option(_)) }
        suppressMissingWarnings <- optional(objCur,
          "suppress-missing-warnings", noBool) { cc => cc.asBoolean.map(Option(_)) }
        common <- optional[CommonOptions](
          objCur, "common", CommonOptions()){ cur => coReader.from(cur) }
        parse <- optional[InputFileOptions](objCur, "parse", InputFileOptions()){
          cur => ifReader.from(cur)
        }
        validate <- optional[InputFileOptions](objCur, "validate",
          InputFileOptions()){ cur =>ifReader.from(cur)}
        reformat <- optional[ReformattingOptions](objCur, "reformat",
          ReformattingOptions()){ cur => reformatReader.from(cur) }
        hugo <- optional[HugoTranslatingOptions](
          objCur, "hugo", HugoTranslatingOptions()){
          cur => htoReader.from(cur) }
        hugoGitCheck <- optional[HugoGitCheckOptions](
          objCur, "git", HugoGitCheckOptions()){
            cur => hugoGitCheckReader.from(cur)
          }
      } yield {
        RiddlOptions(
          str2Command(commandStr),
          FromOptions(),
          RepeatOptions(),
          common.copy(
            showTimes.getOrElse(common.showTimes),
            verbose.getOrElse(common.verbose),
            dryRun.getOrElse(common.dryRun),
            quiet.getOrElse(common.quiet),
            showWarnings = suppressWarnings.map(!_).getOrElse(common.showWarnings),
            showMissingWarnings = suppressMissingWarnings.map(!_).getOrElse(common.showMissingWarnings),
            showStyleWarnings = suppressStyleWarnings.map(!_).getOrElse(common.showStyleWarnings),
          ),
          parse,
          validate,
          reformat,
          hugo,
          hugoGitCheckOptions = hugoGitCheck
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
            validateOptions = loadedOptions.validateOptions,
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
                  inputFile = o.fromOptions.inputFile
                ))
              } else { o }
              val q = if (o.fromOptions.outputDir.nonEmpty) {
                p.copy(reformatOptions = o.reformatOptions.copy(
                  outputDir = o.fromOptions.outputDir
                ))
              } else { p }
              Option(q)
            case RiddlOptions.Hugo =>
              val p = if (o.fromOptions.inputFile.nonEmpty) {
                o.copy(hugoOptions = o.hugoOptions.copy(
                  inputFile = o.fromOptions.inputFile
                ))
              } else { o }
              val q = if (o.fromOptions.outputDir.nonEmpty) {
                p.copy(hugoOptions = o.hugoOptions.copy(
                  outputDir = o.fromOptions.outputDir
                ))
              } else { p }
              Option(q)
            case RiddlOptions.HugoGitCheck => Option(o)
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
    OParser.usage(parser, OneColumn)
  }

  def parse(args: Array[String]): Option[RiddlOptions] = {
    val setup: OParserSetup = new DefaultOParserSetup {
      override def showUsageOnError: Option[Boolean] = Option(false)
      override def renderingMode: RenderingMode.TwoColumns.type =
        RenderingMode.TwoColumns
    }

    val dontTerminate: DefaultOEffectSetup = new DefaultOEffectSetup {
      override def displayToOut(msg: String): Unit = {
        log.info(msg)
      }
      override def displayToErr(msg: String): Unit = {
        log.error(msg)
      }
      override def reportError(msg: String): Unit = {
        log.error(msg)
      }
      override def reportWarning(msg: String): Unit = {
        log.warn(msg)
      }

      // ignore terminate
      override def terminate(exitState: Either[String, Unit]): Unit = ()
    }

    val saneOptions = args.map(_.trim).filterNot(_.isEmpty)
    val (result, effects) = OParser.runParser(RiddlOptions.parser, saneOptions,
      RiddlOptions(), setup)
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

  private val hugoOptionsParser = Seq(
    inputFile((v, c) => c.copy(
      hugoOptions = c.hugoOptions.copy(inputFile = Option(v.toPath)))),
    outputDir((v, c) => c.copy(
      hugoOptions = c.hugoOptions.copy(outputDir = Option(v.toPath)))),
    opt[String]('p', "project-name").optional()
      .action((v, c) => c.copy(
        hugoOptions = c.hugoOptions.copy(projectName = Option(v)))
      )
      .text("optional project name to associate with the generated output")
      .validate(n =>
        if (n.isBlank) {
          Left("optional project-name cannot be blank or empty")
        } else {
          Right(())
        }
      ),
    opt[Boolean]('e', name = "erase-output")
      .text("Erase entire output directory before putting out files"),
    opt[URL]('b', "base-url").optional()
      .action((v, c) => c.copy(hugoOptions = c.hugoOptions.copy(
        baseUrl = Some(v)
      )))
      .text("Optional base URL for root of generated http URLs"),
    opt[Map[String, String]]('t', name = "themes")
      .action((t, c) => c.copy(hugoOptions =
        c.hugoOptions.copy(themes = t.toSeq.map(x =>
          x._1 -> Some(new URL(x._2))))
      )),
    opt[URL]('s', name = "source-url")
      .action((u, c) => c.copy(hugoOptions = c.hugoOptions.copy(baseUrl = Option(u))))
      .text("URL to the input file's Git Repository"),
    opt[String]('h', name = "edit-path")
      .action((h, c) => c.copy(hugoOptions = c.hugoOptions.copy(editPath = Option(h))))
      .text("Path to add to source-url to allow editing"),
    opt[URL]('l', name = "site-logo-url")
      .action((u, c) => c.copy(hugoOptions = c.hugoOptions.copy(siteLogo = Option(u))))
      .text("URL to the site's logo image for use by site"),
    opt[String]('m', "site-logo-path")
      .action((s, c) => c.copy(hugoOptions =
        c.hugoOptions.copy(siteLogoPath = Option(s)))
      ).text(
      """Path, in 'static' directory to placement and use
        |of the site logo.""".stripMargin
    )
  )

  private val repeatableCommands: OParser[Unit, RiddlOptions] = OParser.sequence(
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
          c.reformatOptions.copy(outputDir = Option(v.toPath))
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
      .children(hugoOptionsParser*)
      .text(
        """Parse and validate the input-file and then translate it into the input
          |needed for hugo to translate it to a functioning web site.""".stripMargin
      ),
      cmd("hugo-git-check")
      .action((_, c) => c.copy(command = HugoGitCheck))
      .children(
        arg[File]("git-clone-dir")
          .action((f, c) => c.copy(hugoGitCheckOptions =
            c.hugoGitCheckOptions.copy(gitCloneDir = Some(f.toPath))
          ))
          .text(
            """Provides the top directory of a git repo clone that
              |contains the <input-file> to be processed.""".stripMargin)
      ).children(hugoOptionsParser*)
      .text(
        """This command checks the <git-clone-dir> directory for new commits
          |and does a `git pull" command there if it finds some; otherwise
          |it does nothing. If commits were pulled from the repository, then
          |the hugo command is run to generate the hugo source files and hugo
          |is run to make the web site available at hugo's default local web
          |address:  |http://localhost:1313/
          |""".stripMargin
      ),
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
          )),
          opt[File]('H', "hugo-path").optional().action((v, c) => c.copy(
            fromOptions = c.fromOptions.copy(hugoPath = Option(v.toPath))
          )).text("optional path to the hugo web site generator"),

        )
        .text("Load riddlc options from a config file")
  )


  private val parser: OParser[Unit, RiddlOptions] = {
    OParser.sequence(
      programName("riddlc"),
      head(
        "RIDDL Compiler (c) 2022 Reactive Software LLC. All rights reserved.",
        "\nVersion: ",
        BuildInfo.version,
        "\n\nThis program parses, validates and translates RIDDL sources to other kinds",
        "\nof documents. RIDDL is a language for system specification based on Domain",
        "\nDrive Design, Reactive Architecture, and Agile principles.\n"
      ),
      help('h', "help").text("Print out help/usage information and exit"),
      version('V', "version"),
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
        c.copy(commonOptions = c.commonOptions.copy(
          showWarnings = false, showMissingWarnings = false, showStyleWarnings = false)
        )
      ).text("Suppress all warning messages so only errors are shown"),
      opt[Unit]('m', name = "suppress-missing-warnings").action((_, c) =>
        c.copy(commonOptions = c.commonOptions.copy(showMissingWarnings = false))
      ).text("Show warnings about things that are missing"),
      opt[Unit]('s', name = "suppress-style-warnings").action((_, c) =>
        c.copy(commonOptions = c.commonOptions.copy(showStyleWarnings = false))
      ).text("Show warnings about questionable input style. ")
    ) ++ repeatableCommands ++ OParser.sequence(
      cmd("help").action((_,c) => c.copy(command = Help))
        .text("Print out how to use this program" ),
      cmd("info").action((_,c) => c.copy(command = Info))
        .text("Print out build information about this program"),
      cmd("repeat").action((_,c) => c.copy(command = Repeat))
        .children(
          arg[File]("config-file")
            .required()
            .action((f, c) => c.copy(repeatOptions = c.repeatOptions.copy(
              configFile = Some(f.toPath)
            )))
            .text("The path to the configuration file that should be repeated"),
          arg[FiniteDuration]("refresh-rate")
            .optional()
            .validate {
              case r if r.toMillis < 1000 =>
                Left("<refresh-rate> is too fast, minimum is 1 seconds")
              case r if r.toDays > 1 =>
                Left("<refresh-rate> is too slow, maximum is 1 day")
              case _ => Right(())
            }
            .action((r, c) => c.copy(repeatOptions = c.repeatOptions.copy(
              refreshRate = r
            )))
            .text(
              """Specifies the rate at which the <git-clone-dir> is checked
                |for updates so the process to regenerate the hugo site is
                |started""".stripMargin
            ),
          arg[Int]("max-cycles")
            .optional()
            .validate {
              case x if x < 1 => Left("<max-cycles> can't be less than 1")
              case x if x > 1024 * 1024 => Left("<max-cycles> is too big")
              case _ => Right(())
            }
            .action((m, c) => c.copy(repeatOptions = c.repeatOptions.copy(
              maxCycles = m
            )))
            .text(
              """Limit the number of check cycles that will be repeated."""
            ),
        )
        .text(
          """This command supports the edit-build-check cycle. It doesn't end
            |until <max-cycles> has completed or EOF is reached on standard
            |input. During that time, the selected subcommands are repeated.
            |""".stripMargin
        ),
      opt[Unit]('n',"interactive")
        .optional().action((_,c) =>
          c.copy(repeatOptions = c.repeatOptions.copy(interactive = true))
        )
        .text(
          """This option causes the repeat command to read from the standard
            |input and when it reaches EOF (Ctrl-D is entered) then it cancels
            |the loop to exit.""".stripMargin
        )
    )
  }
}
