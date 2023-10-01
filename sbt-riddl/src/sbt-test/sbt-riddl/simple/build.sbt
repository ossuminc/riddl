import scala.sys.process.Process

lazy val root = (project in file("."))
  .enablePlugins(RiddlSbtPlugin)
  .settings(
    version := "0.1",
    riddlcOptions := Seq("--show-times", "--verbose"),
    riddlcConf := file("src/main/riddl/riddlc.conf")
  )
