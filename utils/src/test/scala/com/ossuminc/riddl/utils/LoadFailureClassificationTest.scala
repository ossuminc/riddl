/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.utils

/** `LoadFailure.from` must classify correctly AND link on every platform.
  *
  * This suite lives in SHARED test sources deliberately. The bug it guards was not a logic error —
  * it was `LoadFailure.from` naming `java.io.FileNotFoundException` and friends, which compile
  * everywhere and are absent from the Scala.js javalib. Any consumer whose reachable graph included
  * the method failed at LINK time:
  *
  * {{{
  * Referring to non-existent class java.io.FileNotFoundException
  *   called from com.ossuminc.riddl.utils.LoadFailure$.from(...)
  * }}}
  *
  * riddl's own `riddlLibJS/fullLinkJS` never caught it, because dead-code elimination did not reach
  * the method from riddl-lib's exports. Synapify found it, where every parse reaches here.
  *
  * Being in shared sources, this suite forces `LoadFailure.from` into the reachable graph of the JS
  * TEST link, so the same mistake fails riddl's own build instead of a consumer's.
  */
class LoadFailureClassificationTest extends AbstractTestingBasis {

  private val url = URL.empty

  /** A stand-in whose SIMPLE NAME is what the classifier reads. Constructing a real
    * `java.io.FileNotFoundException` here would reintroduce the very JVM-only reference this suite
    * exists to keep out of shared code.
    */
  private class FileNotFoundException(msg: String) extends RuntimeException(msg)
  private class NoSuchFileException(msg: String) extends RuntimeException(msg)
  private class MalformedInputException(msg: String) extends RuntimeException(msg)
  private class AccessDeniedException(msg: String) extends RuntimeException(msg)

  "LoadFailure.from" should {

    "classify a missing file as NotFound, whatever the platform calls it" in {
      // The JVM throws java.io.FileNotFoundException; DOMPlatformContext throws its OWN
      // FileNotFoundException case class. Matching on the simple name catches both -- before
      // this fix the JS one fell through to Unreachable, which was wrong quite apart from the
      // link failure.
      LoadFailure.from(url, FileNotFoundException("nope")) mustBe LoadFailure.NotFound(url)
      LoadFailure.from(url, NoSuchFileException("nope")) mustBe LoadFailure.NotFound(url)
    }

    "classify a directory by its message, since the JVM offers no distinct type" in {
      LoadFailure.from(url, RuntimeException("Is a directory")) mustBe LoadFailure.NotAFile(url)
    }

    "classify a permission failure as Unreadable" in {
      LoadFailure.from(url, AccessDeniedException("no")) mustBe LoadFailure.Unreadable(url)
      LoadFailure.from(url, RuntimeException("Permission denied")) mustBe
        LoadFailure.Unreadable(url)
    }

    "classify a decoding failure as Undecodable, keeping the detail" in {
      LoadFailure.from(url, MalformedInputException("Input length = 1")) match
        case LoadFailure.Undecodable(u, detail) =>
          u mustBe url
          detail must include("Input length")
        case other => fail(s"expected Undecodable, got $other")
    }

    "put anything else in Unreachable, naming the class so the report is not empty" in {
      LoadFailure.from(url, RuntimeException("boom")) match
        case LoadFailure.Unreachable(_, detail) =>
          detail must include("RuntimeException")
          detail must include("boom")
        case other => fail(s"expected Unreachable, got $other")
    }

    "not lose the failure when the exception carries no message" in {
      // getMessage is null here; an earlier implementation would have produced "null" or an
      // empty detail, which tells a user nothing.
      LoadFailure.from(url, RuntimeException()) match
        case LoadFailure.Unreachable(_, detail) => detail must include("RuntimeException")
        case other                              => fail(s"expected Unreachable, got $other")
    }
  }
}
