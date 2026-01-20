/*
 * Copyright 2019-2026 Ossum, Inc.
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

import java.net.URL
import java.nio.file.Path
import scala.annotation.unused
import scala.collection.mutable
import scala.jdk.CollectionConverters.MapHasAsScala

class HugoCommand(using pc: PlatformContext) extends PassCommand[HugoPass.Options]("hugo") {

  import HugoPass.Options

  private inline def input_file = "input-file"
  private inline def output_dir = "output-dir"
  private inline def erase_output = "erase-output"
  private inline def project_name = "project-name"
  private inline def enterprise_name = "enterprise-name"
  private inline def hugo_theme_name = "hugo-theme-name"
  private inline def with_statistics = "with-statistics"
  private inline def with_todo_list = "with-todo-list"
  private inline def with_glossary = "with-glossary"
  private inline def with_message_summary = "with-message-summary"
  private inline def with_graphical_toc = "with-graphical-toc"
  private inline def base_url = "base-url"
  private inline def themes = "themes"
  private inline def source_url = "source-url"
  private inline def edit_path = "edit-path"
  private inline def site_title = "site-title"
  private inline def site_description = "site-description"
  private inline def site_logo_path = "site-logo-path"
  private inline def site_logo_url = "site-logo-url"
  private inline def view_path = "view-path"

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
        opt[String]('p', project_name)
          .optional()
          .action((v, c) => c.copy(projectName = Option(v)))
          .text("optional project name to associate with the generated output")
          .validate(n =>
            if n.isBlank then {
              Left(s"option $project_name cannot be blank or empty")
            } else { Right(()) }
          ),
        opt[String]('E', enterprise_name)
          .optional()
          .action((v, c) => c.copy(enterpriseName = Option(v)))
          .text("optional enterprise name for C4 diagram output")
          .validate(n =>
            if n.isBlank then {
              Left("option enterprise-name cannot be blank or empty")
            } else { Right(()) }
          ),
        opt[String]('T', hugo_theme_name)
          .optional()
          .action((v, c) => c.copy(hugoThemeName = Option(v)))
          .text("optional hugo theme name to use")
          .validate {
            case "geekdoc" | "GeekDoc" => Right(Some(GeekDocWriter.name))
            case "dotdoc" | "Dotdock"  => Right(Some(DotdockWriter.name))
          },
        opt[Boolean]('e', name = erase_output)
          .text("Erase entire output directory before putting out files")
          .optional()
          .action((v, c) => c.copy(eraseOutput = v)),
        opt[Boolean]('e', name = with_statistics)
          .text("Generate a statistics page accessible from index page")
          .optional()
          .action((v, c) => c.copy(withStatistics = v)),
        opt[Boolean]('e', name = with_glossary)
          .text("Generate a glossary of terms and definitions ")
          .optional()
          .action((v, c) => c.copy(withGlossary = v)),
        opt[Boolean]('e', name = with_todo_list)
          .text("Generate a To Do list")
          .optional()
          .action((v, c) => c.copy(withTODOList = v)),
        opt[Boolean]('e', name = with_message_summary)
          .text("Generate a message summary for each domain")
          .optional()
          .action((v, c) => c.copy(withMessageSummary = v)),
        opt[Boolean]('e', name = with_graphical_toc)
          .text("Generate a graphically navigable table of contents")
          .optional()
          .action((v, c) => c.copy(withGraphicalTOC = v)),
        opt[URL]('b', base_url)
          .text("Optional base URL for root of generated http URLs")
          .optional()
          .action((v, c) => c.copy(baseUrl = Some(v))),
        opt[Map[String, String]]('t', name = themes)
          .text("Add theme name/url pairs to use alternative Hugo themes")
          .action((t, c) => c.copy(themes = t.toSeq.map(x => x._1 -> Some(java.net.URI.create(x._2).toURL)))),
        opt[URL]('s', name = source_url)
          .text("URL to the input file's Git Repository")
          .action((u, c) => c.copy(sourceURL = Option(u))),
        opt[String]('h', name = edit_path)
          .text("Path to add to source-url to allow editing")
          .action((h, c) => c.copy(editPath = Option(h))),
        opt[String]('v', name = view_path)
          .text("Path to add to source-url to allow viewing")
          .action((h, c) => c.copy(editPath = Option(h))),
        opt[String]('m', site_logo_path)
          .text("""Path, in 'static' directory to placement and use
                  |of the site logo.""".stripMargin)
          .action((s, c) => c.copy(siteLogoPath = Option(s))),
        opt[String]('n', site_logo_url)
          .text("URL from which to copy the site logo.")
          .action((s, c) => c.copy(siteLogoURL = Option(java.net.URI(s).toURL))),
        opt[String]('l', site_title)
          .text("Title of the site")
          .action((s,c) => c.copy(siteTitle = Option(s))),
        opt[String]('d', site_description)
          .text("Description of the site")
          .action((s,c) => c.copy(siteDescription = Option(s)))
      ) -> HugoPass.Options()
  }

  override def interpretConfig(config: Config): Options =
    val obj = config.getObject(commandName).toConfig
    val inputFile = Path.of(obj.getString(input_file))
    val outputDir = Path.of(obj.getString(output_dir))
    val eraseOutput= if obj.hasPath(erase_output) then obj.getBoolean(erase_output) else false
    val projectName = if obj.hasPath(project_name) then obj.getString(project_name) else "No Project Name"
    val hugoThemeName = if obj.hasPath(hugo_theme_name) then obj.getString(hugo_theme_name) else "GeekDoc"
    val enterpriseName =
      if obj.hasPath(enterprise_name) then obj.getString(enterprise_name) else "No Enterprise Name"
    val siteTitle = if obj.hasPath(site_title) then obj.getString(site_title) else "No Site Title"
    val siteDescription =
      if obj.hasPath(site_description) then obj.getString(site_description) else "No Site Description"
    val siteLogoPath = if obj.hasPath(site_logo_path) then obj.getString(site_logo_path) else "static/somewhere"
    val siteLogoURL =
      if obj.hasPath(site_logo_url) then Some(obj.getString(site_logo_url)) else None
    val baseURL = if obj.hasPath(base_url) then Some(obj.getString(base_url)) else None
    val themesMap: mutable.Map[String,Object] =
      if obj.hasPath(themes) then obj.getObject(themes).unwrapped.asScala else mutable.Map.empty[String,Object]
    val sourceURL = if obj.hasPath(source_url) then Some(obj.getString(source_url)) else None
    val viewPath =
      if obj.hasPath(view_path) then Some(obj.getString(view_path)) else Some("blob/main/src/main/riddl")
    val editPath =
     if obj.hasPath(edit_path) then Some(obj.getString(edit_path)) else Some("edit/main/src/main/riddl")
    val withGlossary = if obj.hasPath(with_glossary) then obj.getBoolean(with_glossary) else true
    val withToDoList = if obj.hasPath(with_todo_list) then obj.getBoolean(with_todo_list) else true
    val withStatistics = if obj.hasPath(with_statistics) then obj.getBoolean(with_statistics) else true
    val withGraphicalTOC = if obj.hasPath(with_graphical_toc) then obj.getBoolean(with_graphical_toc) else true
    def handleURL(url: Option[String]): Option[URL] =
      url match
        case None                 => Option.empty[URL]
        case Some(u) if u.isEmpty => Option.empty[URL]
        case Some(u)              => Option(java.net.URI(u).toURL)
      end match
    end handleURL

    val themesList: Seq[(String, Option[java.net.URL])] =
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
    end themesList

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
      themesList,
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
