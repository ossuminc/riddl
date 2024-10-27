/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.command

import com.ossuminc.riddl.language.Messages.{Messages, errors}
import com.ossuminc.riddl.language.Messages
import pureconfig.{ConfigCursor, ConfigObjectCursor, ConfigReader}
import pureconfig.error.ConfigReaderFailures
import scopt.OParser

import java.nio.file.Path

/** Base class for command options. Every command should extend this to a case class
  */
trait CommandOptions {
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
}

object CommandOptions {

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

  /** A helper function for reading optional items from a config file.
    *
    * @param objCur
    *   The ConfigObjectCursor to start with
    * @param key
    *   The name of the optional config item
    * @param default
    *   The default value of the config item
    * @param mapIt
    *   The function to map ConfigCursor to ConfigReader.Result[T]
    * @tparam T
    *   The Scala type of the config item's value
    *
    * @return
    *   The reader for this optional configuration item.
    */
  def optional[T](
    objCur: ConfigObjectCursor,
    key: String,
    default: T
  )(mapIt: ConfigCursor => ConfigReader.Result[T]): ConfigReader.Result[T] = {
    objCur.atKeyOrUndefined(key) match {
      case stCur if stCur.isUndefined => Right[ConfigReaderFailures, T](default)
      case stCur                      => mapIt(stCur)
    }
  }
}
