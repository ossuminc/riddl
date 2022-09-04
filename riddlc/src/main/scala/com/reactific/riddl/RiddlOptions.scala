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
import com.reactific.riddl.commands.{CommandOptions, CommandPlugin}
import com.reactific.riddl.hugo.HugoTranslator
import com.reactific.riddl.language.CommonOptions
import com.reactific.riddl.language.ReformattingOptions
import com.reactific.riddl.translator.hugo_git_check.HugoGitCheckOptions
import com.reactific.riddl.translator.kalix.KalixOptions
import com.reactific.riddl.utils.RiddlBuildInfo
import pureconfig.*
import pureconfig.error.ConfigReaderFailures
import scopt.*
import scopt.RenderingMode.OneColumn

import java.io.File
import java.net.URL
import java.nio.file.Path
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration
import scala.util.control.NonFatal

/** Command Line Options for Riddl compiler program */

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




class RiddlOptions(
  command: String,
  val repeatOptions: RepeatOptions = RepeatOptions(),
  commonOptions: CommonOptions = CommonOptions(),
  hugoGitCheckOptions: HugoGitCheckOptions = HugoGitCheckOptions(),
  pluginsDir: Option[Path] = Some(Path.of("plugins")),
  commandArgs: Map[String,String] = Map.empty[String,String]
)

object RiddlOptions {


  def str2Command(str: String): Command = {
    str match {
      case "repeat"         => Repeat
      case "hugo-git-check" => HugoGitCheck
      case "d3"             => D3
      case "info"           => Info
      case _                => Unspecified
    }
  }



  implicit val ifReader: ConfigReader[InputFileOptions] = {
    (cur: ConfigCursor) =>
      {
        for {
          objCur <- cur.asObjectCursor
          inFileRes <- objCur.atKey("input-file").map(_.asString)
          inFile <- inFileRes
        } yield { InputFileOptions(inputFile = Some(Path.of(inFile))) }
      }
  }


  implicit val kalixReader: ConfigReader[KalixOptions] = {
    (cur: ConfigCursor) =>
      {
        for {
          objCur <- cur.asObjectCursor
          inputPathRes <- objCur.atKey("input-file")
          inputPath <- inputPathRes.asString
          outputPathRes <- objCur.atKey("output-dir")
          outputPath <- outputPathRes.asString
          kalixPathRes <- objCur.atKey("kalix-path")
          kalixPath <- kalixPathRes.asString
          projectName <-
            optional(objCur, "project-name", "No Project Name Specified") {
              cur => cur.asString
            }
        } yield {
          KalixOptions(
            Option(Path.of(inputPath)),
            Option(Path.of(outputPath)),
            Option(Path.of(kalixPath)),
            Option(projectName)
          )
        }
      }
  }

  private def noBool = Option.empty[Boolean]

  implicit val riddlReader: ConfigReader[RiddlOptions] = {
    (cur: ConfigCursor) =>
      {
        for {
          objCur <- cur.asObjectCursor
          commandRes <- objCur.atKey("command")
          commandStr <- commandRes.asString
          showTimes <- optional(objCur, "show-times", noBool)(c =>
            c.asBoolean.map(Option(_))
          )
          verbose <- optional(objCur, "verbose", noBool)(cc =>
            cc.asBoolean.map(Option(_))
          )
          dryRun <- optional(objCur, "dry-run", noBool)(cc =>
            cc.asBoolean.map(Option(_))
          )
          quiet <- optional(objCur, "quiet", noBool)(cc =>
            cc.asBoolean.map(Option(_))
          )
          debug <- optional(objCur, "debug", noBool)(cc =>
            cc.asBoolean.map(Option(_))
          )
          suppressWarnings <- optional(objCur, "suppress-warnings", noBool) {
            cc => cc.asBoolean.map(Option(_))
          }
          suppressStyleWarnings <-
            optional(objCur, "suppress-style-warnings", noBool) { cc =>
              cc.asBoolean.map(Option(_))
            }
          suppressMissingWarnings <-
            optional(objCur, "suppress-missing-warnings", noBool) { cc =>
              cc.asBoolean.map(Option(_))
            }
          pluginsDir <- optional[Option[Path]](objCur, "plugins-dir", None) {
            cc => cc.asString.map(f => Option(Path.of(f)))
          }
          common <- optional[CommonOptions](objCur, "common", CommonOptions()) {
            cur => coReader.from(cur)
          }
          parse <-
            optional[InputFileOptions](objCur, "parse", InputFileOptions()) {
              cur => ifReader.from(cur)
            }
          validate <-
            optional[InputFileOptions](objCur, "validate", InputFileOptions()) {
              cur => ifReader.from(cur)
            }
          reformat <- optional[ReformattingOptions](
            objCur,
            "reformat",
            ReformattingOptions()
          ) { cur => reformatReader.from(cur) }
          hugo <- optional[HugoTranslatingOptions](
            objCur,
            "hugo",
            HugoTranslatingOptions()
          ) { cur => htoReader.from(cur) }
          hugoGitCheck <- optional[HugoGitCheckOptions](
            objCur,
            "git",
            HugoGitCheckOptions()
          ) { cur => hugoGitCheckReader.from(cur) }
          kalix <- optional[KalixOptions](objCur, "kalix", KalixOptions()) {
            cur => kalixReader.from(cur)
          }
        } yield {
          new RiddlOptions(
            str2Command(commandStr),
            FromOptions(),
            RepeatOptions(),
            common.copy(
              showTimes.getOrElse(common.showTimes),
              verbose.getOrElse(common.verbose),
              dryRun.getOrElse(common.dryRun),
              quiet.getOrElse(common.quiet),
              showWarnings = suppressWarnings.map(!_)
                .getOrElse(common.showWarnings),
              showMissingWarnings = suppressMissingWarnings.map(!_)
                .getOrElse(common.showMissingWarnings),
              showStyleWarnings = suppressStyleWarnings.map(!_)
                .getOrElse(common.showStyleWarnings),
              debug.getOrElse(common.debug)
            ),
            parse,
            validate,
            reformat,
            hugo,
            hugoGitCheck,
            kalix,
            pluginsDir
          )
        }
      }
  }
  final private def copyPrettifyOptions(
    o: RiddlOptions
  ): Option[RiddlOptions] = {
    val p =
      if (o.fromOptions.inputFile.nonEmpty) {
        o.copy(reformatOptions =
          o.reformatOptions.copy(inputFile = o.fromOptions.inputFile)
        )
      } else { o }
    val q =
      if (o.fromOptions.outputDir.nonEmpty) {
        p.copy(reformatOptions =
          o.reformatOptions.copy(outputDir = o.fromOptions.outputDir)
        )
      } else { p }
    Option(q)
  }
  final private def copyHugoOptions(o: RiddlOptions): Option[RiddlOptions] = {
    val p =
      if (o.fromOptions.inputFile.nonEmpty) {
        o.copy(hugoOptions =
          o.hugoOptions.copy(inputFile = o.fromOptions.inputFile)
        )
      } else { o }
    val q =
      if (o.fromOptions.outputDir.nonEmpty) {
        p.copy(hugoOptions =
          o.hugoOptions.copy(outputDir = o.fromOptions.outputDir)
        )
      } else { p }
    Option(q)
  }
  final private def copyKalixOptions(o: RiddlOptions): Option[RiddlOptions] = {
    val p =
      if (o.fromOptions.inputFile.nonEmpty) {
        o.copy(kalixOptions =
          o.kalixOptions.copy(inputFile = o.fromOptions.inputFile)
        )
      } else { o }
    val q =
      if (o.fromOptions.outputDir.nonEmpty) {
        p.copy(kalixOptions =
          o.kalixOptions.copy(outputDir = o.fromOptions.outputDir)
        )
      } else { p }
    val r =
      if (o.fromOptions.kalixPath.nonEmpty) {
        q.copy(kalixOptions =
          o.kalixOptions.copy(kalixPath = o.fromOptions.kalixPath)
        )
      } else { q }
    Option(r)
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
            hugoOptions = loadedOptions.hugoOptions,
            kalixOptions = loadedOptions.kalixOptions
          )
          loadedOptions.command match {
            case RiddlOptions.From =>
              log
                .error("The 'from' command is not supported from a config file")
              None
            case RiddlOptions.Parse =>
              val p =
                if (o.fromOptions.inputFile.nonEmpty) {
                  o.copy(parseOptions =
                    o.parseOptions.copy(inputFile = o.fromOptions.inputFile)
                  )
                } else { o }
              Option(p)
            case RiddlOptions.Validate =>
              val p =
                if (o.fromOptions.inputFile.nonEmpty) {
                  o.copy(validateOptions =
                    o.validateOptions.copy(inputFile = o.fromOptions.inputFile)
                  )
                } else { o }
              Option(p)
            case RiddlOptions.Prettify     => copyPrettifyOptions(o)
            case RiddlOptions.Hugo         => copyHugoOptions(o)
            case RiddlOptions.HugoGitCheck => Option(o)
            case RiddlOptions.Kalix        => copyKalixOptions(o)
            case RiddlOptions.D3           => Option(o)
            case _                         => Option(o)
          }
        }
      case Left(errors) =>
        RIDDLC.log.error(s"Failed to load options from $path because:")
        val failures = RiddlOptions.stringifyConfigReaderErrors(errors)
          .mkString("", "\n", "\n")
        RIDDLC.log.error(failures)
        None
    }
  }


  def usage: String = { OParser.usage(parser, OneColumn) }

  def parse(args: Array[String]): Option[CommandOptions] = {
    val setup: OParserSetup = new DefaultOParserSetup {
      override def showUsageOnError: Option[Boolean] = Option(false)
      override def renderingMode: RenderingMode.TwoColumns.type =
        RenderingMode.TwoColumns
    }

    val dontTerminate: DefaultOEffectSetup = new DefaultOEffectSetup {
      override def displayToOut(msg: String): Unit = { log.info(msg) }
      override def displayToErr(msg: String): Unit = { log.error(msg) }
      override def reportError(msg: String): Unit = { log.error(msg) }
      override def reportWarning(msg: String): Unit = { log.warn(msg) }

      // ignore terminate
      override def terminate(exitState: Either[String, Unit]): Unit = ()
    }

    val saneOptions = args.map(_.trim).filter(_.nonEmpty)
    val pluginOptions = getPluginOptions
    val combined = OParser.sequence(
      RiddlOptions.parser, pluginOptions
    )
    val (result, effects) = OParser
      .runParser[CommandOptions](combined, saneOptions, CommandOptions.empty, setup)
    OParser.runEffects(effects, dontTerminate)
    result
  }

  def getPluginOptions: OParser[Unit, CommandOptions] = {
    val dir = Path.of("plugins")
    val plugins = utils.Plugin
      .loadPluginsFrom[CommandPlugin[CommandOptions]](dir)
    val list = for {
      plugin <- plugins
    } yield {
      plugin.getOptions(log)
    }
    OParser.sequence(list.head._1, list.tail.map(_._1)*)
  }




  private val kalixOptionsParser = Seq(
    inputFile((v, c) =>
      c.copy(kalixOptions = c.kalixOptions.copy(inputFile = Option(v.toPath)))
    ),
    outputDir((v, c) =>
      c.copy(kalixOptions = c.kalixOptions.copy(outputDir = Option(v.toPath)))
    ),
    opt[File]('K', "kalix-path").optional().action((v, c) =>
      c.copy(kalixOptions = c.kalixOptions.copy(kalixPath = Option(v.toPath)))
    ).text("optional path to the kalix command"),
    opt[String]('p', "project-name").optional().action((v, c) =>
      c.copy(kalixOptions = c.kalixOptions.copy(projectName = Option(v)))
    ).text("optional project name to associate with the generated output")
      .validate(n =>
        if (n.isBlank) {
          Left("optional project-name cannot be blank or empty")
        } else { Right(()) }
      )
  )

  private val repeatableCommands: OParser[Unit, RiddlOptions] = OParser
    .sequence(
      cmd("parse").action((_, c) => c.copy(command = Parse))
        .children(inputFile((v, c) =>
          c.copy(parseOptions =
            c.parseOptions.copy(inputFile = Option(v.toPath))
          )
        )).text(
          """Parse the input for syntactic compliance with riddl language.
            |No validation or translation is done on the input""".stripMargin
        ),
      cmd("validate").action((_, c) => c.copy(command = Validate))
        .children(inputFile((v, c) =>
          c.copy(validateOptions =
            c.validateOptions.copy(inputFile = Option(v.toPath))
          )
        ))
        .text("""Parse the input and if successful validate the resulting model.
                |No translation is done on the input.""".stripMargin),
      cmd("hugo-git-check").action((_, c) => c.copy(command = HugoGitCheck))
        .children(
          arg[File]("git-clone-dir").action((f, c) =>
            c.copy(hugoGitCheckOptions =
              c.hugoGitCheckOptions.copy(gitCloneDir = Some(f.toPath))
            )
          ).text("""Provides the top directory of a git repo clone that
                   |contains the <input-file> to be processed.""".stripMargin)
        ).children(hugoOptionsParser*).text(
          """This command checks the <git-clone-dir> directory for new commits
            |and does a `git pull" command there if it finds some; otherwise
            |it does nothing. If commits were pulled from the repository, then
            |the hugo command is run to generate the hugo source files and hugo
            |is run to make the web site available at hugo's default local web
            |address:  |http://localhost:1313/
            |""".stripMargin
        ),
    )

  private val parser: OParser[Unit, CommandOptions] = {
    OParser.sequence(
      programName("riddlc"),
      head(
        "RIDDL Compiler (c) 2022 Reactive Software LLC. All rights reserved.",
        "\nVersion: ",
        RiddlBuildInfo.version,
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
        c.copy(commonOptions = c.commonOptions.copy(dryRun = true))
      ).text("go through the motions but don't write any changes"),
      opt[Unit]('v', "verbose").action((_, c) =>
        c.copy(commonOptions = c.commonOptions.copy(verbose = true))
      ).text(
        "Provide detailed, step-by-step, output detailing riddlc's actions"
      ),
      opt[Boolean]('D', "debug").optional().action((_,c) =>
        c.copy(commonOptions = c.commonOptions.copy(debug = true))
      ).text("Enable debug output. Only useful for riddlc developers"),
      opt[Unit]('q', "quiet").action((_, c) =>
        c.copy(commonOptions = c.commonOptions.copy(quiet = true))
      ).text("Do not print out any output, just do the requested command"),
      opt[Unit]('w', name = "suppress-warnings").action((_, c) =>
        c.copy(commonOptions =
          c.commonOptions.copy(
            showWarnings = false,
            showMissingWarnings = false,
            showStyleWarnings = false
          )
        )
      ).text("Suppress all warning messages so only errors are shown"),
      opt[Unit]('m', name = "suppress-missing-warnings").action((_, c) =>
        c.copy(commonOptions =
          c.commonOptions.copy(showMissingWarnings = false)
        )
      ).text("Show warnings about things that are missing"),
      opt[Unit]('s', name = "suppress-style-warnings").action((_, c) =>
        c.copy(commonOptions = c.commonOptions.copy(showStyleWarnings = false))
      ).text("Show warnings about questionable input style. "),
      opt[File]('P', name="plugins-dir").action((file,c) =>
        c.copy(pluginsDir = Some(file.toPath))
      ).text("Load riddlc command extension plugins from this directory.")
    ) ++ repeatableCommands ++ OParser.sequence(
      cmd("help").action((_, c) => c.copy(command = Help))
        .text("Print out how to use this program"),
      cmd("info").action((_, c) => c.copy(command = Info))
        .text("Print out build information about this program"),
      cmd("run").children(
        arg[String]("command").required().action((n,c) =>
          c.copy(command = Plugin(n))),
        arg[Map[String,String]]("args").unbounded().action( (m,c) =>
          c.copy(commandArgs = m)
        )
      ).text("Run an arbitrary command from a plugin module"),
      cmd("repeat").action((_, c) => c.copy(command = Repeat)).children(
        arg[File]("config-file").required().action((f, c) =>
          c.copy(repeatOptions =
            c.repeatOptions.copy(configFile = Some(f.toPath))
          )
        ).text("The path to the configuration file that should be repeated"),
        arg[FiniteDuration]("refresh-rate").optional().validate {
          case r if r.toMillis < 1000 =>
            Left("<refresh-rate> is too fast, minimum is 1 seconds")
          case r if r.toDays > 1 =>
            Left("<refresh-rate> is too slow, maximum is 1 day")
          case _ => Right(())
        }.action((r, c) =>
          c.copy(repeatOptions = c.repeatOptions.copy(refreshRate = r))
        ).text("""Specifies the rate at which the <git-clone-dir> is checked
                 |for updates so the process to regenerate the hugo site is
                 |started""".stripMargin),
        arg[Int]("max-cycles").optional().validate {
          case x if x < 1           => Left("<max-cycles> can't be less than 1")
          case x if x > 1024 * 1024 => Left("<max-cycles> is too big")
          case _                    => Right(())
        }.action((m, c) =>
          c.copy(repeatOptions = c.repeatOptions.copy(maxCycles = m))
        ).text("""Limit the number of check cycles that will be repeated.""")
      ).text("""This command supports the edit-build-check cycle. It doesn't end
               |until <max-cycles> has completed or EOF is reached on standard
               |input. During that time, the selected subcommands are repeated.
               |""".stripMargin),
      opt[Unit]('n', "interactive").optional().action((_, c) =>
        c.copy(repeatOptions = c.repeatOptions.copy(interactive = true))
      ).text(
        """This option causes the repeat command to read from the standard
          |input and when it reaches EOF (Ctrl-D is entered) then it cancels
          |the loop to exit.""".stripMargin
      ),
    )
  }
}
