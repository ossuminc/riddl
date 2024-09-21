/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.language.parsing.RiddlParserInput.*
import com.ossuminc.riddl.language.{AST, CommonOptions}
import com.ossuminc.riddl.utils.{TestingBasisWithTestData, URL}
import fastparse.*

import java.nio.file.{Files, Path}
import scala.annotation.unused
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.reflect.*

/** A helper class for testing the parser */
trait ParsingTest extends NoJVMParsingTest {

  def parsePath(
    path: Path,
    commonOptions: CommonOptions = CommonOptions.empty
  ): Either[Messages, Root] = {
    if Files.exists(path) then
      if Files.isReadable(path) then {
        val input = RiddlParserInput.fromCwdPath(path)
        TopLevelParser.parseInput(input, commonOptions)
      } else {
        val message: Message = error(s"Input file `${path.toString} is not readable.")
        Left(List(message))
      }
      end if
    else {
      val message: Message = error(s"Input file `${path.toString} does not exist.")
      Left(List(message))
    }
    end if
  }

  def parseFile(
    file: java.io.File,
    commonOptions: CommonOptions = CommonOptions.empty
  ): Either[Messages, Root] = {
    parsePath(file.toPath, commonOptions)
  }

  def parseRoot(path: java.nio.file.Path): Either[Messages, Root] = {
    val rpi = RiddlParserInput.fromCwdPath(path)
    parseTopLevelDomains(rpi)
  }

  val defaultInputDir = "language/jvm/src/test/input"

  def checkFile(
    @unused label: String,
    fileName: String,
    directory: String = defaultInputDir
  ): (Root, RiddlParserInput) = {
    val path = java.nio.file.Path.of(directory, fileName)
    val rpi = fromCwdPath(path)
    TopLevelParser.parseInput(rpi) match {
      case Left(errors) =>
        fail(errors.format)
      case Right(root) => root -> rpi
    }
  }

}
