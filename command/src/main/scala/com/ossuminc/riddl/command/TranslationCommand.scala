/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.command

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.passes.translate.TranslatingOptions
import com.ossuminc.riddl.utils.{PlatformContext, OutputFile, Timer}

import java.nio.file.Path
import scala.collection.mutable
import scala.reflect.ClassTag


object TranslationCommand {
  trait Options extends TranslatingOptions with PassCommandOptions {
    def inputFile: Option[Path]

    def outputDir: Option[Path]

    def projectName: Option[String]
  }
}

/** An abstract base class for translation style commands. That is, they translate an input file into an output
  * directory of files.
  *
  * @param name
  *   The name of the command to pass to [[Command]]
  * @tparam OPT
  *   The option type for the command
  */
abstract class TranslationCommand[OPT <: TranslationCommand.Options: ClassTag](name: String)(using io: PlatformContext)
    extends PassCommand[OPT](name) {}
