/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.File
import scala.io.Source

/** Phase 9 coverage guard: every AST *construct* (a definition, type
  * expression, statement, or interaction case class) must have an entry in
  * `JSON_COVERAGE.md`. When RIDDL gains a new construct, this test fails until
  * the ledger records its JSON-input status (supported / phase-N / deferred).
  *
  * It scans the AST source directly (JVM-only, repo-root relative) and detects
  * constructs by the base traits in their `extends` clause. Metadata nodes
  * (descriptions, terms, options, attachments, comments) are tracked manually
  * in the ledger's Metadata section and are intentionally out of this scan.
  */
class JsonCoverageGuardTest extends AnyWordSpec with Matchers {

  private def readFile(rel: String): Option[String] =
    val f = new File(rel)
    if f.exists() then
      val src = Source.fromFile(f)
      try Some(src.mkString)
      finally src.close()
    else None

  /** Base traits whose presence in an `extends` clause marks a "construct". */
  private val constructMarkers = Seq(
    "Definition", "Processor", "Branch", "Leaf", "AggregateValue", "OnClause", "Portlet",
    "Interaction", "TypeExpression", "PredefinedType", "Cardinality", "TimeType", "NumericType",
    "Statement"
  )

  "JSON_COVERAGE.md" should {
    "list every AST definition, type expression, statement, and interaction" in {
      val ledgerOpt = readFile("JSON_COVERAGE.md")
      val astOpt = readFile("language/shared/src/main/scala/com/ossuminc/riddl/language/AST.scala")

      (ledgerOpt, astOpt) match
        case (Some(ledger), Some(ast)) =>
          // Node names listed in the ledger tables (first column), with
          // combined cells (e.g. "Sequence / Set / Graph / Replica") split.
          val ledgerNames: Set[String] =
            ledger.linesIterator
              .filter(_.startsWith("|"))
              .flatMap { line =>
                val cells = line.split("\\|", -1).map(_.trim)
                if cells.length >= 2 then Some(cells(1)) else None
              }
              .filter(n => n.nonEmpty && n != "Construct" && !n.startsWith("---"))
              .flatMap(_.split("[/,]").iterator.map(_.trim))
              .filter(_.nonEmpty)
              .toSet

          val caseClass = """case class (\w+)""".r
          val matches = caseClass.findAllMatchIn(ast).toList
          val missing = scala.collection.mutable.ListBuffer.empty[String]

          for (m, idx) <- matches.zipWithIndex do
            val name = m.group(1)
            // Class names are PascalCase; `*Ref` are references (not constructs,
            // and their `extends ProcessorRef`/`PortletRef` would false-match).
            val isCandidate = name.headOption.exists(_.isUpper) && !name.endsWith("Ref")
            val blockEnd = if idx + 1 < matches.length then matches(idx + 1).start else ast.length
            val block = ast.substring(m.start, blockEnd)
            val extendsIdx = block.indexOf("extends")
            if isCandidate && extendsIdx >= 0 then
              val afterExtends = block.substring(extendsIdx)
              val stops = Seq(afterExtends.indexOf(":"), afterExtends.indexOf("{")).filter(_ >= 0)
              val clause = afterExtends.substring(0, if stops.isEmpty then afterExtends.length else stops.min)
              if constructMarkers.exists(clause.contains) && !ledgerNames.contains(name) then
                missing += name
          end for

          withClue(
            s"AST constructs absent from JSON_COVERAGE.md (add a ledger row): ${missing.distinct.mkString(", ")}\n"
          ) {
            missing.distinct mustBe empty
          }

        case _ =>
          cancel("JSON_COVERAGE.md or AST.scala not found relative to the build root; guard skipped")
      end match
    }
  }
}
