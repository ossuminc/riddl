/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands

import com.ossuminc.riddl.command.{CommandOptions, PassCommand}
import com.ossuminc.riddl.commands.hugo.HugoPass
import com.ossuminc.riddl.commands.hugo.themes.{DotdockWriter, GeekDocWriter}
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.passes.PassCreators
import com.ossuminc.riddl.utils.PlatformContext
import com.ossuminc.riddl.utils.StringHelpers.*
import org.ekrich.config.*
import org.ekrich.config.impl.ConfigString
import scopt.OParser
import sourcecode.Text.generate

import java.net.URL
import java.nio.file.Path
import scala.annotation.unused
import scala.collection.mutable 
import scala.jdk.CollectionConverters.MapHasAsScala

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

  override def interpretConfig(config: Config): Options =
    val obj = config.getObject(commandName).toConfig
    val inputFile = Path.of(obj.getString("input-file"))
    val outputDir = Path.of(obj.getString("output-dir"))
    val eraseOutput= if obj.hasPath("erase-output") then obj.getBoolean("erase-output") else false
    val projectName = if obj.hasPath("project-name") then obj.getString("project-name") else "No Project Name"
    val hugoThemeName = if obj.hasPath("hugo-theme-name") then obj.getString("hugo-theme-name") else "GeekDoc"
    val enterpriseName =
      if obj.hasPath("enterprise-name") then obj.getString("enterprise-name") else "No Enterprise Name"
    val siteTitle = if obj.hasPath("site-title") then obj.getString("site-title") else "No Site Title"
    val siteDescription =
      if obj.hasPath("site-description") then obj.getString("site-description") else "No Site Description"
    val siteLogoPath = if obj.hasPath("site-logo-path") then obj.getString("site-logo-path") else "static/somewhere"
    val siteLogoURL =
      if obj.hasPath("site-logo-url") then Some(obj.getString("site-logo-url")) else None
    val baseURL = if obj.hasPath("base-url") then Some(obj.getString("base-url")) else None
    val themesMap: mutable.Map[String,Object] = 
      if obj.hasPath("themes") then obj.getObject("themes").unwrapped.asScala else mutable.Map.empty[String,Object]
    val sourceURL = if obj.hasPath("source-url") then Some(obj.getString("source-url")) else None
    val viewPath =
      if obj.hasPath("view-path") then Some(obj.getString("view-path")) else Some("blob/main/src/main/riddl")
    val editPath =
     if obj.hasPath("edit-path") then Some(obj.getString("edit-path")) else Some("edit/main/src/main/riddl")
    val withGlossary = if obj.hasPath("with-glossary") then obj.getBoolean("with-glossary") else true
    val withToDoList = if obj.hasPath("with-todo-list") then obj.getBoolean("with-todo-list") else true
    val withStatistics = if obj.hasPath("with-statistics") then obj.getBoolean("with-statistics") else true
    val withGraphicalTOC = if obj.hasPath("with-graphical-toc") then obj.getBoolean("with-graphical-toc") else true
    def handleURL(url: Option[String]): Option[URL] =
      url match
        case None                 => Option.empty[URL]
        case Some(u) if u.isEmpty => Option.empty[URL]
        case Some(u)              => Option(java.net.URI(u).toURL)
      end match
    end handleURL

    val themes: Seq[(String, Option[java.net.URL])] =
      if themesMap.isEmpty then
        Seq("hugo-geekdoc" -> Option(HugoPass.geekDoc_url))
      else
        val themesEither = themesMap.toSeq.map(x => x._1 -> x._2)
        themesEither.map { (name: String, maybeUrl: Object) =>
            maybeUrl match
              case s: String => name -> handleURL(Option(s))
              case _ => name -> None
            end match
        }
      end if
    end themes

    HugoPass.Options(
      Option(inputFile),
      Option(outputDir),
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
      editPath,
      viewPath,
      withGlossary,
      withToDoList,
      withGraphicalTOC,
      withStatistics
   )
   end interpretConfig

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
