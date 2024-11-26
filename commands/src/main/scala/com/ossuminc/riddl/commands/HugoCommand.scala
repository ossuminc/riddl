/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands

import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.hugo.HugoPass
import com.ossuminc.riddl.hugo.themes.{DotdockWriter, GeekDocWriter}
import com.ossuminc.riddl.command.CommandOptions
import com.ossuminc.riddl.command.CommandOptions.optional
import com.ossuminc.riddl.command.PassCommand
import com.ossuminc.riddl.passes.PassCreators
import com.ossuminc.riddl.utils.PlatformContext
import pureconfig.ConfigCursor
import pureconfig.ConfigReader
import scopt.OParser

import java.net.URL
import java.nio.file.Path
import scala.annotation.unused

class HugoCommand(using pc: PlatformContext) extends PassCommand[HugoPass.Options]("hugo") {

  import HugoPass.Options

  override def getOptionsParser: (OParser[Unit, Options], Options) = {
    import builder.*
    cmd("hugo")
      .text(
        """Parse and validate the input-file and then translate it into the input
        |needed for hugo to translate it to a functioning web site.""".stripMargin
      )
      .children(
        inputFile((v, c) => c.copy(inputFile = Option(v.toPath))),
        outputDir((v, c) => c.copy(outputDir = Option(v.toPath))),
        opt[String]('p', "project-name")
          .optional()
          .action((v, c) => c.copy(projectName = Option(v)))
          .text("optional project name to associate with the generated output")
          .validate(n =>
            if n.isBlank then {
              Left("option project-name cannot be blank or empty")
            } else { Right(()) }
          ),
        opt[String]('E', "enterprise-name")
          .optional()
          .action((v, c) => c.copy(projectName = Option(v)))
          .text("optional enterprise name for C4 diagram output")
          .validate(n =>
            if n.isBlank then {
              Left("option enterprise-name cannot be blank or empty")
            } else { Right(()) }
          ),
        opt[String]('T', "hugo-theme-name")
          .optional()
          .action((v, c) => c.copy(hugoThemeName = Option(v)))
          .text("optional hugo theme name to use")
          .validate {
            case "geekdoc" | "GeekDoc" => Right(Some(GeekDocWriter.name))
            case "dotdoc" | "Dotdock"  => Right(Some(DotdockWriter.name))
          },
        opt[Boolean]('e', name = "erase-output")
          .text("Erase entire output directory before putting out files")
          .optional()
          .action((v, c) => c.copy(eraseOutput = v)),
        opt[Boolean]('e', name = "with-statistics")
          .text("Generate a statistics page accessible from index page")
          .optional()
          .action((v, c) => c.copy(withStatistics = v)),
        opt[Boolean]('e', name = "with-glossary")
          .text("Generate a glossary of terms and definitions ")
          .optional()
          .action((v, c) => c.copy(withGlossary = v)),
        opt[Boolean]('e', name = "with-todo-list")
          .text("Generate a To Do list")
          .optional()
          .action((v, c) => c.copy(withTODOList = v)),
        opt[Boolean]('e', name = "with-message-summary")
          .text("Generate a message summary for each domain")
          .optional()
          .action((v, c) => c.copy(withMessageSummary = v)),
        opt[Boolean]('e', name = "with-graphical-toc")
          .text("Generate a graphically navigable table of contents")
          .optional()
          .action((v, c) => c.copy(withGraphicalTOC = v)),
        opt[URL]('b', "base-url")
          .optional()
          .action((v, c) => c.copy(baseUrl = Some(v)))
          .text("Optional base URL for root of generated http URLs"),
        opt[Map[String, String]]('t', name = "themes")
          .action((t, c) => c.copy(themes = t.toSeq.map(x => x._1 -> Some(java.net.URI.create(x._2).toURL))))
          .text("Add theme name/url pairs to use alternative Hugo themes"),
        opt[URL]('s', name = "source-url")
          .action((u, c) => c.copy(baseUrl = Option(u)))
          .text("URL to the input file's Git Repository"),
        opt[String]('h', name = "edit-path")
          .action((h, c) => c.copy(editPath = Option(h)))
          .text("Path to add to source-url to allow editing"),
        opt[String]('m', "site-logo-path")
          .action((s, c) => c.copy(siteLogoPath = Option(s)))
          .text("""Path, in 'static' directory to placement and use
                |of the site logo.""".stripMargin),
        opt[String]('n', "site-logo-url")
          .action((s, c) => c.copy(siteLogoURL = Option(java.net.URI(s).toURL)))
          .text("URL from which to copy the site logo.")
      ) -> HugoPass.Options()
  }

  override def getConfigReader: ConfigReader[Options] = { (cur: ConfigCursor) =>
    for
      topCur <- cur.asObjectCursor
      topRes <- topCur.atKey(pluginName)
      objCur <- topRes.asObjectCursor
      inputPathRes <- objCur.atKey("input-file")
      inputPath <- inputPathRes.asString
      outputPathRes <- objCur.atKey("output-dir")
      outputPath <- outputPathRes.asString
      eraseOutput <- optional(objCur, "erase-output", false) { cc => cc.asBoolean }
      projectName <- optional(objCur, "project-name", "No Project Name") { cur => cur.asString }
      hugoThemeName <- optional(objCur, "hugo-theme-name", "GeekDoc") { cur => cur.asString }
      enterpriseName <- optional(objCur, "enterprise-name", "No Enterprise Name") { cur => cur.asString }
      siteTitle <- optional(objCur, "site-title", "No Site Title") { cur => cur.asString }
      siteDescription <- optional(objCur, "site-description", "No Site Description") { cur => cur.asString }
      siteLogoPath <- optional(objCur, "site-logo-path", "static/somewhere") { cc => cc.asString }
      siteLogoURL <- optional(objCur, "site-logo-url", Option.empty[String]) { cc => cc.asString.map(Option[String]) }
      baseURL <- optional(objCur, "base-url", Option.empty[String]) { cc =>
        cc.asString.map(Option[String])
      }
      themesMap <- optional(objCur, "themes", Map.empty[String, ConfigCursor]) { cc =>
        cc.asMap
      }
      sourceURL <- optional(objCur, "source-url", Option.empty[String]) { cc =>
        cc.asString.map(Option[String])
      }
      viewPath <- optional(objCur, "view-path", "blob/main/src/main/riddl") { cc =>
        cc.asString
      }
      editPath <- optional(objCur, "edit-path", "edit/main/src/main/riddl") { cc =>
        cc.asString
      }
      withGlossary <- optional(objCur, "with-glossary", true) { cc =>
        cc.asBoolean
      }
      withToDoList <- optional(objCur, "with-todo-list", true) { cc =>
        cc.asBoolean
      }
      withStatistics <- optional(objCur, "with-statistics", true) { cc =>
        cc.asBoolean
      }
      withGraphicalTOC <- optional(objCur, "with-graphical-toc", false) { cc =>
        cc.asBoolean
      }
    yield {
      def handleURL(url: Option[String]): Option[URL] =
        url match {
          case None                 => Option.empty[URL]
          case Some(u) if u.isEmpty => Option.empty[URL]
          case Some(u)              => Option(java.net.URI(u).toURL)
        }

      val themes: Seq[(String, Option[java.net.URL])] = {
        if themesMap.isEmpty then {
          Seq("hugo-geekdoc" -> Option(HugoPass.geekDoc_url))
        } else {
          val themesEither = themesMap.toSeq.map(x => x._1 -> x._2.asString)
          themesEither.map { case (name, maybeUrl) =>
            name -> {
              maybeUrl match {
                case Right(s) => handleURL(Option(s))
                case Left(x) =>
                  val errs = x.prettyPrint(1)
                  require(false, errs)
                  None
              }
            }
          }
        }
      }
      HugoPass.Options(
        Option(Path.of(inputPath)),
        Option(Path.of(outputPath)),
        Option(projectName),
        Option(hugoThemeName),
        Option(enterpriseName),
        eraseOutput,
        Option(siteTitle),
        Option(siteDescription),
        Option(siteLogoPath),
        handleURL(siteLogoURL),
        handleURL(baseURL),
        themes,
        handleURL(sourceURL),
        Option(editPath),
        Option(viewPath),
        withGlossary,
        withToDoList,
        withGraphicalTOC,
        withStatistics
      )
    }
  }

  def overrideOptions(options: Options, newOutputDir: Path): Options = {
    options.copy(outputDir = Some(newOutputDir))
  }

  def getPasses(options: Options): PassCreators = {
    HugoPass.getPasses(options)
  }

  override def replaceInputFile(
    opts: Options,
    @unused inputFile: Path
  ): Options = { opts.copy(inputFile = Some(inputFile)) }

  override def loadOptionsFrom(
    configFile: Path
  ): Either[Messages, HugoPass.Options] = {
    super.loadOptionsFrom(configFile).map { options =>
      resolveInputFileToConfigFile(options, configFile)
    }
  }

}
