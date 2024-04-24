/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.testkit

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{AST, CommonOptions}
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.language.parsing.{TopLevelParser, RiddlParserInput}
import com.ossuminc.riddl.language.parsing.{ParsingTest => langParsingTest} 
import fastparse.{P, *}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.File
import java.nio.file.Path
import scala.annotation.unused
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.reflect.*

/** Bring the language.ParsingTest from test configuration into the same name in the testkit */
trait ParsingTest extends langParsingTest
