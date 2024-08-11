/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.CommonOptions
import com.ossuminc.riddl.passes.validate.ValidatingTest
import org.scalatest.{Assertion, TestData}

import java.nio.file.Path

/** Unit Tests For ExamplesTest */
class ExamplesTest extends ValidatingTest {

  val dir = "passes/jvm/src/test/input/"

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
    "compile Reactive BBQ" in { (td: TestData) =>  doOne("rbbq.riddl") }
    "compile Pet Store" in { (td: TestData) => doOne("petstore.riddl") }
    "compile Everything" in { (td: TestData) => doOne("everything.riddl") }
    "compile dokn" in { (td: TestData) => doOne("dokn.riddl") }
  }
}
