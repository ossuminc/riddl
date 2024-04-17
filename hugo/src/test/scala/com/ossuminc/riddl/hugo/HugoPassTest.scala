/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.hugo

import com.ossuminc.riddl.passes.PassesResult
import com.ossuminc.riddl.testkit.RunCommandOnExamplesTest
import org.scalatest.Assertion

import java.nio.file.Files
import java.nio.file.Path
import scala.collection.mutable.ArrayBuffer

class HugoPassTest
    extends RunCommandOnExamplesTest[HugoCommand.Options, HugoCommand](
      commandName = "hugo"
    ) {

  "HugoTranslator" should {
    "handle all example sources" in {
      runTests()
    }
  }

  val passing_test_cases: Seq[String] = Seq(
    "ToDoodles",
    "FooBarSuccess/FooBar",
    "dokn"
  )
  override def validateTestName(name: String): Boolean =
    val result = passing_test_cases.exists(name.endsWith)
    result

  override def onSuccess(
    commandName: String,
    name: String,
    configFile: Path,
    passesResult: PassesResult,
    outputDir: Path
  ): Assertion = {
    if commandName == "hugo" then {
      if !passesResult.messages.hasErrors then runHugo(outputDir, tmpDir)
      else fail(passesResult.messages.format)
    } else fail("wrong command!")
  }

  def runHugo(outputDir: Path, tmpDir: Path): Assertion = {
    import scala.sys.process.*
    val output = ArrayBuffer[String]()
    var hadErrorOutput: Boolean = output.nonEmpty

    def fout(line: String): Unit = { output.append(line) }

    def ferr(line: String): Unit = {
      output.append(line); hadErrorOutput = true
    }

    val logger = ProcessLogger(fout, ferr)
    if !Files.exists(outputDir) then { Files.createDirectories(outputDir) }
    require(Files.isDirectory(outputDir))
    val cwdFile = outputDir.toFile
    val command = "hugo"
    println(s"Running hugo with cwd=$cwdFile, tmpDir=$tmpDir")
    val proc = Process(command, cwd = Option(cwdFile))
    proc.!<(logger) match {
      case 0 =>
        if hadErrorOutput then {
          fail("hugo wrote to stderr:\n  " + output.mkString("\n  "))
        } else { info("hugo issued warnings:\n  " + output.mkString("\n  ")) }
        succeed

      case rc: Int =>
        fail(
          s"hugo run failed with rc=$rc:\n  " ++
            output.mkString("\n ", "\n  ", "\n") ++
            s"tmpDir=$tmpDir\ncwd=$cwdFile\ncommand=$command\n"
        )
    }
  }
}
