/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.command

import com.ossuminc.riddl.language.Messages.{Messages, errors}
import com.ossuminc.riddl.language.Messages
import scopt.OParser

import java.nio.file.Path

/** Base class for command options. Every command should extend this to a case class
  */
trait CommandOptions:
  def command: String

  def inputFile: Option[Path]

  def withInputFile[S](
    f: Path => Either[Messages, S]
  ): Either[Messages, S] = {
    CommandOptions.withInputFile(inputFile, command)(f)
  }

  def check: Messages = {
    if inputFile.isEmpty then {
      Messages.errors("An input path was not provided.")
    } else {
      Messages.empty
    }
  }
end CommandOptions

object CommandOptions:

  def withInputFile[S](
    inputFile: Option[Path],
    commandName: String
  )(
    f: Path => Either[Messages, S]
  ): Either[Messages, S] = {
    inputFile match {
      case Some(inputFile) => f(inputFile)
      case None =>
        Left(List(Messages.error(s"No input file specified for $commandName")))
    }
  }

  val empty: CommandOptions = new CommandOptions {
    def command: String = "unspecified"
    def inputFile: Option[Path] = Option.empty[Path]
  }
end CommandOptions
