/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands

import com.ossuminc.riddl.language.CommonOptions
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.passes.PassesResult
import com.ossuminc.riddl.utils.Logger
import pureconfig.ConfigCursor
import pureconfig.ConfigReader
import scopt.OParser

import java.nio.file.Path

object ASimpleTestCommand {
  case class Options(
    command: String = "test",
    arg1: String = "")
      extends CommandOptions {
    override def inputFile: Option[Path] = None
  }
}

/** A pluggable command for testing plugin commands! */
class ASimpleTestCommand
    extends CommandPlugin[ASimpleTestCommand.Options]("test") {
  import ASimpleTestCommand.Options
  override def getOptions: (OParser[Unit, Options], Options) = {
    val builder = OParser.builder[Options]
    import builder.*
    OParser.sequence(cmd("test").children(
      arg[String]("arg1").action((s, to) => to.copy(arg1 = s)).validate { a1 =>
        if a1.nonEmpty then { Right(()) }
        else { Left("All argument keys must be nonempty") }
      }
    )) -> Options()
  }

  override def getConfigReader: ConfigReader[Options] = { (cur: ConfigCursor) =>
    for
      objCur <- cur.asObjectCursor
      contentCur <- objCur.atKey("test")
      contentObjCur <- contentCur.asObjectCursor
      arg1Res <- contentObjCur.atKey("arg1")
      str <- arg1Res.asString
    yield { Options(arg1 = str) }
  }

  override def run(
    options: Options,
    commonOptions: CommonOptions,
    log: Logger,
    outputDirOverride: Option[Path]
  ): Either[Messages, PassesResult] = {
    println(s"arg1: '${options.arg1}''")
    Right(PassesResult())
  }
}
