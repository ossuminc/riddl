/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.utils.{Await, PathUtils, PlatformContext, URL, ec, pc}
import org.scalatest.{Assertion, TestData}

import java.io.FileNotFoundException
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

/** Unit Tests For Includes */
class LoadingURLTests extends ParsingTest {

  "Include" should {
    "handle non existent URL" in { (td: TestData) =>
      val nonExistentURL =
        "https://raw.githubusercontent.com/ossuminc/riddl/main/testkit/src/test/input/domains/simpleDomain2.riddl"
      intercept[java.io.FileNotFoundException] {
        val future = RiddlParserInput.fromURL(URL(nonExistentURL), td).map { (rpi: RiddlParserInput) =>
          parseDomainDefinition(rpi, identity) match {
            case Right(_) =>
              fail("Should have gotten 'port out of range' error")
            case Left(errors) =>
              errors.size must be(1)
              errors.exists(_.format.contains("port out of range: 8900000"))
          }
        }
        Await.result(future, 10.seconds)
      }
    }
  }
}
