/*
 * Copyright 2019-2026 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.must.Matchers

import java.nio.file.{Files, Path, Paths}

/** Regression test: scan the Scala.js-linked JS bundle for string
  * patterns that ESM shim plugins (e.g. Vite's esmShimPlugin)
  * misinterpret as real ES module import/export statements.
  *
  * If the JS bundle hasn't been built yet (no fullLinkJS output),
  * the test is skipped via `assume`.  In CI the link step runs
  * before tests, so this will always execute there.
  *
  * See AST.scala BASTImport.format for the canonical explanation
  * of why these patterns are dangerous.
  */
class ESMSafetyTest extends AnyWordSpec with Matchers {

  // Patterns that esmShimPlugin et al. will rewrite if they appear
  // in the JS bundle.  Each entry is a (regex, description) pair.
  //
  // Static imports need \s+ (one or more spaces) because the bare
  // keyword string "import" (zero spaces before a quote) appears
  // legitimately in the bundle as a parser keyword value and does
  // NOT trigger ESM shims — only  import 'mod'  /  import "mod"
  // with whitespace does.  Dynamic import uses \s* because
  // import() with no space is valid ES syntax.
  private val dangerousPatterns: Seq[(String, String)] = Seq(
    ("""import\s+'""",   """import '…  (single-quoted static import)"""),
    ("""import\s+"""",   """import "…  (double-quoted static import)"""),
    ("""import\s*\(""",  """import(…  (dynamic import call)""")
  )

  /** Locate the fullLinkJS output.  The directory name encodes the
    * Scala version so we glob for it rather than hard-coding.
    */
  private def findJsBundle: Option[Path] = {
    val base = Paths.get("riddlLib/js/target")
    if !Files.isDirectory(base) then return None
    val scalaDirs = Files.list(base)
      .filter(p => Files.isDirectory(p) && p.getFileName.toString.startsWith("scala-"))
      .toArray.toSeq.map(_.asInstanceOf[Path])
    scalaDirs.flatMap { sd =>
      val opt = sd.resolve("riddl-lib-opt/main.js")
      val fast = sd.resolve("riddl-lib-fastopt/main.js")
      Seq(opt, fast).find(Files.isRegularFile(_))
    }.headOption
  }

  "Scala.js bundle" should {
    "not contain patterns that trigger ESM shim rewriting" in {
      val bundlePath = findJsBundle
      assume(bundlePath.isDefined,
        "JS bundle not found — run `sbt riddlLibJS/fullLinkJS` first")

      val path = bundlePath.get
      info(s"Scanning JS bundle: $path (${Files.size(path)} bytes)")
      val content = new String(Files.readAllBytes(path), "UTF-8")

      dangerousPatterns.foreach { case (regex, description) =>
        val pattern = regex.r
        val matches = pattern.findAllMatchIn(content).toList
        withClue(
          s"Found ${matches.size} occurrence(s) of dangerous pattern: $description\n" +
          matches.take(5).map { m =>
            val start = Math.max(0, m.start - 40)
            val end = Math.min(content.length, m.end + 40)
            s"  ...${content.substring(start, end).replace("\n", "\\n")}..."
          }.mkString("\n") + "\n"
        ) {
          matches.size mustBe 0
        }
      }
    }
  }
}
