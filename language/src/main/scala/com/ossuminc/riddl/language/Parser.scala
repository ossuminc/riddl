/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language
import com.ossuminc.riddl.language.AST.RootContainer
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.language.parsing.{FileParserInput, RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.utils.Timer

import java.nio.file.{Files, Path}

object Parser {
  def parse(
    path: Path,
    options: CommonOptions
  ): Either[Messages, RootContainer] = {
    if Files.exists(path) then {
      val input = new FileParserInput(path)
      parse(input, options)
    } else {
      Left(
        List(Messages.error(s"Input file `${path.toString} does not exist."))
      )
    }
  }
  def parse(
    input: RiddlParserInput,
    options: CommonOptions = CommonOptions.empty
  ): Either[Messages, RootContainer] = {
    Timer.time("parse", options.showTimes) {
      TopLevelParser.parse(input)
    }
  }
}
