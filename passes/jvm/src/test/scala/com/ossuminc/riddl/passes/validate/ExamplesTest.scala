/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.utils.pc
import com.ossuminc.riddl.utils.CommonOptions

import org.scalatest.{Assertion, TestData}

import java.nio.file.Path

/** Unit Tests For ExamplesTest */
class ExamplesTest extends JVMAbstractValidatingTest {

  val dir = "language/jvm/src/test/input/"

  def doOne(fileName: String): Assertion = {
    pc.withOptions(CommonOptions.noWarnings.copy(showTimes = true)) { _ =>
      parseAndValidateFile(Path.of(dir, fileName))
    }
  }

  "Examples" should {
    "compile Reactive BBQ" in { (td: TestData) => doOne("rbbq.riddl") }
    "compile Pet Store" in { (td: TestData) => doOne("petstore.riddl") }
    "compile Everything" in { (td: TestData) => doOne("everything.riddl") }
    "compile dokn" in { (td: TestData) => doOne("dokn.riddl") }
  }
}
