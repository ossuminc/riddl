/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl

import com.ossuminc.riddl.language.AST.{Root, RiddlValue, WithMetaData}
import com.ossuminc.riddl.language.{Finder, toSeq}
import com.ossuminc.riddl.utils.{pc, PlatformContext}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.io.File
import scala.io.Source

/** The JSON identity fixed point, over every RIDDL fixture in this repository.
  *
  * The check is `root0 -> json1 -> root1 -> json2`, asserting `json1 == json2`. A stable fixed
  * point proves the AST<->JSON mapping is lossless and deterministic: anything the serializer
  * drops, reorders or cannot re-read makes the second JSON diverge from the first.
  *
  * This is the in-repo counterpart to `Root2JsonCorpusTest`, and it is the one that gates. The
  * corpus test reads `../riddl-models`, which is a moving target being migrated to 2.0 syntax; the
  * fixtures below are the models this repository owns and must keep working, and they are where
  * every construct the language has gained actually appears.
  *
  * A great many fixtures are not standalone models — `include` fragments, and deliberately-broken
  * inputs under `issues/` that exist precisely to produce parse errors. Those are skipped, but the
  * skip is COUNTED AND NAMED in the test output and the suite asserts a floor on the number of
  * models it actually parsed. That floor is the point: without it a change that broke parsing
  * everywhere would empty the check and the suite would pass green, which is exactly how the corpus
  * test came to assert `0 mustBe 0` for months.
  */
class Root2JsonFixturesTest extends AnyWordSpec with Matchers {

  /** JVM tests run with the build root as the working directory, which is how sibling suites read
    * `riddlLib/json-examples` and `../riddl-models`.
    */
  private val fixtureDirs: Seq[String] =
    Seq("language/input", "passes/input", "riddlc/input", "commands/input")

  private def riddlFiles(dir: String): Seq[File] =
    def walk(f: File): Seq[File] =
      if f.isDirectory then Option(f.listFiles).map(_.toSeq).getOrElse(Nil).flatMap(walk)
      else if f.getName.endsWith(".riddl") then Seq(f)
      else Nil
    val root = new File(dir)
    if root.isDirectory then walk(root).sortBy(_.getPath) else Nil
  end riddlFiles

  private def read(f: File): String =
    val s = Source.fromFile(f)
    try s.mkString
    finally s.close()
  end read

  /** First line index where two JSON strings differ, with a short excerpt of each side. */
  private def firstDiff(a: String, b: String): String =
    val la = a.linesIterator.toIndexedSeq
    val lb = b.linesIterator.toIndexedSeq
    val i = la.indices.find(i => i >= lb.size || la(i) != lb(i)).getOrElse(la.size)
    val ja = if i < la.size then la(i) else "<eof>"
    val jb = if i < lb.size then lb(i) else "<eof>"
    s"line $i:\n      json1: ${ja.trim.take(140)}\n      json2: ${jb.trim.take(140)}"
  end firstDiff

  /** Below this many parsed models something is wrong with the harness itself, not with the
    * fixtures. Set well under the number that parse today so ordinary fixture churn does not
    * disturb it, but far above zero so the suite cannot go vacuously green.
    */
  private val ParsedFloor: Int = 40

  /** How many nodes of each kind the tree holds, counting metadata as well as contents.
    *
    * This is what makes the round trip a FIDELITY check and not merely an idempotence one. A
    * construct the serializer drops entirely is absent from both JSONs, so `json1 == json2` still
    * holds and the identity check sails past it — the census does not, because the re-parsed tree
    * is then short by exactly the nodes that were dropped.
    *
    * Traversal is `Finder`'s, so the statement containers it knows about (`when` branches, `match`
    * cases, `foreach` bodies, saga steps) are all included; metadata hangs off definitions rather
    * than living in their contents, so it is gathered separately.
    */

  private def census(root: Root): Map[String, Int] =
    val finder = Finder(root)
    val nodes = finder.recursiveFindByType[RiddlValue]
    val metadata = finder.recursiveFindByType[WithMetaData].flatMap(_.metadata.toSeq)
    (nodes ++ metadata)
      .groupBy(_.getClass.getSimpleName)
      .map((kind, all) => kind -> all.size)
  end census

  /** The kinds present in one census and missing (or short) in the other, most-lost first. */
  private def censusDiff(before: Map[String, Int], after: Map[String, Int]): String =
    (before.keySet ++ after.keySet).toSeq
      .map(k => (k, before.getOrElse(k, 0), after.getOrElse(k, 0)))
      .filter((_, b, a) => b != a)
      .sortBy((_, b, a) => -(b - a).abs)
      .map((k, b, a) => s"$k: $b -> $a")
      .mkString(", ")

  "the JSON identity fixed point" should {

    "hold for every standalone RIDDL fixture in this repository" in {
      val files = fixtureDirs.flatMap(riddlFiles)
      files must not be empty

      var parsed = 0
      var reparsed = 0
      var identical = 0
      val notStandalone = scala.collection.mutable.ListBuffer.empty[String]
      val failures = scala.collection.mutable.ListBuffer.empty[String]

      for f <- files do
        // The ABSOLUTE path is the origin so that `include` resolves against the fixture's own
        // directory: RiddlLib.originToURL only builds a full-path URL for an origin starting "/".
        val origin = f.getAbsolutePath
        RiddlLib.parseString(read(f), origin) match
          case RiddlResult.Success(root0) =>
            parsed += 1
            val json1 = RiddlLib.root2Json(root0)
            RiddlLib.parseJson(json1, f.getName) match
              case RiddlResult.Success(root1) =>
                reparsed += 1
                val json2 = RiddlLib.root2Json(root1)
                if json1 == json2 then identical += 1
                else failures += s"${f.getPath}: ${firstDiff(json1, json2)}"
              case RiddlResult.Failure(errors) =>
                failures += s"${f.getPath} [the generated JSON did not re-parse]: " +
                  errors.take(2).map(_.format).mkString("; ")
          // Not a standalone model: an include fragment, or an input written to fail parsing.
          // That is a property of the fixture, not a defect, so it is recorded and skipped.
          case RiddlResult.Failure(_) => notStandalone += f.getPath
      end for

      info(s"fixtures=${files.size} parsed=$parsed reparsed=$reparsed identical=$identical")
      if notStandalone.nonEmpty then
        info(s"not standalone models (skipped, ${notStandalone.size}):")
        notStandalone.foreach(p => info("  " + p))
      end if
      if failures.nonEmpty then
        info(s"identity failures (${failures.size}):")
        failures.foreach(m => info("  " + m))
      end if

      withClue(
        s"only $parsed fixtures parsed, below the floor of $ParsedFloor — the harness is broken, " +
          "not the fixtures: "
      ) {
        parsed must be >= ParsedFloor
      }
      // Every model that parses must survive the round trip, and survive it identically.
      reparsed mustBe parsed
      identical mustBe parsed
    }

    "carry every node of every fixture through the round trip, losing none" in {
      val files = fixtureDirs.flatMap(riddlFiles)

      var compared = 0
      val lossy = scala.collection.mutable.ListBuffer.empty[String]
      // Aggregated across all fixtures, so a kind the serializer cannot express shows up as one
      // headline number rather than as eighty separate lines, each with a fixture to look at.
      val lostByKind = scala.collection.mutable.Map.empty[String, Int].withDefaultValue(0)
      val exampleOf = scala.collection.mutable.Map.empty[String, String]

      for f <- files do
        RiddlLib.parseString(read(f), f.getAbsolutePath) match
          case RiddlResult.Success(root0) =>
            RiddlLib.parseJson(RiddlLib.root2Json(root0), f.getName) match
              case RiddlResult.Success(root1) =>
                compared += 1
                val before = census(root0)
                val after = census(root1)
                if before != after then
                  lossy += s"${f.getPath}: ${censusDiff(before, after)}"
                  for kind <- before.keySet ++ after.keySet do
                    val delta = before.getOrElse(kind, 0) - after.getOrElse(kind, 0)
                    if delta != 0 then
                      lostByKind(kind) += delta
                      exampleOf.getOrElseUpdate(kind, f.getPath)
                    end if
                  end for
                end if
              case RiddlResult.Failure(_) => () // reported by the identity case above
          case RiddlResult.Failure(_) => () // not a standalone model; listed by the case above
      end for

      info(s"censuses compared=$compared lossy=${lossy.size}")
      if lostByKind.nonEmpty then
        info("net node count change by kind (positive = lost in the round trip):")
        lostByKind.toSeq
          .sortBy(-_._2.abs)
          .foreach((k, n) => info(f"  $n%5d  $k%-20s e.g. ${exampleOf.getOrElse(k, "?")}"))
      end if
      if lossy.nonEmpty then
        info(s"lossy fixtures (${lossy.size}, first 12):")
        lossy.take(12).foreach(m => info("  " + m))
      end if

      compared must be >= ParsedFloor
      // Nothing is lost, and nothing is duplicated either: a non-zero count in EITHER direction
      // fails. Gaining nodes is as wrong as losing them — carrying a comment in two places at
      // once is how the statement-list work first went wrong.
      withClue(s"${lossy.size} of $compared fixtures changed node counts in the round trip: ") {
        lostByKind.filter((_, n) => n != 0).toMap mustBe empty
      }
    }

    /** The census counts NODES, so it is blind to a lost FIELD: drop `from` off an on-clause, or
      * the `command` keyword off an inlet's type reference, and every node is still present and
      * both censuses agree. `json1 == json2` is blinder still — anything dropped consistently in
      * both directions satisfies it, which is how eleven gaps survived here at once.
      *
      * Prettify is RIDDL's other full-fidelity surface: it renders the whole tree back to source,
      * fields and all, and it is location-independent, so two ASTs that differ only in where they
      * were read from still render identically. Comparing the rendering of `root0` against the
      * rendering of `root1` therefore catches field-level loss that neither other check can see.
      *
      * The bound is prettify's own fidelity — a construct prettify does not emit is invisible here
      * too. That is a real limit, but the two surfaces have different gaps, so each covers the
      * other's blind spots better than either covers its own.
      */
    "render identically through prettify before and after the round trip" in {
      val files = fixtureDirs.flatMap(riddlFiles)

      var compared = 0
      val divergent = scala.collection.mutable.ListBuffer.empty[String]

      for f <- files do
        RiddlLib.parseString(read(f), f.getAbsolutePath) match
          case RiddlResult.Success(root0) =>
            RiddlLib.parseJson(RiddlLib.root2Json(root0), f.getName) match
              case RiddlResult.Success(root1) =>
                compared += 1
                val before = RiddlLib.root2RiddlSource(root0)
                val after = RiddlLib.root2RiddlSource(root1)
                if before != after then divergent += s"${f.getPath}: ${firstDiff(before, after)}"
              case RiddlResult.Failure(_) => () // reported by the identity case above
          case RiddlResult.Failure(_) => () // not a standalone model; listed by the case above
      end for

      info(s"prettify agreement compared=$compared divergent=${divergent.size}")
      if divergent.nonEmpty then
        info(s"fixtures whose prettified source changed (${divergent.size}, first 12):")
        divergent.take(12).foreach(m => info("  " + m))
      end if

      compared must be >= ParsedFloor
      // No ceiling: RIDDL is fully reflective, so a model written to JSON and read back must
      // reproduce its source EXACTLY. This started at 63 divergent fixtures and was ratcheted down
      // to zero; anything above zero now is a regression, not a backlog item.
      withClue(
        s"${divergent.size} of $compared fixtures render differently after a JSON round trip — " +
          "the JSON surface lost, reordered or altered something: "
      ) {
        divergent mustBe empty
      }
    }
  }
}
