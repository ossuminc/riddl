/*
 * Copyright 2019-2025 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing
import com.ossuminc.riddl.language.AST.Context
import com.ossuminc.riddl.utils.PlatformContext
import org.scalatest.TestData

abstract class MetaDataTest(using PlatformContext) extends AbstractParsingTest {

  "MetaDataParser" should {
    "parse attachments correctly" in { (td: TestData) =>
      val input = RiddlParserInput(
        """context foo {
          | ???
          |} with {
          |  attachment infile is text/plain in file "nada.txt"
          |  attachment inline is text/plain as "nada"
          |}""".stripMargin,
        td
      )
      parseDefinition[Context](input) match {
        case Left(errors) =>
          val msg = errors.map(_.format).mkString
          fail(msg)
        case Right((context: Context, _)) =>
          context.stringAttachments.size must be(1)
          context.stringAttachments.head.value.s must be("nada")
          context.fileAttachments.size must be(1)
          context.fileAttachments.head.inFile.s must be("nada.txt")
      }
    }
  }
}
