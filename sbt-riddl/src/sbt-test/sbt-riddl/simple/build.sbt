import scala.sys.process.Process

lazy val scalacheck = "1.18.1" // NOTE: Synchronize with Dependencies.V.scalacheck
lazy val scalatest = "3.2.19" // NOTE: Synchronize with Depenendencies.V.scalatest
lazy val riddl = sys.props.get("plugin.version").getOrElse("0.56.0")

lazy val root = (project in file("."))
  .enablePlugins(RiddlSbtPlugin)
  .settings(
    version := "0.1",
    scalaVersion := "3.7.4",
    riddlcOptions := Seq("--show-times", "--verbose"),
    riddlcConf := file("src/main/riddl/riddlc.conf"),
    libraryDependencies ++= Seq(
      "com.ossuminc" %% "riddl-commands" % riddl % Test,
      "com.ossuminc" %% "riddl-commands" % riddl % Test,
      "org.scalactic" %% "scalactic" % scalatest % Test,
      "org.scalatest" %% "scalatest" % scalatest % Test
    )
  )
