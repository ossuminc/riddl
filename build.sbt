import sbt.Keys.scalaVersion
import sbtbuildinfo.BuildInfoOption.BuildTime
import sbtbuildinfo.BuildInfoOption.ToMap

// NEVER  SET  THIS: version := "0.1"
// IT IS HANDLED BY: sbt-dynver
dynverSeparator in ThisBuild := "-"
scalafmtOnCompile in ThisBuild := true
organization in ThisBuild := "com.yoppworks"
scalaVersion in ThisBuild := "2.13.3"
buildInfoOptions := Seq(ToMap, BuildTime)
buildInfoKeys := Seq[BuildInfoKey](
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

scalacOptions := Seq(
  s"-target:11",
  "-Wdead-code",
  // "-Woctal-literal", <-- that warning is broken, it warns on:  val x: Int = 0
  // "-Xdev",
  // "-Wunused:imports",   // Warn if an import selector is not referenced.
  "-Wunused:patvars", // Warn if a variable bound in a pattern is unused.
  "-Wunused:privates", // Warn if a private member is unused.
  "-Wunused:locals", // Warn if a local definition is unused.
  "-Wunused:explicits", // Warn if an explicit parameter is unused.
  "-Wunused:implicits", // Warn if an implicit parameter is unused.
  "-Wunused:params", // Enable -Wunused:explicits,implicits.
  "-Xlint:nonlocal-return", // A return statement used an exception for flow control.
  "-Xlint:implicit-not-found", // Check @implicitNotFound and @implicitAmbiguous messages.
  "-Xlint:serial", // @SerialVersionUID on traits and non-serializable classes.
  "-Xlint:valpattern", // Enable pattern checks in val definitions.
  "-Xlint:eta-zero", // Warn on eta-expansion (rather than auto-application) of zero-ary method.
  "-Xlint:eta-sam", // Warn on eta-expansion to meet a Java-defined functional
  // interface that is not explicitly annotated with @FunctionalInterface.
  "-Xlint:deprecation" // Enable linted deprecations.
)

lazy val compilecheck = taskKey[Unit]("compile and then scalastyle")

lazy val riddl = (project in file("."))
  .settings(publish := {}, publishLocal := {})
  .aggregate(language, translator, riddlc /*, `sbt-riddl`*/ )

lazy val riddlc = (project in file("riddlc")).enablePlugins(ParadoxPlugin)
  .enablePlugins(ParadoxSitePlugin).enablePlugins(ParadoxMaterialThemePlugin)
  .enablePlugins(BuildInfoPlugin).settings(
    name := "riddlc",
    mainClass := Some("com.yoppworks.ossum.riddl.RIDDL"),
    paradoxNavigationDepth := 6,
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
      ParadoxMaterialTheme().withColor("blue", "grey")
        .withLogoIcon("yw-elephant")
        .withCopyright("Copyright © 2019 Yoppworks Inc.").withSocial(
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
    buildInfoPackage := "com.yoppworks.ossum.riddl"
  ).dependsOn(translator).aggregate(language, translator)

lazy val language = (project in file("language")).settings(
  name := "riddl-languge",
  buildInfoPackage := "com.yoppworks.ossum.riddl.language",
  libraryDependencies ++= Seq(
    "org.typelevel" %% "cats-core" % "2.1.0",
    "com.lihaoyi" %% "fastparse" % "2.2.4",
    "com.github.pureconfig" %% "pureconfig" % "0.12.2",
    "org.scalactic" %% "scalactic" % "3.1.0",
    "org.scalatest" %% "scalatest" % "3.1.0" % "test",
    "org.scalacheck" %% "scalacheck" % "1.14.3" % "test"
  ),
  compilecheck in Compile := {
    Def.sequential(compile in Compile, (scalastyle in Compile).toTask("")).value
  }
)

lazy val translator = (project in file("translator")).settings(
  name := "riddl-translator",
  buildInfoPackage := "com.yoppworks.ossum.riddl.translator",
  libraryDependencies ++= Seq(
    "org.jfree" % "jfreesvg" % "3.4",
    "org.scalactic" %% "scalactic" % "3.1.0",
    "org.scalatest" %% "scalatest" % "3.1.0" % "test",
    "org.scalacheck" %% "scalacheck" % "1.14.3" % "test",
    "com.github.pureconfig" %% "pureconfig" % "0.12.2"
  )
).dependsOn(language % "test->test;compile->compile")

/*
lazy val `sbt-riddl` = (project in file("sbt-riddl")).settings(
  name := "sbt-riddl",
  sbtPlugin := true,
  scalaVersion := "2.12.10",
  buildInfoPackage := "com.yoppworks.ossum.riddl.sbt.plugin"
).enablePlugins(SbtPlugin)
//  .enablePlugins(ParadoxPlugin)
  .dependsOn(translator)
 */
