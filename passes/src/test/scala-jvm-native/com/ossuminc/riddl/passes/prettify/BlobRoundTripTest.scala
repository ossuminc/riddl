/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.prettify

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{Finder, toSeq}
import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.passes.validate.AbstractValidatingTest
import com.ossuminc.riddl.passes.{Pass, PassInput, PassesOutput}
import com.ossuminc.riddl.utils.pc

import org.scalatest.*

/** `Blob(<kind>)` -- the predefined type for opaque bulk content.
  *
  * Until 2.0 this was RESERVED BUT UNUSABLE. `AST.Blob`, the `BlobKind` enum, BASTWriter/Reader
  * support and the JSON `BlobDto` all existed, and `Blob` sat in `PredefType.allPredefTypes` -- so
  * `type B is Blob` failed with "Path 'Blob' was not resolved" AND `type Blob is String` failed
  * with "redefines built-in type 'Blob'". Every surface was ready except the parser rule.
  *
  * This pins the reflection contract for it: anything that parses must also EMIT and survive a
  * round trip. Prettify needs no `case Blob` of its own because `AST.Blob.format` is
  * `s"$kind($blobKind)"` and `RiddlFileEmitter.emitTypeExpression` falls through to
  * `case p: PredefinedType => add(p.format)` -- but "it should just work" is exactly the assumption
  * this repo requires proving rather than believing.
  */
class BlobRoundTripTest extends AbstractValidatingTest {

  private def parse(src: String, origin: String): Root =
    TopLevelParser.parseInput(RiddlParserInput(src, origin)) match
      case Right(root) => root
      case Left(msgs)  => fail(s"parse of $origin failed:\n${msgs.format}")

  private def prettify(root: Root): String =
    val creators = Pass.standardPasses :+ { (in: PassInput, out: PassesOutput) =>
      PrettifyPass(in, out, PrettifyPass.Options(flatten = true, inputDir = ""))
    }
    Pass
      .runThesePasses(PassInput(root), creators)
      .outputs
      .outputOf[PrettifyOutput](PrettifyPass.name)
      .getOrElse(fail("PrettifyPass produced no output"))
      .state
      .filesAsString

  private def blobOf(root: Root, name: String): Blob =
    Finder(root)
      .recursiveFindByType[Type]
      .find(_.id.value == name)
      .map(_.typEx)
      .collect { case b: Blob => b }
      .getOrElse(fail(s"type '$name' is not a Blob in the parsed tree"))

  private def model(typeEx: String): String =
    s"""domain D is {
       |  type Payload is $typeEx with { briefly "p" }
       |} with { briefly "d" }
       |""".stripMargin

  "a Blob type" should {

    "parse to AST.Blob carrying its declared kind, for every BlobKind" in { (td: TestData) =>
      BlobKind.values.foreach { kind =>
        blobOf(parse(model(s"Blob($kind)"), s"blob-$kind"), "Payload").blobKind mustBe kind
      }
    }

    "prettify back to the SAME spelling it was written in" in { (td: TestData) =>
      val pretty = prettify(parse(model("Blob(Image)"), "emit"))
      pretty must include("Blob(Image)")
    }

    "round-trip: parse -> prettify -> parse preserves the kind, for every BlobKind" in {
      (td: TestData) =>
        BlobKind.values.foreach { kind =>
          val once = parse(model(s"Blob($kind)"), s"rt-$kind")
          val again = parse(prettify(once), s"rt-regen-$kind")
          blobOf(again, "Payload").blobKind mustBe blobOf(once, "Payload").blobKind
        }
    }

    "work as a field type inside a record" in { (td: TestData) =>
      val src =
        """domain D is {
          |  record R is { attachment: Blob(FileSystem) } with { briefly "r" }
          |} with { briefly "d" }
          |""".stripMargin
      val field = Finder(parse(src, "field"))
        .recursiveFindByType[Field]
        .find(_.id.value == "attachment")
        .getOrElse(fail("field 'attachment' not found"))
      field.typeEx match
        case b: Blob => b.blobKind mustBe BlobKind.FileSystem
        case other   => fail(s"field 'attachment' is a ${other.getClass.getSimpleName}, not a Blob")
    }
  }

  "the names freed in 2.0" should {

    // Capitalized `Range` and `Unknown` were in the reserved-name list but had no way to be
    // written, so they were unusable in BOTH directions. Dropping them frees them as ordinary
    // names. The writable range type is and remains the LOWERCASE `range(n,m)`.
    "let a model define its own type named Range or Unknown" in { (td: TestData) =>
      val src =
        """domain D is {
          |  type Range is Integer with { briefly "r" }
          |  type Unknown is String with { briefly "u" }
          |} with { briefly "d" }
          |""".stripMargin
      val root = parse(src, "freed")
      val names = Finder(root).recursiveFindByType[Type].map(_.id.value)
      names must contain("Range")
      names must contain("Unknown")
    }

    "keep lowercase range(n,m) working as the range type" in { (td: TestData) =>
      Finder(parse(model("range(1,10)"), "lowercase-range"))
        .recursiveFindByType[Type]
        .find(_.id.value == "Payload")
        .map(_.typEx)
        .getOrElse(fail("type 'Payload' not found")) match
        case RangeType(_, min, max) => (min, max) mustBe (1L, 10L)
        case other => fail(s"expected RangeType, got ${other.getClass.getSimpleName}")
    }
  }
}
