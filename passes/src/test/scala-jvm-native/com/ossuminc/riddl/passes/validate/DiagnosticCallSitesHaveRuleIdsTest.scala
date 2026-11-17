/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.utils.AbstractTestingBasis

import java.nio.file.{Files, Path, Paths}
import scala.jdk.CollectionConverters.*

/** Every diagnostic the passes emit must name its rule.
  *
  * `ruleId` is REQUIRED on the `Accumulator.add*` helpers, but optional on `Message(...)` and on
  * the `Messages.style/warning/...` factories, so a site that builds its message directly and hands
  * it to `messages.add` compiles with no id at all. Ten validation diagnostics did exactly that
  * until 2026-11-13; synapify, which judges messages by rule id, could only match one of them by
  * its TEXT. This test reads the `passes` sources and fails on any such construction without a
  * `ruleId`, so the bypass cannot silently return.
  *
  * **The companion is `EveryDiagnosticHasARuleIdTest`, and neither replaces the other.** That one
  * asserts over OUTPUT, so it catches any construction form, including ones this regex does not
  * know, but only for the checks its single fixture happens to trip; all ten of these sites were
  * outside its reach. This one reaches every call site in the module, but only for the call forms
  * it names below. A new chokepoint shape needs adding HERE; a new check needs nothing.
  *
  * The allowlist is for messages that are NOT rules: an internal exception surfaced as Severe, and
  * a `--debug` resolution trace. Add to it only for that kind of message.
  */
class DiagnosticCallSitesHaveRuleIdsTest extends AbstractTestingBasis {

  private val root: Path = Paths.get("passes/src/main/scala")

  /** (file name, a fragment of the call) for constructions that are deliberately id-less. */
  private val notRules: Seq[(String, String)] = Seq(
    "Pass.scala" -> "Messages.severe(message, At.empty)",
    "ResolutionPass.scala" -> "resolved to ${definition.identify}"
  )

  private val callStart =
    """(Message|Messages\.(style|advisory|tip|missing|usage|info|warning|error|severe|completeness|deprecation))\(""".r

  /** The full text of a call starting at `start`, up to its balanced closing parenthesis. */
  private def callText(text: String, start: Int, open: Int): String = {
    var depth = 1
    var i = open
    while depth > 0 && i < text.length do
      text(i) match
        case '(' => depth += 1
        case ')' => depth -= 1
        case _   => ()
      i += 1
    end while
    text.substring(start, i)
  }

  "Every diagnostic in passes" should {
    "name its rule id" in {
      Files.isDirectory(root) mustBe true
      val files = Files.walk(root).iterator().asScala.filter(_.toString.endsWith(".scala")).toSeq
      files.size must be > 20 // the walk found the module, not an empty directory
      val offenders = for
        file <- files
        text = Files.readString(file)
        m <- callStart.findAllMatchIn(text)
        // No lookbehind in the regex: Scala Native's engine has no lookaround, and the pattern is a
        // `val`, so it would throw at class initialisation. Exclude `xMessage(` / `a.Message(` here.
        if m.start == 0 || !(text(m.start - 1).isLetterOrDigit || "_.".contains(text(m.start - 1)))
        lineSoFar = text.substring(text.lastIndexOf('\n', m.start) + 1, m.start).trim
        // A construction quoted in a comment or ScalaDoc is not a call.
        if !(lineSoFar.startsWith("*") || lineSoFar.startsWith("//") || lineSoFar.startsWith("/*"))
        call = callText(text, m.start, m.end)
        // A `Message(` whose body has no string is a pattern or a type, not a construction.
        if call.contains("\"") && !call.contains("ruleId")
        name = file.getFileName.toString
        if !notRules.exists { case (f, frag) => f == name && call.contains(frag) }
      yield
        val line = text.substring(0, m.start).count(_ == '\n') + 1
        s"$name:$line  ${call.linesIterator.take(3).mkString(" ").take(120)}"
      withClue(offenders.mkString("\nDiagnostics without a ruleId:\n", "\n", "\n")) {
        offenders mustBe empty
      }
    }
  }

  "Every rule emitted as a deprecation" should {
    "be declared `deprecates = true`, so RuleId.deprecations derives it" in {
      // `type-reference-to-is-id` and `opt-deprecated` were emitted as deprecations but declared
      // without the flag, so `RuleId.deprecations` (and `DeprecationCode.all`) omitted both -- the
      // hand-maintained-list failure the flag exists to prevent, reached by a different road.
      // Found by ossum.tech's 2.7.0 docs audit, 2026-11-17.
      val roots = Seq(Paths.get("language/src/main/scala"), root)
      roots.foreach(r => Files.isDirectory(r) mustBe true)
      val files = roots.flatMap(r =>
        Files.walk(r).iterator().asScala.filter(_.toString.endsWith(".scala")).toSeq
      )
      val deprecationCall = """(addDeprecation|deprecation)\(""".r
      val ruleName = """RuleId\.([A-Za-z]+)""".r
      val emitted = (for
        file <- files
        text = Files.readString(file)
        m <- deprecationCall.findAllMatchIn(text)
        if m.start == 0 || !(text(m.start - 1).isLetterOrDigit || text(m.start - 1) == '_')
        lineSoFar = text.substring(text.lastIndexOf('\n', m.start) + 1, m.start).trim
        if !(lineSoFar.startsWith("*") || lineSoFar.startsWith("//") || lineSoFar.contains("def "))
        name <- ruleName.findAllMatchIn(callText(text, m.start, m.end)).map(_.group(1))
      yield name).distinct
      emitted.size must be > 10 // the scan found the deprecation sites, not nothing
      val unflagged = emitted.filterNot(n => com.ossuminc.riddl.language.RuleId.valueOf(n).deprecates)
      withClue(s"emitted as deprecations but not `deprecates = true`: ${unflagged.mkString(", ")}") {
        unflagged mustBe empty
      }
    }
  }
}
