import sbt._

import scoverage.ScoverageKeys.coverageEnabled
import scoverage.ScoverageKeys.coverageFailOnMinimum
import scoverage.ScoverageKeys.coverageMinimumBranchTotal
import scoverage.ScoverageKeys.coverageMinimumStmtTotal

/** V - Dependency Versions object */
object V {
  val cats = "2.7.0"
  val config = "1.4.1"
  val fastparse = "2.3.3"
  val pureconfig = "0.17.1"
  val scalacheck = "1.15.4"
  val scalatest = "3.2.9"
  val scopt = "4.0.1"
}

object Dep {
  val cats_core = "org.typelevel" %% "cats-core" % V.cats
  val config = "com.typesafe" % "config" % V.config
  val fastparse = "com.lihaoyi" %% "fastparse" % V.fastparse
  val pureconfig = "com.github.pureconfig" %% "pureconfig" % V.pureconfig
  val scalactic = "org.scalactic" %% "scalactic" % V.scalatest % "test"
  val scalatest = "org.scalatest" %% "scalatest" % V.scalatest % "test"
  val scalacheck = "org.scalacheck" %% "scalacheck" % V.scalacheck % "test"
  val scopt = "com.github.scopt" %% "scopt" % V.scopt

  val parsing = Seq(fastparse, pureconfig)
  val testing = Seq(scalactic, scalatest, scalacheck)

}

object C {
  def withCoverage(p: Project): Project = {
    p.settings(
      coverageEnabled := true,
      coverageFailOnMinimum := true,
      coverageMinimumStmtTotal := 80,
      coverageMinimumBranchTotal := 80
    )
  }
}
