/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands

import com.ossuminc.riddl.utils.PlatformContext
import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.command.{Command, CommandOptions}
import scopt.OParser

object CommandLoader:
  /** Convert a string and some [[com.ossuminc.riddl.utils.CommonOptions]] into either a
   * [[com.ossuminc.riddl.command.Command]] or some [[com.ossuminc.riddl.language.Messages.Messages]] Note that the
   * [[com.ossuminc.riddl.command.CommandOptions]] will be passed to the command when you run it.
   *
   * @param name
   *   The name of the command to be converted
   * @return
   */
  def loadCommandNamed(name: String)(using io: PlatformContext): Either[Messages, Command[?]] =
    if io.options.verbose then io.log.info(s"Loading command: $name") else ()
    name match
      case "about"    => Right(AboutCommand())
      case "dump"     => Right(DumpCommand())
      case "flatten"  => Right(FlattenCommand())
      case "from"     => Right(FromCommand())
      case "hugo"     => Right(HugoCommand())
      case "help"     => Right(HelpCommand())
      case "info"     => Right(InfoCommand())
      case "onchange" => Right(OnChangeCommand())
      case "parse"    => Right(ParseCommand())
      case "prettify" => Right(PrettifyCommand())
      case "repeat"   => Right(RepeatCommand())
      case "stats"    => Right(StatsCommand())
      case "validate" => Right(ValidateCommand())
      case "version"  => Right(VersionCommand())
      case _          => Left(errors(s"No command found for '$name'"))
    end match
  end loadCommandNamed

  def commandOptionsParser(using io: PlatformContext): OParser[Unit, ?] = {
    val optionParsers = Seq(
      AboutCommand().getOptionsParser._1.asInstanceOf[OParser[Unit, CommandOptions]],
      DumpCommand().getOptionsParser._1.asInstanceOf[OParser[Unit, CommandOptions]],
      FlattenCommand().getOptionsParser._1.asInstanceOf[OParser[Unit, CommandOptions]],
      FromCommand().getOptionsParser._1.asInstanceOf[OParser[Unit, CommandOptions]],
      HelpCommand().getOptionsParser._1.asInstanceOf[OParser[Unit, CommandOptions]],
      HugoCommand().getOptionsParser._1.asInstanceOf[OParser[Unit, CommandOptions]],
      InfoCommand().getOptionsParser._1.asInstanceOf[OParser[Unit, CommandOptions]],
      OnChangeCommand().getOptionsParser._1.asInstanceOf[OParser[Unit, CommandOptions]],
      ParseCommand().getOptionsParser._1.asInstanceOf[OParser[Unit, CommandOptions]],
      PrettifyCommand().getOptionsParser._1.asInstanceOf[OParser[Unit, CommandOptions]],
      RepeatCommand().getOptionsParser._1.asInstanceOf[OParser[Unit, CommandOptions]],
      StatsCommand().getOptionsParser._1.asInstanceOf[OParser[Unit, CommandOptions]],
      ValidateCommand().getOptionsParser._1.asInstanceOf[OParser[Unit, CommandOptions]],
      VersionCommand().getOptionsParser._1.asInstanceOf[OParser[Unit, CommandOptions]]
    )
    OParser.sequence[Unit, CommandOptions](optionParsers.head, optionParsers.tail*)
  }

end CommandLoader

