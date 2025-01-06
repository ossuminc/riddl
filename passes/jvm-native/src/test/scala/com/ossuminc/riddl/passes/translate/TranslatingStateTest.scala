/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.translate

import com.ossuminc.riddl.language.AST.{Domain, Identifier}
import com.ossuminc.riddl.language.At
import com.ossuminc.riddl.utils.{AbstractTestingBasis, OutputFile}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.nio.file.{Files, Path}

class TranslatingStateTest extends AbstractTestingBasis {

  case class TestTranslatingOptions(
    inputFile: Option[Path] = None,
    outputDir: Option[Path] = None,
    projectName: Option[String] = None
  ) extends TranslatingOptions

  case class TestTranslatingState(options: TestTranslatingOptions) extends TranslatingState[OutputFile]

  "TranslatingState" should {
    "have defaulted list of generatedFiles" in {
      val tto = TestTranslatingOptions(Some(Path.of("")), Some(Files.createTempDirectory("test")), Some("test"))
      val ts = TestTranslatingState(tto)
      val gf = ts.generatedFiles
      gf must be(empty)
      val path: Seq[String] = ts.makeDefPath(Domain(At(), Identifier(At(), "domain")),Seq.empty)
      path.mkString(".") must be("domain")
    }
  }
}
