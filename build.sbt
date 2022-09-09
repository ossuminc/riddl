import com.jsuereth.sbtpgp.PgpKeys.pgpSigner

import sbtbuildinfo.BuildInfoOption.ToJson
import sbtbuildinfo.BuildInfoOption.ToMap
import sbtbuildinfo.BuildInfoOption.BuildTime
import java.util.Calendar

Global / onChangedBuildSource := ReloadOnSourceChanges
(Global / excludeLintKeys) ++=
  Set(buildInfoPackage, buildInfoKeys, buildInfoOptions, mainClass, maintainer)

maintainer := "reid@ossumin.com"

lazy val riddl = (project in file(".")).settings(
  publish := {},
  publishLocal := {},
  pgpSigner / skip := true,
  publishTo := Some(Resolver.defaultLocal)
).aggregate(
  utils,
  language,
  commands,
  testkit,
  prettify,
  hugo,
  `git-check`,
  examples,
  doc,
  riddlc,
  `sbt-riddl`
)

lazy val utils = project.in(file("utils")).configure(C.mavenPublish)
  .configure(C.withCoverage()).enablePlugins(BuildInfoPlugin).settings(
    name := "riddl-utils",
    coverageExcludedPackages := "<empty>",
    libraryDependencies ++= Seq(Dep.compress, Dep.lang3) ++ Dep.testing,
    buildInfoObject := "RiddlBuildInfo",
    buildInfoPackage := "com.reactific.riddl.utils",
    buildInfoOptions := Seq(ToMap, ToJson, BuildTime),
    buildInfoUsePackageAsPath := true,
    buildInfoKeys ++= Seq[BuildInfoKey](
      name,
      version,
      description,
      organization,
      organizationName,
      BuildInfoKey.map(organizationHomepage) { case (k, v) =>
        k -> v.get.toString
      },
      BuildInfoKey.map(homepage) { case (k, v) =>
        "projectHomepage" -> v.map(_.toString).getOrElse("https://riddl.tech")
      },
      BuildInfoKey.map(startYear) { case (k, v) =>
        k -> v.map(_.toString).getOrElse("2019")
      },
      BuildInfoKey.map(startYear) { case (k, v) =>
        "copyright" -> s"© ${v.map(_.toString).getOrElse("2019")}-${Calendar
          .getInstance().get(Calendar.YEAR)} Ossum Inc."
      },
      scalaVersion,
      sbtVersion,
      BuildInfoKey.map(scalaVersion) { case (k, v) =>
        "scalaCompatVersion" -> v.substring(0, v.lastIndexOf('.'))
      },
      BuildInfoKey.map(licenses) { case (k, v) =>
        k -> v.map(_._1).mkString(", ")
      }
    )
  )

lazy val language = project.in(file("language")).configure(C.withCoverage())
  .configure(C.mavenPublish).settings(
    name := "riddl-language",
    coverageExcludedPackages :=
      "<empty>;.*AST;.*BuildInfo;.*PredefinedType;.*Terminals.*",
    libraryDependencies ++= Seq(Dep.fastparse, Dep.lang3, Dep.commons_io) ++
      Dep.testing
  ).dependsOn(utils)

lazy val commands = project.in(file("commands")).configure(C.mavenPublish)
  .settings(
    name := "riddl-commands",
    libraryDependencies ++= Seq(Dep.scopt, Dep.pureconfig) ++ Dep.testing
  ).dependsOn(utils % "compile->compile;test->test", language)

lazy val testkit = project.in(file("testkit")).configure(C.mavenPublish)
  .settings(name := "riddl-testkit", libraryDependencies ++= Dep.testKitDeps)
  .dependsOn(commands)

lazy val prettify = project.in(file("prettify")).configure(C.mavenPublish)
  .settings(name := "riddl-prettify", libraryDependencies ++= Dep.testing)
  .dependsOn(commands, testkit % "test->compile").dependsOn(utils)

lazy val `hugo`: Project = project.in(file("hugo")).configure(C.mavenPublish)
  .settings(
    name := "riddl-hugo",
    Compile / unmanagedResourceDirectories += {
      baseDirectory.value / "resources"
    },
    Test / parallelExecution := false,
    libraryDependencies ++= Seq(Dep.pureconfig) ++ Dep.testing
  )
  .dependsOn(language % "compile->compile", commands, testkit % "test->compile")
  .dependsOn(utils)

lazy val `git-check`: Project = project.in(file("git-check"))
  .configure(C.mavenPublish).settings(
    name := "riddl-git-check",
    Compile / unmanagedResourceDirectories += {
      baseDirectory.value / "resources"
    },
    Test / parallelExecution := false,
    libraryDependencies ++= Seq(Dep.pureconfig, Dep.jgit) ++ Dep.testing
  ).dependsOn(commands, testkit % "test->compile")

lazy val examples = project.in(file("examples")).configure(C.withScalaCompile)
  .settings(
    name := "riddl-examples",
    Compile / packageBin / publishArtifact := false,
    Compile / packageDoc / publishArtifact := false,
    Compile / packageSrc / publishArtifact := false,
    publishTo := Option(Resolver.defaultLocal),
    libraryDependencies ++=
      Seq("org.scalatest" %% "scalatest" % "3.2.13" % "test")
  ).dependsOn(hugo % "test->test", riddlc)

lazy val doc = project.in(file("doc"))
  .enablePlugins(SitePlugin, SiteScaladocPlugin, ScalaUnidocPlugin)
  .configure(C.zipResource("hugo")).configure(C.withScalaCompile).settings(
    name := "riddl-doc",
    publishTo := Option(Resolver.defaultLocal),
    maintainer := "reid@ossuminc.com",
  // Hugo / sourceDirectory := sourceDirectory.value / "hugo",
    // siteSubdirName / ScalaUnidoc := "api",
    // (mappings / (
    //   ScalaUnidoc, packageDoc), siteSubdirName in ScalaUnidoc
    // ),
    publishSite
  ).dependsOn(hugo % "test->test", riddlc)

lazy val riddlc: Project = project.in(file("riddlc"))
  .enablePlugins(JavaAppPackaging, UniversalDeployPlugin)
  .enablePlugins(MiniDependencyTreePlugin).configure(C.mavenPublish)
  .configure(C.withCoverage()).dependsOn(
    utils % "compile->compile;test->test",
    commands,
    language,
    hugo,
    `git-check`,
    testkit % "test->compile"
  ).settings(
    name := "riddlc",
    mainClass := Option("com.reactific.riddl.RIDDLC"),
    libraryDependencies ++= Seq(Dep.pureconfig) ++ Dep.testing
  )

lazy val `sbt-riddl` = (project in file("sbt-riddl"))
  .enablePlugins(SbtPlugin, BuildInfoPlugin)
  .configure(C.mavenPublish).settings(
    name := "sbt-riddl",
    sbtPlugin := true,
    scalaVersion := "2.12.16",
    buildInfoObject := "SbtRiddlPluginBuildInfo",
    buildInfoPackage := "com.reactific.riddl.sbt",
    buildInfoOptions := Seq(BuildTime),
    buildInfoUsePackageAsPath := true,
    scriptedLaunchOpts := {
      scriptedLaunchOpts.value ++
        Seq("-Xmx1024M", "-Dplugin.version=" + version.value)
    },
    scriptedBufferLog := false
  )
