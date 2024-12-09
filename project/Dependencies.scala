import sbt.*
import sbt.librarymanagement.ModuleID
import org.portablescala.sbtplatformdeps.PlatformDepsPlugin.autoImport.*

/** V - Dependency Versions object */

object V {
  val airframe_json = "24.9.2"
  val airframe_ulid = "24.9.3"
  val commons_io = "2.18.0"
  val compress = "1.27.1"
  val config = "1.4.2"
  val fastparse = "3.1.1"
  val jgit = "6.5.0"
  val lang3 = "3.17.0"
  val scalacheck = "1.18.1"
  val scalatest = "3.2.19"
  val sconfig = "1.8.1"
  val scopt = "4.1.0"
  val slf4j = "2.0.4"
}

object Dep {
  val compress = "org.apache.commons" % "commons-compress" % V.compress
  val commons_io = "commons-io" % "commons-io" % V.commons_io
  val fastparse = "com.lihaoyi" %% "fastparse" % V.fastparse
  val jacabi_w3c = "com.jcabi" % "jcabi-w3c" % "1.4.0"
  val jgit = "org.eclipse.jgit" % "org.eclipse.jgit" % V.jgit
  val lang3 = "org.apache.commons" % "commons-lang3" % V.lang3
  val sconfig = "org.ekrich" %% "sconfig" % V.sconfig
  val scalactic = "org.scalactic" %% "scalactic" % V.scalatest
  val scalatest = "org.scalatest" %% "scalatest" % V.scalatest
  val scalacheck = "org.scalacheck" %% "scalacheck" % V.scalacheck
  val scopt = "com.github.scopt" %% "scopt" % V.scopt
  val slf4j = "org.slf4j" % "slf4j-nop" % V.slf4j

  val testing: Seq[ModuleID] = Seq(scalactic % "test", scalatest % "test", scalacheck % "test")
  val testKitDeps: Seq[ModuleID] = Seq(scalactic, scalatest, scalacheck)

}
