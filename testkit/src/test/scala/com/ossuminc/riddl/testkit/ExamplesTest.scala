/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.testkit

import com.ossuminc.riddl.language.CommonOptions
import org.scalatest.Assertion

import java.nio.file.Path

/** Unit Tests For ExamplesTest */
class ExamplesTest extends ValidatingTest {

  val dir = "testkit/src/test/input/"

  def doOne(fileName: String): Assertion = {
    parseAndValidateFile(
      Path.of(dir, fileName).toFile,
      CommonOptions(
        showTimes = true,
        showWarnings = false,
        showMissingWarnings = false,
        showStyleWarnings = false
      )
    )
    succeed
  }

  "Examples" should {
    "compile Reactive BBQ" in { doOne("rbbq.riddl") }
    "compile Pet Store" in { doOne("petstore.riddl") }
    "compile Everything" in { doOne("everything.riddl") }
    "compile dokn" in { doOne("dokn.riddl") }
  }
}
