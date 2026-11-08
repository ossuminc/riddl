import sbt.*
import sbt.librarymanagement.ModuleID
import sbt.Keys.libraryDependencies

/** V - Dependency Versions object */

object V {
  val airframe_json = "2026.2.5"
  val airframe_ulid = "2026.2.5"
  val commons_io = "2.22.0"
  val compress = "1.28.0"
  val dom = "2.8.1"
  val fastparse = "3.1.1"
  val lang3 = "3.21.0"
  val previous = "2.6.0"
  val scala = "3.9.0"
  val scala_java_time = "2.9.0"
  val scalacheck = "1.20.0"
  val scalatest = "3.2.20"
  val sconfig = "1.12.4"
  val scopt = "4.2.0"
  val sttp = "4.1.0"
  val upickle = "4.4.3"
}

object Dep {
  val airframe_ulid = "org.wvlet.airframe" %% "airframe-ulid" % V.airframe_ulid
  val airframe_ulid_nojvm = Def.setting {
    "org.wvlet.airframe" %% "airframe-ulid" % V.airframe_ulid
  }

  // airframe-json pulls airframe-log, which declares logback-core -- dual-licensed
  // EPL-1.0 / LGPL-2.1, the only non-permissive license that reached the
  // distribution, and used by nothing: no riddl source references logback or
  // slf4j. airframe-log's default backend is java.util.logging and it only binds
  // logback when the classes are present, so removing them costs nothing and drops
  // an LGPL obligation from every artifact we ship.
  val airframe_json = ("org.wvlet.airframe" %% "airframe-json" % V.airframe_json)
    .exclude("ch.qos.logback", "logback-core")
  val compress = "org.apache.commons" % "commons-compress" % V.compress
  val commons_io = "commons-io" % "commons-io" % V.commons_io
  val fastparse = "com.lihaoyi" %% "fastparse" % V.fastparse
  val fastparse_nojvm = Def.setting { "com.lihaoyi" %% "fastparse" % V.fastparse }
  val upickle = "com.lihaoyi" %% "upickle" % V.upickle
  val upickle_nojvm = Def.setting { "com.lihaoyi" %% "upickle" % V.upickle }
  val lang3 = "org.apache.commons" % "commons-lang3" % V.lang3

  val scalactic = "org.scalactic" %% "scalactic" % V.scalatest
  val scalactic_nojvm = Def.setting { "org.scalactic" %% "scalactic" % V.scalatest }

  val scalatest = "org.scalatest" %% "scalatest" % V.scalatest
  val scalatest_nojvm = Def.setting { "org.scalatest" %% "scalatest" % V.scalatest }

  val scalacheck = "org.scalacheck" %% "scalacheck" % V.scalacheck
  val scalacheck_nojvm = Def.setting { "org.scalacheck" %% "scalacheck" % V.scalacheck }

  val scalajs_stubs = "org.scala-js" %% "scalajs-stubs" % "1.1.0" % "provided"

  val sconfig = "org.ekrich" %% "sconfig" % V.sconfig
  val sconfig_nojvm = Def.setting { "org.ekrich" %% "sconfig" % V.sconfig }

  val scopt = "com.github.scopt" %% "scopt" % V.scopt
  val scopt_nojvm = Def.setting { "com.github.scopt" %% "scopt" % V.scopt }

  val sttp_nojvm = Def.setting { "com.softwaremill.sttp.client4" %% "core" % V.sttp }

  val testing: Seq[ModuleID] = Seq(scalactic % Test, scalatest % Test, scalacheck % Test)
  val testKitDeps: Seq[ModuleID] = Seq(scalactic, scalatest, scalacheck)

  // JS dependencies
  val dom = Def.setting { "org.scala-js" %% "scalajs-dom" % V.dom }
  val scala_java_time = Def.setting {
    "io.github.cquiroz" %% "scala-java-time" % V.scala_java_time
  }

  // Native dependencies
  val java_net_url_stubs = Def.setting {
    "org.scala-native" %% "java-net-url-stubs" % "1.0.0"
  }

}
