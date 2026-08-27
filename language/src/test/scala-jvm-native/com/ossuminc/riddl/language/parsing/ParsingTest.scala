/*
 * Copyright 2019-2026 Ossum Inc.
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
import scala.util.control.NonFatal
import scala.concurrent.duration.DurationInt
import scala.reflect.*

/** A helper class for testing the parser */
trait ParsingTest extends AbstractParsingTest {

  def parsePath(
    path: Path
  ): Either[Messages, Root] = {
    if !Files.exists(path) then Left(List(error(s"Input file `$path` does not exist.")))
    else if Files.isDirectory(path) then
      Left(List(error(s"`$path` is a directory, not a RIDDL input file.")))
    else if !Files.isReadable(path) then Left(List(error(s"Input file `$path` is not readable.")))
    else
      // An ABSOLUTE path cannot go through urlFromCwdPath: URL.fromCwdPath requires a relative one
      // and throws otherwise, so any test parsing a temp file failed here before reaching the
      // parser.
      val url =
        if path.isAbsolute then URL.fromFullPath(path.toString)
        else PathUtils.urlFromCwdPath(path, "")
      try
        val future = RiddlParserInput.fromURL(url, "").map { rpi => TopLevelParser.parseInput(rpi) }
        Await.result(future, 10.seconds)
      catch
        // Unreadable content — a binary file, a bad encoding — is an ordinary bad input, not a
        // crash. Report it as a message like any other so a caller can handle it uniformly.
        case NonFatal(x) =>
          Left(List(error(s"Could not read `$path`: ${x.getClass.getSimpleName}: ${x.getMessage}")))
      end try
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

  val defaultInputDir = "language/input"

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
