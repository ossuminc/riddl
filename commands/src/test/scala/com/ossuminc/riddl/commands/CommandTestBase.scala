package com.ossuminc.riddl.commands

import com.ossuminc.riddl.command.{Command,CommandOptions}
import com.ossuminc.riddl.utils.SysLogger

import org.scalatest.Assertion
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.nio.file.Path

/** Unit Tests For CommandTestBase */
trait CommandTestBase(val inputDir: String = "command/src/test/input/") extends AnyWordSpec with Matchers {

  val confFile = s"$inputDir/cmdoptions.conf"

  val quiet = "--quiet"
  val suppressMissing = "--suppress-missing-warnings"
  val suppressStyle = "--suppress-style-warnings"
  val common: Seq[String] = Seq(quiet, suppressMissing, suppressStyle)

  def runCommand(
    args: Seq[String] = Seq.empty[String]
  ): Assertion = {
    Commands.runMainForTest(args.toArray) match
      case Left(messages) => fail(messages.justErrors.format)
      case Right(passesResult) => succeed
    end match
  }

  def check[OPTS <: CommandOptions](
    cmd: Command[OPTS],
    expected: OPTS,
    file: Path = Path.of(confFile)
  )(checker: (opts: OPTS) => Assertion = { (opts: OPTS) => opts.check must be(empty) }): Assertion = {
    cmd.loadOptionsFrom(file, SysLogger()) match {
      case Left(errors) =>
        fail(errors.format)
      case Right(options: OPTS) =>
        checker(options)
        options must be(expected)
    }
  }

}
