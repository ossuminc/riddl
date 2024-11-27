/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.utils.PlatformContext
import org.scalatest.TestData

abstract class NebulaTest(using PlatformContext) extends AbstractParsingTest {

  "Module" should {
    "be accepted at root scope" in { (td: TestData) =>
      val input = RiddlParserInput(
        """
          | domain blah is { ??? }
          | entity foo is { ??? }
          |""".stripMargin, td
      )
      parseNebula(input) match
        case Left(messages) => fail(messages.justErrors.format)
        case Right(root) => succeed
    }
  }

}

