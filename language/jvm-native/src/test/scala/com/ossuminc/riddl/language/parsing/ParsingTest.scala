/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST
import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.language.parsing.RiddlParserInput.*
import com.ossuminc.riddl.utils.{Await, CommonOptions, PathUtils, URL, ec, pc}
import fastparse.*

import java.nio.file.{Files, Path}
import scala.annotation.unused
import scala.concurrent.duration.DurationInt
import scala.reflect.*

/** A helper class for testing the parser */
trait ParsingTest extends AbstractParsingTest {

  def parsePath(
    path: Path
  ): Either[Messages, Root] = {
    if Files.exists(path) then
      if Files.isReadable(path) then {
        val url = PathUtils.urlFromCwdPath(path, "")
        val future = RiddlParserInput.fromURL(url, "").map { rpi => TopLevelParser.parseInput(rpi) }
        Await.result(future, 10.seconds)
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
    file: java.io.File
  ): Either[Messages, Root] = {
    parsePath(file.toPath)
  }

  def parseRoot(path: java.nio.file.Path): Either[Messages, Root] = {
    val url = PathUtils.urlFromCwdPath(path)
    val future = RiddlParserInput.fromURL(url).map { rpi => parseTopLevelDomains(rpi) }
    Await.result(future, 10.seconds)
  }

  val defaultInputDir = "language/jvm-native/src/test/input"

  def checkFile(
    @unused label: String,
    fileName: String,
    directory: String = defaultInputDir
  ): (Root, RiddlParserInput) = {
    val path = java.nio.file.Path.of(directory, fileName)
    val rul = PathUtils.urlFromCwdPath(path)
    val future = RiddlParserInput.fromURL(rul).map { rpi =>
      TopLevelParser.parseInput(rpi) match {
        case Left(errors) =>
          fail(errors.format)
        case Right(root) => root -> rpi
      }
    }
    Await.result(future, 10.seconds)
  }

}
