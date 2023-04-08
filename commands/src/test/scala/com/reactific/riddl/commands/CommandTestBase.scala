package com.reactific.riddl.commands

import org.scalatest.Assertion
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.nio.file.Path

/** Unit Tests For CommandTestBase */
trait CommandTestBase extends AnyWordSpec with Matchers{

  val inputDir = "commands/src/test/input/"
  val confFile = s"$inputDir/cmdoptions.conf"

  val quiet = "--quiet"
  val suppressMissing = "--suppress-missing-warnings"
  val suppressStyle = "--suppress-style-warnings"
  val common = Seq(quiet, suppressMissing, suppressStyle)

  def runCommand(
    args: Seq[String] = Seq.empty[String],
  ): Assertion = {
    val rc = CommandPlugin.runMain(args.toArray)
    rc mustBe 0
  }

  def check[OPTS <: CommandOptions](
    cmd: CommandPlugin[?],
    expected: OPTS,
    file: Path = Path.of(confFile)
  ): Assertion = {
    cmd.loadOptionsFrom(file) match {
      case Left(errors) => fail(errors.format)
      case Right(options) => options must be(expected)
    }
  }


}
