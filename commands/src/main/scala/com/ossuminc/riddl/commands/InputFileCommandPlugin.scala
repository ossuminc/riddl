/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands
import pureconfig.ConfigCursor
import pureconfig.ConfigReader
import scopt.OParser

import java.io.File
import java.nio.file.Path

object InputFileCommandPlugin {
  case class  Options(inputFile: Option[Path] = None, command: String = "unspecified") extends CommandOptions
}

/** An abstract command definition helper class for commands that only take a single input file parameter
  * @param name
  *   The name of the command
  */
abstract class InputFileCommandPlugin(name: String) extends CommandPlugin[InputFileCommandPlugin.Options](name) {
  import InputFileCommandPlugin.Options
  def getOptions: (OParser[Unit, Options], Options) = {
    import builder.*
    cmd(name).children(
      arg[File]("input-file").action((f, opt) => opt.copy(command = name, inputFile = Some(f.toPath)))
    ) -> InputFileCommandPlugin.Options()
  }

  override def getConfigReader: ConfigReader[Options] = { (cur: ConfigCursor) =>
    {
      for
        topCur <- cur.asObjectCursor
        topRes <- topCur.atKey(name)
        objCur <- topRes.asObjectCursor
        inFileRes <- objCur.atKey("input-file").map(_.asString)
        inFile <- inFileRes
      yield { Options(command = name, inputFile = Some(Path.of(inFile))) }
    }
  }
}
