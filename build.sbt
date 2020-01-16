import sbt.Keys.scalaVersion
import sbtbuildinfo.BuildInfoOption.BuildTime
import sbtbuildinfo.BuildInfoOption.ToMap

// NEVER  SET  THIS: version := "0.1"
// IT IS HANDLED BY: sbt-dynver
dynverSeparator in ThisBuild := "-"
scalafmtOnCompile in ThisBuild := true
organization in ThisBuild := "com.yoppworks"
buildInfoOptions in ThisBuild := Seq(ToMap, BuildTime)
buildInfoKeys in ThisBuild := Seq[BuildInfoKey](
  name,
  normalizedName,
  description,
  homepage,
  startYear,
  organization,
  organizationName,
  organizationHomepage,
  version,
  scalaVersion,
  sbtVersion
)

def standardScalaCOptions(is2_13: => Boolean): Seq[String] = {
  Seq(
    "-encoding",
    "utf8",
    "-Xfatal-warnings",
    "-deprecation",
    "-unchecked",
    "-language:implicitConversions",
    "-language:higherKinds",
    "-language:existentials",
    "-language:postfixOps",
    "-J--illegal-access=none",
    { if (is2_13) "-Wdead-code" else "" }
  )
}

lazy val riddl = (project in file("."))
  .settings(publish := {}, publishLocal := {})
  .aggregate(language, translator, riddlc, `sbt-riddl`)

lazy val riddlc = (project in file("riddlc"))
  .enablePlugins(ParadoxPlugin)
  .enablePlugins(ParadoxSitePlugin)
  .enablePlugins(ParadoxMaterialThemePlugin)
  .enablePlugins(BuildInfoPlugin)
  .settings(
    name := "riddlc",
    mainClass := Some("com.yoppworks.ossum.riddl.RIDDL"),
    paradoxNavigationDepth := 5,
    //Determines depth of TOC for page navigation.
    /*
  val paradoxNavigationExpandDepth = settingKey[Option[Int]]("Depth of auto-expanding navigation below the active page.")
  val paradoxNavigationIncludeHeaders = settingKey[Boolean]("Whether to include headers in the navigation.")
  val paradoxRoots = settingKey[List[String]]("Which ToC roots (pages without parent) to expect.")
  val paradoxLeadingBreadcrumbs = settingKey[List[(String, String)]]("Any leading breadcrumbs (label -> url)")
  val paradoxIllegalLinkPath = settingKey[Regex]("Path pattern to fail site creation (eg. to protect against missing `@ref` for links).")
  val paradoxOrganization = settingKey[String]("Paradox dependency organization (for theme dependencies).")
  val paradoxSourceSuffix = settingKey[String]("Source file suffix for markdown files [default = \".md\"].")
  val paradoxTargetSuffix = settingKey[String]("Target file suffix for HTML files [default = \".html\"].")
  val paradoxTheme = settingKey[Option[ModuleID]]("Web module name of the paradox theme, otherwise local template.")
  val paradoxOverlayDirectories = settingKey[Seq[File]]("Directory containing common source files for configuration.")
  val paradoxDefaultTemplateName = settingKey[String]("Name of default template for generating pages.")
  val paradoxVersion = settingKey[String]("Paradox plugin version.")
  val paradoxGroups = settingKey[Map[String, Seq[String]]]("Paradox groups.")
  val paradoxValidationIgnorePaths = settingKey[List[Regex]]("List of regular expressions to apply to paths to determine if they should be ignored.")
  val paradoxValidationSiteBasePath = settingKey[Option[String]]("The base path that the documentation is deployed to, allows validating links on the docs site that are outside of the documentation root tree")
     */
    Compile / paradoxMaterialTheme := {
      ParadoxMaterialTheme()
        .withColor("blue", "grey")
        .withLogoIcon("yw-elephant")
        .withCopyright("Copyright © 2019 Yoppworks Inc.")
        .withSocial(
          uri("https://github.com/yoppworks"),
          uri("https://twitter.com/yoppworks"),
          uri("https://www.linkedin.com/company/yoppworks"),
          uri("https://www.facebook.com/YoppWorks/")
        )
      // .withFavicon("assets/images/riddl-favicon.png")
      // .withLogo("assets/images/riddl-logo.png")
    },
    libraryDependencies ++= Seq(
      "com.github.scopt" %% "scopt" % "4.0.0-RC2",
      "com.typesafe" % "config" % "1.4.0"
    ),
    crossScalaVersions := Seq("2.13.1", "2.12.10"),
    scalacOptions ++= standardScalaCOptions(scalaVersion.value == "2.13.1"),
    buildInfoPackage := "com.yoppworks.ossum.riddl"
  )
  .dependsOn(translator)
  .aggregate(language, translator)

lazy val language = (project in file("language")).settings(
  name := "riddl-languge",
  crossScalaVersions := Seq("2.13.1", "2.12.10"),
  scalacOptions ++= standardScalaCOptions(scalaVersion.value == "2.13.1"),
  buildInfoPackage := "com.yoppworks.ossum.riddl.language",
  libraryDependencies ++= Seq(
    "org.typelevel" %% "cats-core" % "2.1.0",
    "com.lihaoyi" %% "fastparse" % "2.2.3",
    "com.github.pureconfig" %% "pureconfig" % "0.12.2",
    "org.scalactic" %% "scalactic" % "3.1.0",
    "org.scalatest" %% "scalatest" % "3.1.0" % "test",
    "org.scalacheck" %% "scalacheck" % "1.14.3" % "test"
  )
)

lazy val translator = (project in file("translator"))
  .settings(
    name := "riddl-translator",
    crossScalaVersions := Seq("2.13.1", "2.12.10"),
    scalacOptions ++= standardScalaCOptions(scalaVersion.value == "2.13.1"),
    buildInfoPackage := "com.yoppworks.ossum.riddl.translator",
    libraryDependencies ++= Seq(
      "org.jfree" % "jfreesvg" % "3.4",
      "org.scalactic" %% "scalactic" % "3.1.0",
      "org.scalatest" %% "scalatest" % "3.1.0" % "test",
      "org.scalacheck" %% "scalacheck" % "1.14.3" % "test",
      "com.github.pureconfig" %% "pureconfig" % "0.12.2"
    )
  )
  .dependsOn(language % "test->test;compile->compile")

lazy val `sbt-riddl` = (project in file("sbt-riddl"))
  .settings(
    name := "sbt-riddl",
    sbtPlugin := true,
    scalaVersion := "2.12.10",
    scalacOptions ++= standardScalaCOptions(false),
    buildInfoPackage := "com.yoppworks.ossum.riddl.sbt.plugin"
  )
  .enablePlugins(SbtPlugin)
//  .enablePlugins(ParadoxPlugin)
  .dependsOn(translator)
