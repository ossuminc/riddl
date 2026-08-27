/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing
import com.ossuminc.riddl.language.AST
import com.ossuminc.riddl.language.AST.Context
import com.ossuminc.riddl.language.toSeq
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

    /** The ULID attachment could NOT be parsed at all until the three attachment forms were
      * factored to share one `attachment` keyword: `Keywords.keyword` ends in a cut, so the general
      * `attachment` rule — listed first in `metaData` — committed and the ULID rule was
      * unreachable. It had no fixture and no test anywhere, which is why nothing noticed.
      */
    "parse a ULID attachment" in { (td: TestData) =>
      val input = RiddlParserInput(
        """context foo {
          | ???
          |} with {
          |  attachment ULID is "01ARZ3NDEKTSV4RRFFQ69G5FAV"
          |}""".stripMargin,
        td
      )
      parseDefinition[Context](input) match {
        case Left(errors) => fail(errors.map(_.format).mkString)
        case Right((context: Context, _)) =>
          val ulids = context.metadata.toSeq.collect { case u: AST.ULIDAttachment => u }
          ulids.size must be(1)
          ulids.head.ulid.toString must be("01ARZ3NDEKTSV4RRFFQ69G5FAV")
      }
    }

    /** An ordinary attachment that happens to be NAMED `ULID` must still take the general form —
      * the ULID branch is tried first, so this proves the two branches backtrack against each other
      * rather than the first one winning outright.
      */
    "parse a normal attachment named ULID" in { (td: TestData) =>
      val input = RiddlParserInput(
        """context foo {
          | ???
          |} with {
          |  attachment ULID is text/plain as "not really a ulid"
          |}""".stripMargin,
        td
      )
      parseDefinition[Context](input) match {
        case Left(errors) => fail(errors.map(_.format).mkString)
        case Right((context: Context, _)) =>
          context.stringAttachments.size must be(1)
          context.stringAttachments.head.value.s must be("not really a ulid")
      }
    }
  }
}
