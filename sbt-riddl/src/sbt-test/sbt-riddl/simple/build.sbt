import scala.sys.process.Process

lazy val root = (project in file(".")).enablePlugins(RiddlSbtPlugin).settings(
  version := "0.1",
  scalaVersion := "2.12.6",
  TaskKey[Unit]("checkInfoOutput") := {
    val i = Seq("--verbose", "info")
    val p1 = Process("riddlc", i)
    val out1 = (p1 !!).trim
    println(out1)
    if (!out1.contains("name: riddlc")) {
      sys.error("output should contain 'name: riddlc'")
    }
    val plugin_version = sys.props.get("plugin.version").getOrElse("0")
    if (!out1.contains(s"version: $plugin_version")) {
      sys.error(s"output should contain 'version: $plugin_version")
    }
    val v = Seq(
      "--verbose",
      "--suppress-missing-warnings",
      "from",
      "src/main/riddl/riddlc.conf",
      "validate"
    )
    val p2 = Process("riddlc", v)
    val out2 = (p2 !!).trim
    println(out2)
    if (
      !out2
        .contains("Ran: from src/main/riddl/riddlc.conf validate: success=yes")
    ) {
      sys.error(
        "riddlc output did not contain 'Ran: from riddlc.conf validate: success=yes'"
      )
    }
    ()
  }
)
