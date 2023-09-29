import scala.sys.process.Process

lazy val root = (project in file("."))
  .enablePlugins(RiddlSbtPlugin)
  .settings(
    version := "0.1",
    scalaVersion := "2.12.18",

    riddlcOptions := Seq.empty[String],
    riddlcPath := file("/Users/reid/Code/reactific/riddl/riddlc/target/universal/stage/bin/riddlc"),
    TaskKey[String]("checkInfoOutput") := {
      val which_cmd = Process("/bin/zsh", Seq("-c", "which riddlc"))
      val riddlc_path: String = {
        val which_out = (which_cmd.lineStream_!).toSeq.mkString.trim
        if (!which_out.startsWith("/")) {
          "/Users/reid/Code/reactific/riddl/riddlc/target/universal/stage/bin/riddlc"
        } else {
          which_out
        }
      }
      println(s"RIDDLC path is: $riddlc_path")
      if (!riddlc_path.contains("riddlc")) {
        sys.error(s"which command didn't return riddlc path but:\n$riddlc_path")
      }
      val args = Seq("--verbose", "info")
      val p1 = Process(riddlc_path, args)
      val out1 = (p1 !!).trim
      println(out1)
      if (!out1.contains("name: riddlc")) {
        sys.error(s"wrong output from 'riddlc info' command:\n$out1")
      }
      val plugin_version = sys.props.get("plugin.version").getOrElse("0")
      if (!out1.contains(s"version: ")) {
        sys.error(s"output should contain 'version: $plugin_version")
      }
      val v = Seq(
        "--verbose",
        "--suppress-missing-warnings",
        "from",
        "src/main/riddl/riddlc.conf",
        "validate"
      )
      val p2 = Process(riddlc_path, v)
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
      riddlc_path
    }
  )
