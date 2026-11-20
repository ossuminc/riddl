/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.commands

import com.ossuminc.riddl.commands.find.FindPredicates
import com.ossuminc.riddl.commands.project.{ProjectionOutput, ProjectionPass}
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.passes.{Pass, PassInput, PassesOutput, Riddl}
import com.ossuminc.riddl.utils.{Await, PlatformContext, pc}
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.nio.file.{Files, Path}
import scala.concurrent.duration.DurationInt
import scala.jdk.CollectionConverters.*

/** `find -type` must accept every kind the projection can emit.
  *
  * **`-type` rejects an unknown value as a parameter error**, which is only safe while the
  * vocabulary is complete. A MISSING entry turns a working query into a failure — the opposite
  * defect from the one the guard fixes, and a worse one, because the user is told their correct
  * query is wrong.
  *
  * `ProjectionPass.kindOf` derives a kind from `RiddlValue.kind` at RUNTIME, so there is no static
  * list to check against. This re-derives the real vocabulary from the corpus — which between its
  * models exercises essentially every construct in the language — and fails on drift.
  *
  * SKIPS when the sibling checkout is absent, per [1.3], so a developer without it is not blocked.
  */
class FindTypeVocabularyTest extends AnyWordSpec with Matchers {

  given io: PlatformContext = pc

  // riddl-examples was retired 2026-11-19; riddl-models is the corpus.
  private val corpora = Seq(Path.of("../riddl-models"))

  /** The direct children of `dir`, or nothing if it cannot be read. A directory that vanishes
    * between being listed and being descended is not this test's problem to report.
    */
  private def childrenOf(dir: Path): Seq[Path] =
    try
      val stream = Files.list(dir)
      try stream.iterator().asScala.toSeq
      finally stream.close()
    catch case _: java.io.IOException => Nil

  /** Entry points under `root`, PRUNING `target` during the traversal rather than filtering it out
    * afterwards.
    *
    * **A filter after `Files.walk` looked equivalent to a prune and is not.** The walk STATS every
    * entry it descends past, so `.filter(!_.toString.contains("/target/"))` still traversed a
    * sibling repo's entire build output — and on 2026-09-28 a concurrent sbt run in riddl-models
    * removed a file under `target/out/value` mid-walk, so the Native row failed with
    * `NoSuchFileException` raised *inside* the walk, where no downstream filter can help. Pruning
    * is also just cheaper: `riddl-models/target/out/value` alone held 783 cache files, none of
    * which can ever be an entry point.
    */
  private def entryPoints(root: Path): Seq[Path] =
    if !Files.isDirectory(root) then Nil
    else
      val confs = Seq.newBuilder[Path]
      def descend(dir: Path): Unit =
        childrenOf(dir).foreach { p =>
          if Files.isDirectory(p) then
            if p.getFileName.toString != "target" then descend(p)
          else if p.toString.endsWith(".conf") then confs += p
        }
      descend(root)
      confs
        .result()
        .flatMap { conf =>
          val base = conf.getFileName.toString.stripSuffix(".conf")
          val src = conf.getParent.resolve(s"$base.riddl")
          if Files.isRegularFile(src) then Some(src) else None
        }

  private def kindsIn(model: Path): Set[String] =
    given scala.concurrent.ExecutionContext = pc.ec
    val future = RiddlParserInput.fromPathSafe(model.toString).map {
      case Left(_) => Set.empty[String]
      case Right(rpi) =>
        Riddl.parseAndValidate(rpi, shouldFailOnError = false) match
          case Left(_) => Set.empty[String]
          case Right(result) =>
            val projection = Pass.runPass[ProjectionOutput](
              PassInput(result.root),
              PassesOutput(),
              ProjectionPass(PassInput(result.root), result.outputs)
            )
            projection.records.flatMap(_.value.get("kind").map(_.str)).toSet
    }
    Await.result(future, 60.seconds)

  /** Kinds projected from an inline source, for constructs the corpus may not use yet. */
  private def kindsInSource(src: String): Set[String] =
    Riddl.parseAndValidate(RiddlParserInput(src, "inline"), shouldFailOnError = false) match
      case Left(msgs) => fail(msgs.map(_.format).mkString("\n"))
      case Right(result) =>
        val projection = Pass.runPass[ProjectionOutput](
          PassInput(result.root),
          PassesOutput(),
          ProjectionPass(PassInput(result.root), result.outputs)
        )
        projection.records.flatMap(_.value.get("kind").map(_.str)).toSet

  "the -type vocabulary" should {

    // ossum.tech, 2026-11-20: the 2.8.0 clauses were refused as "unknown -type" because nothing
    // in the corpus used them, so the corpus case below could not see they were missing.
    "accept the 2.8.0 `on every` and `on entry` clauses" in {
      val kinds = kindsInSource(
        """domain D is { context C is {
          |  entity E is {
          |    record S is { n: Integer }
          |    state Only of record E.S is {
          |      handler SH is { on entry is { do "arrived" } }
          |    }
          |    handler H is { on every 5 minutes is { do "tick" } }
          |  }
          |} }
          |""".stripMargin
      )
      kinds must contain allOf ("on-every", "on-entry")
      FindPredicates.typeVocabulary must contain allOf ("on-every", "on-entry")
    }


    "contain every kind the corpus can produce" in {
      val models = corpora.flatMap(entryPoints)
      if models.isEmpty then cancel("no sibling corpus checkout; see BACKLOG [1.3]")
      else {
        // Guard the guard: a truncated corpus would make this vacuously pass, which is the
        // `0 mustBe 0` shape this repo keeps recording.
        withClue("the corpus must be present and whole, or this test proves nothing: ") {
          models.size must be >= 190
        }
        val observed = models.map(kindsIn).reduce(_ ++ _)
        withClue(s"the corpus produced ${observed.size} distinct kinds: ") {
          observed.size must be >= 80
        }
        val missing = (observed -- FindPredicates.typeVocabulary).toSeq.sorted
        withClue(
          s"add these to FindPredicates.knownKinds -- `-type` currently REJECTS them as unknown, " +
            s"so a correct query fails:\n  ${missing.mkString("\n  ")}\n"
        ) {
          missing mustBe empty
        }
      }
    }
  }
}
