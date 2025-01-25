import sbt.*
import sbt.librarymanagement.ModuleID
import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport.*
import sbt.Keys.libraryDependencies

/** V - Dependency Versions object */

object V {
  val airframe_json = "24.9.2"
  val airframe_ulid = "24.12.2"
  val commons_io = "2.18.0"
  val compress = "1.27.1"
  val config = "1.4.2"
  val dom = "2.8.0"
  val fastparse = "3.1.1"
  val jgit = "6.5.0"
  val lang3 = "3.17.0"
  val scala_java_time = "2.6.0"
  val scalacheck = "1.18.1"
  val scalatest = "3.2.19"
  val sconfig = "1.8.1"
  val scopt = "4.1.0"
  val sttp = "4.0.0-M22"
  val slf4j = "2.0.4"
}

object Dep {
  val airframe_ulid = "org.wvlet.airframe" %% "airframe-ulid" % V.airframe_ulid
  val airframe_ulid_nojvm = Def.setting {
    "org.wvlet.airframe" %%% "airframe-ulid" % V.airframe_ulid
  }

  val airframe_json = "org.wvlet.airframe" %% "airframe-json" % V.airframe_json
  val compress = "org.apache.commons" % "commons-compress" % V.compress
  val commons_io = "commons-io" % "commons-io" % V.commons_io
  val fastparse = "com.lihaoyi" %% "fastparse" % V.fastparse
  val fastparse_nojvm = Def.setting { "com.lihaoyi" %%% "fastparse" % V.fastparse }
  val jacabi_w3c = "com.jcabi" % "jcabi-w3c" % "1.4.0"
  val jgit = "org.eclipse.jgit" % "org.eclipse.jgit" % V.jgit
  val lang3 = "org.apache.commons" % "commons-lang3" % V.lang3

  val scalactic = "org.scalactic" %% "scalactic" % V.scalatest
  val scalactic_nojvm = Def.setting { "org.scalactic" %%% "scalactic" % V.scalatest }

  val scalatest = "org.scalatest" %% "scalatest" % V.scalatest
  val scalatest_nojvm = Def.setting { "org.scalatest" %%% "scalatest" % V.scalatest }

  val scalacheck = "org.scalacheck" %% "scalacheck" % V.scalacheck
  val scalacheck_nojvm = Def.setting { "org.scalacheck" %%% "scalacheck" % V.scalacheck }

  val scalajs_stubs = "org.scala-js" %% "scalajs-stubs" % "1.1.0" % "provided"

  val sconfig = "org.ekrich" %% "sconfig" % V.sconfig
  val sconfig_nojvm = Def.setting { "org.ekrich" %%% "sconfig" % V.sconfig }

  val scopt = "com.github.scopt" %% "scopt" % V.scopt
  val scopt_nojvm = Def.setting { "com.github.scopt" %%% "scopt" % V.scopt }

  val sttp = "com.softwaremill.sttp.client3" %% "core" % V.sttp
  val sttp_nojvm = Def.setting { "com.softwaremill.sttp.client4" %%% "core" % V.sttp }

  val slf4j = "org.slf4j" % "slf4j-nop" % V.slf4j

  val testing: Seq[ModuleID] = Seq(scalactic % Test, scalatest % Test, scalacheck % Test)
  val testKitDeps: Seq[ModuleID] = Seq(scalactic, scalatest, scalacheck)

  // JS dependencies
  val dom = Def.setting { "org.scala-js" %%% "scalajs-dom" % V.dom }
  val scala_java_time = Def.setting {
    "io.github.cquiroz" %%% "scala-java-time" % V.scala_java_time
  }

  // Native dependencies
  val java_net_url_stubs = Def.setting {
    "org.scala-native" %%% "java-net-url-stubs" % "1.0.0"
  }

}
