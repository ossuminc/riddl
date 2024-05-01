package com.ossuminc.riddl.hugo.themes

import com.ossuminc.riddl.hugo.HugoPass
import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.passes.{PassInput, PassesOutput}

import java.nio.file.Path

case class GeekDocGenerator(
  options: HugoPass.Options,
  input: PassInput,
  outputs: PassesOutput,
  messages: Messages.Accumulator
) extends ThemeGenerator {

  /** Generate a string that contains the name of a definition that is markdown linked to the definition in its source.
    * For example, given sourceURL option of https://github.com/a/b and for an editPath option of src/main/riddl and for
    * a Location that has Org/org.riddl at line 30, we would generate this URL:
    * `https://github.com/a/b/blob/main/src/main/riddl/Org/org.riddl#L30` Note that that this works through recursive
    * path identifiers to find the first type that is not a reference Note: this only works for github sources
    * @param definition
    *   The definition for which we want the link
    * @return
    *   a string that gives the source link for the definition
    */
  def makeSourceLink(definition: Definition): String = {
    options.sourceURL match {
      case Some(url) =>
        options.viewPath match {
          case Some(viewPath) =>
            makeFilePath(definition) match {
              case Some(filePath) =>
                val lineNo = definition.loc.line
                url.toExternalForm ++ "/" ++ Path.of(viewPath, filePath).toString ++ s"#L$lineNo"
              case _ => ""
            }
          case None => ""
        }
      case None => ""
    }
  }

  def makeDocLink(definition: NamedValue, parents: Seq[String]): String = {
    val pars = ("/" + parents.mkString("/")).toLowerCase
    val result = definition match {
      case _: OnMessageClause | _: OnInitializationClause | _: OnTerminationClause | _: OnOtherClause | _: Inlet | _: Outlet =>
        pars + "#" + definition.id.value.toLowerCase
      case _: Field | _: Enumerator | _: Invariant | _: Author | _: SagaStep | _: Include[Definition] @unchecked |
          _: Root | _: Term =>
        pars
      case _ =>
        if parents.isEmpty then pars + definition.id.value.toLowerCase
        else pars + "/" + definition.id.value.toLowerCase
    }
    // deal with Geekdoc's url processor
    result.replace(" ", "-")
  }

  def makeDocAndParentsLinks(definition: NamedValue): String = {
    val parents = outputs.symbols.parentsOf(definition)
    val docLink = makeDocLink(definition, makeStringParents(parents))
    if parents.isEmpty then {
      s"[${definition.identify}]($docLink)"
    } else {
      parents.headOption match
        case None =>
          messages.addError(definition.loc, s"No parents found for definition '${definition.identify}")
          ""
        case Some(parent: Definition) =>
          val parentLink = makeDocLink(parent, makeStringParents(parents.drop(1)))
          s"[${definition.identify}]($docLink) in [${parent.identify}]($parentLink)"
    }
  }

  // scalastyle:off method.length
  def makeTomlFile(options: HugoPass.Options, author: Option[Author]): String = {
    val auth: Author = author.getOrElse(
      Author(
        1 -> 1,
        id = Identifier(1 -> 1, "unknown"),
        name = LiteralString(1 -> 1, "Not Provided"),
        email = LiteralString(1 -> 1, "somebody@somewere.tld")
      )
    )
    val themes: String = {
      options.themes.map(_._1).mkString("[ \"", "\", \"", "\" ]")
    }
    val baseURL: String = options.baseUrl
      .fold("https://example.prg/")(_.toString)
    val srcURL: String = options.sourceURL.fold("")(_.toString)
    val editPath: String = options.editPath.getOrElse("")
    val siteLogoPath: String = options.siteLogoPath.getOrElse("images/logo.png")
    val legalPath: String = "/legal"
    val privacyPath: String = "/privacy"
    val siteTitle = options.siteTitle.getOrElse("Unspecified Site Title")
    val siteName = options.projectName.getOrElse("Unspecified Project Name")
    val siteDescription = options.siteDescription
      .getOrElse("Unspecified Project Description")

    s"""######################## Hugo Configuration ####################
       |
       |# Configure GeekDocs
       |baseUrl = "$baseURL"
       |languageCode = "en-us"
       |title = "$siteTitle"
       |name = "$siteName"
       |description = "$siteDescription"
       |tags = ["docs", "documentation", "responsive", "simple", "riddl"]
       |min_version = "0.83.0"
       |theme = $themes
       |
       |# Required to get well formatted code blocks
       |pygmentsUseClasses = true
       |pygmentsCodeFences = true
       |disablePathToLower = true
       |enableGitInfo      = true
       |pygmentsStyle      =  "monokailight"
       |
       |# Required if you want to render robots.txt template
       |enableRobotsTXT = true
       |
       |
       |# markup(down?) rendering configuration
       |[markup.goldmark.renderer]
       |  # Needed for mermaid shortcode
       |  unsafe = true
       |[markup.tableOfContents]
       |  startLevel = 1
       |  endLevel = 9
       |[markup.goldmark.extensions]
       |  definitionList = true
       |  footnote = true
       |  linkify = true
       |  strikethrough = true
       |  table = true
       |  taskList = true
       |  typographer = true
       |
       |
       |[taxonomies]
       |  tag = "tags"
       |
       |# Author information from config
       |[params.author]
       |    name = "${auth.name.s}"
       |    email = "${auth.email.s}"
       |    homepage = "${auth.url.getOrElse(java.net.URI.create("https://example.org/").toURL)}"
       |
       |# Geekdoc parameters
       |[params]
       |
       |  # (Optional, default 6) Set how many table of contents levels to be showed on page.
       |  # Use false to hide ToC, note that 0 will default to 6 (https://gohugo.io/functions/default/)
       |  # You can also specify this parameter per page in front matter.
       |  geekdocToC = false
       |
       |  # (Optional, default static/brand.svg) Set the path to a logo for the Geekdoc
       |  # relative to your 'static/' folder.
       |  geekdocLogo = "$siteLogoPath"
       |
       |  # (Optional, default false) Render menu from data file in 'data/menu/main.yaml'.
       |  # See also https://geekdocs.de/usage/menus/#bundle-menu.
       |  geekdocMenuBundle = false
       |
       |  # (Optional, default false) Collapse all menu entries, can not be overwritten
       |  # per page if enabled. Can be enabled per page via `geekdocCollapseSection`.
       |  geekdocCollapseAllSections = false
       |
       |  # (Optional, default true) Show page navigation links at the bottom of each
       |  # docs page (bundle menu only).
       |  geekdocNextPrev = true
       |
       |  # (Optional, default true) Show a breadcrumb navigation bar at the top of each docs page.
       |  # You can also specify this parameter per page in front matter.
       |  geekdocBreadcrumb = true
       |
       |  # (Optional, default none) Set source repository location. Used for 'Edit page' links.
       |  # You can also specify this parameter per page in front matter.
       |  geekdocRepo = "$srcURL"
       |
       |  # (Optional, default none) Enable 'Edit page' links. Requires 'GeekdocRepo' param
       |  # and path must point to 'content' directory of repo.
       |  # You can also specify this parameter per page in front matter.
       |  geekdocEditPath = "$editPath"
       |
       |  # (Optional, default true) Enables search function with flexsearch.
       |  # Index is built on the fly and might slow down your website.
       |  geekdocSearch = true
       |
       |  # (Optional, default false) Display search results with the parent folder as prefix. This
       |  # option allows you to distinguish between files with the same name in different folders.
       |  # NOTE: This parameter only applies when 'geekdocSearch = true'.
       |  geekdocSearchShowParent = true
       |
       |  # (Optional, default none) Add a link to your Legal Notice page to the site footer.
       |  # It can be either a remote url or a local file path relative to your content directory.
       |  geekdocLegalNotice = "$legalPath"
       |
       |  # (Optional, default none) Add a link to your Privacy Policy page to the site footer.
       |  # It can be either a remote url or a local file path relative to your content directory.
       |  geekdocPrivacyPolicy = "$privacyPath"
       |
       |  # (Optional, default true) Add an anchor link to headlines.
       |  geekdocAnchor = true
       |
       |  # (Optional, default true) Copy anchor url to clipboard on click.
       |  geekdocAnchorCopy = true
       |
       |  # (Optional, default true) Enable or disable image lazy loading for images rendered
       |  # by the 'img' shortcode.
       |  geekdocImageLazyLoading = true
       |
       |  # (Optional, default false) Set HTMl <base> to .Site.BaseURL if enabled. It might be required
       |  # if a subdirectory is used within Hugos BaseURL.
       |  # See https://developer.mozilla.org/de/docs/Web/HTML/Element/base.
       |  geekdocOverwriteHTMLBase = false
       |
       |  # (Optional, default false) Auto-decrease brightness of images and add a slightly grayscale to avoid
       |  # bright spots while using the dark mode.
       |  geekdocDarkModeDim = true
       |
       |  # (Optional, default true) Display a "Back to top" link in the site footer.
       |  geekdocBackToTop = true
       |
       |  # (Optional, default false) Enable or disable adding tags for post pages automatically to the
       |  # navigation sidebar.
       |  geekdocTagsToMenu = true
       |
       |  # (Optional, default 'title') Configure how to sort file-tree menu entries. Possible options are 'title',
       |  # 'linktitle', 'date', 'publishdate', 'expirydate' or 'lastmod'. Every option can be used with a reverse
       |  # modifier as well e.g. 'title_reverse'.
       |  geekdocFileTreeSortBy = "title"
       |
       |""".stripMargin
  }
}
