import sbt.*
import sbt.librarymanagement.ModuleID

/** V - Dependency Versions object */

object V {
  val commons_io = "2.16.1"
  val compress = "1.27.0"
  val config = "1.4.2"
  val fastparse = "3.1.1"
  val jgit = "6.5.0"
  val lang3 = "3.15.0"
  val pureconfig = "0.17.7"
  val scalacheck = "1.18.0"
  val scalatest = "3.2.19"
  val scopt = "4.1.0"
  val slf4j = "2.0.4"
}

object Dep {
  val compress = "org.apache.commons" % "commons-compress" % V.compress
  val commons_io = "commons-io" % "commons-io" % V.commons_io
  val fastparse = "com.lihaoyi" %% "fastparse" % V.fastparse
  val jgit = "org.eclipse.jgit" % "org.eclipse.jgit" % V.jgit
  val lang3 = "org.apache.commons" % "commons-lang3" % V.lang3
  val pureconfig = "com.github.pureconfig" %% "pureconfig-core" % V.pureconfig
  val scalactic = "org.scalactic" %% "scalactic" % V.scalatest
  val scalatest = "org.scalatest" %% "scalatest" % V.scalatest
  val scalacheck = "org.scalacheck" %% "scalacheck" % V.scalacheck
  val scopt = "com.github.scopt" %% "scopt" % V.scopt
  val slf4j = "org.slf4j" % "slf4j-nop" % V.slf4j
  val jacabi_w3c = "com.jcabi" % "jcabi-w3c" % "1.4.0"

  val testing: Seq[ModuleID] = Seq(scalactic % "test", scalatest % "test", scalacheck % "test")
  val testKitDeps: Seq[ModuleID] = Seq(scalactic, scalatest, scalacheck)

}
