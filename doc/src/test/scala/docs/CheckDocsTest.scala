/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package docs
import com.ossuminc.riddl.hugo.RunHugoTestBase
import org.scalatest.wordspec.AnyWordSpec

import java.nio.file.Path

/** Unit Tests To Check Documentation Examples */
class CheckDocsTest extends AnyWordSpec with RunHugoTestBase {

  "Docs" should {
    "run successfully with hugo" in {
      val srcDir = Path.of("doc/src/hugo")
      runHugo(srcDir)
    }
  }
}
