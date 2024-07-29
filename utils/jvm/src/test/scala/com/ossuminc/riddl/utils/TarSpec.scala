/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.utils

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.nio.file.Files
import java.nio.file.Path

class TarSpec extends AnyWordSpec with Matchers {

  final val test_tar_file = Path.of("utils/jvm/src/test/input/test-data.tar.gz")
  "Tar" must {
    "untar a .tar.gz file correctly" in {

      val destDir = Files.createTempDirectory("TarSpec")
      Tar.untar(test_tar_file, destDir) match {
        case Right(filesCopied) => filesCopied must be(184)
        case Left(message)      => fail(message)
      }
    }
  }

}
