package com.reactific.riddl.commands
import com.reactific.riddl.language.Messages
import com.reactific.riddl.language.Messages.Messages

import java.nio.file.Path

/**
 * Base class for command options. Every command should extend this to a case
 * class
 */
trait CommandOptions {
  def command: Command
  def inputFile: Option[Path]

  def withInputFile(f: Path => Either[Messages,Unit]): Either[Messages, Unit] = {
    inputFile match {
      case Some(inputFile) =>
        f(inputFile)
      case None =>
        Left(List(
          Messages.error("No input file specified for prettify")
        ))
    }
  }
}

object CommandOptions {
  val empty: CommandOptions = new CommandOptions {
    def command: Command = Unspecified
    def inputFile: Option[Path] = Option.empty[Path]
  }
}
