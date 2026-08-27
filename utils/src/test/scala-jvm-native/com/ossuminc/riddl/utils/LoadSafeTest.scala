/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.utils

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.nio.file.Files
import scala.concurrent.duration.DurationInt

/** `loadSafe` reports every expected failure instead of throwing.
  *
  * `load` throws, and on the JVM it throws SYNCHRONOUSLY for a missing file — before the Future
  * exists — so `load(url).recover { … }` never sees it. The exception then reached the
  * command-level catch-all and a user was shown `java.nio.charset.MalformedInputException: Input
  * length = 1`: a Java class name that does not even name the file. Each case below is one of
  * those, and none of them opens a network connection.
  */
class LoadSafeTest extends AnyWordSpec with Matchers {

  private def loadOf(url: URL): Either[LoadFailure, String] =
    Await.result(pc.loadSafe(url), 10.seconds)

  "loadSafe" should {

    "report a missing file as NotFound, not throw" in {
      val missing = URL.fromFullPath("/tmp/riddl-loadsafe-does-not-exist-98765.riddl")
      loadOf(missing) match
        case Left(f: LoadFailure.NotFound) => f.describe must include("No such file")
        case other                         => fail(s"expected NotFound, got $other")
    }

    "report a directory as NotAFile, not throw" in {
      val dir = Files.createTempDirectory("riddl-loadsafe")
      try
        loadOf(URL.fromFullPath(dir.toString)) match
          case Left(f: LoadFailure.NotAFile) => f.describe must include("directory")
          case Left(other)                   =>
            // Previously this asserted only "some failure", which let a directory be reported as
            // "No such file" — accurate-sounding and wrong, and the loose assertion hid it.
            fail(s"a directory must be NotAFile, not $other")
          case Right(_) => fail("reading a directory must not succeed")
      finally Files.deleteIfExists(dir)
    }

    "report undecodable bytes as a failure, not throw" in {
      val f = Files.createTempFile("riddl-loadsafe", ".riddl")
      try
        Files.write(f, Array[Byte](0, 1, 2, 3, 255.toByte, 254.toByte))
        loadOf(URL.fromFullPath(f.toString)) match
          case Left(failure) => failure.describe must include(f.getFileName.toString)
          case Right(_)      => fail("binary content must not load as text")
      finally Files.deleteIfExists(f)
    }

    "still load a good file" in {
      val f = Files.createTempFile("riddl-loadsafe-ok", ".riddl")
      try
        Files.writeString(f, "domain D is { ??? }")
        loadOf(URL.fromFullPath(f.toString)) match
          case Right(content) => content must include("domain D")
          case Left(failure)  => fail(s"a readable file must load: ${failure.describe}")
      finally Files.deleteIfExists(f)
    }

    "name the file in every failure" in {
      val missing = URL.fromFullPath("/tmp/riddl-loadsafe-absent-13579.riddl")
      loadOf(missing) match
        case Left(failure) =>
          // The old output said "Input length = 1" and left you guessing which file.
          failure.url.toExternalForm must include("riddl-loadsafe-absent-13579")
        case Right(_) => fail("must not succeed")
    }
  }
}
