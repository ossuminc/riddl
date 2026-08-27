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

  private val corpora = Seq(Path.of("../riddl-models"), Path.of("../riddl-examples"))

  private def entryPoints(root: Path): Seq[Path] =
    if !Files.isDirectory(root) then Nil
    else
      Files
        .walk(root)
        .iterator()
        .asScala
        .filter(p => p.toString.endsWith(".conf") && !p.toString.contains("/target/"))
        .flatMap { conf =>
          val base = conf.getFileName.toString.stripSuffix(".conf")
          val src = conf.getParent.resolve(s"$base.riddl")
          if Files.isRegularFile(src) then Some(src) else None
        }
        .toSeq

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

  "the -type vocabulary" should {

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
