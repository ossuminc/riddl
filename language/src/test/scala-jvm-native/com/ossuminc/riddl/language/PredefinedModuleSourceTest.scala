/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language

import com.ossuminc.riddl.utils.pc

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.nio.file.{Files, Paths}

/** The bundled standard-module source lives in a Scala string constant so it is available on JVM,
  * JS and Native without resource loading. That puts it OUT of reach of the CI TatSu grammar
  * validators, which only scan the repository's `input` directories for `.riddl` files.
  *
  * `language/input/predefined/riddl-standard-module.riddl` is a verbatim copy that those validators
  * DO scan, so the standard library is held to the published grammar. This test is what keeps the
  * copy honest: if the constant changes and the fixture does not (or vice versa), it fails.
  */
class PredefinedModuleSourceTest extends AnyWordSpec with Matchers {

  private val fixture = "language/input/predefined/riddl-standard-module.riddl"

  "the predefined module fixture" must {
    "be byte-identical to PredefinedModule.source" in {
      val path = Paths.get(fixture)
      Files.exists(path) mustBe true
      val onDisk = Files.readString(path)
      withClue(
        s"$fixture has drifted from PredefinedModule.source; regenerate it from the constant. "
      ) {
        onDisk mustBe PredefinedModule.source
      }
    }
  }
}
