/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language

import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.utils.{pc, URL, AbstractTestingBasis}

/** Values that get INTERPOLATED into messages must survive JS string coercion.
  *
  * Scala string interpolation compiles to JS `+` on Scala.js, which performs ToPrimitive on the
  * operand. Putting `@JSExport` on an overridden `toString` breaks that conversion for the whole
  * class: the concatenation throws `TypeError: Cannot convert object to primitive value`.
  *
  * This is not hypothetical and it is not cheap. `At` carried that annotation, so every validation
  * of a model that produced a message containing `at $loc` CRASHED on Scala.js while passing on the
  * JVM. It was invisible twice over: nothing at compile time flags it, and the pass runner's
  * catch-all rendered it through a JS shim that returned no stack trace, so it reached riddl-vscode
  * as a Severe message with EMPTY text -- a blank squiggle on line 1 of the user's file.
  *
  * JS-only by necessity: on the JVM every one of these assertions passes whatever the annotations
  * say, which is exactly why the bug survived.
  */
class ToPrimitiveCoercionTest extends AbstractTestingBasis {

  "an At" should {
    "survive string interpolation, which is JS `+` under the hood" in {
      val input = RiddlParserInput("domain D is { ??? }", "coercion-test")
      val loc = At(input, 0, 6)
      // The exact shape that crashed: an At interpolated into a message.
      val interpolated = s"something at $loc happened"
      // The assertion is that coercion HAPPENS at all -- with the annotation present this line
      // threw before it could produce any string. The offsets prove the At's own rendering ran.
      interpolated must startWith("something at ")
      interpolated must include("(0->6)")
      interpolated must endWith(" happened")
    }

    "survive interpolation when it is the EMPTY location" in {
      val interpolated = s"at ${At.empty}"
      interpolated must include("empty")
    }

    "still render the same text through an explicit toString" in {
      val loc = At(RiddlParserInput("domain D is { ??? }", "coercion-test"), 0, 6)
      s"$loc" mustBe loc.toString
    }
  }

  "a URL" should {
    "survive string interpolation too -- it carried the same annotation" in {
      val url = URL("https", "example.com", "", "some/path.riddl")
      val interpolated = s"loading $url now"
      interpolated must include("example.com")
      interpolated must include("some/path.riddl")
    }

    "survive interpolation when EMPTY" in {
      val interpolated = s"[${URL.empty}]"
      interpolated mustBe "[]"
    }
  }
}
