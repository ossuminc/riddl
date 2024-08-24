package com.ossuminc.riddl.passes

import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.language.{AST, CommonOptions, Messages}
import com.ossuminc.riddl.passes.stats.{DefinitionStats, KindStats, StatsOutput, StatsPass}
import com.ossuminc.riddl.passes.validate.ValidatingTest
import com.ossuminc.riddl.passes.{Pass, PassInput, PassesResult}

import org.scalatest.*
import java.nio.file.Path

class StatsPassTest extends ValidatingTest {

  "DefinitionStats" must {
    "default correctly" in { (td: TestData) =>
      val ds = DefinitionStats()
      ds.kind mustBe ""
      ds.isEmpty mustBe true
      ds.descriptionLines mustBe 0
      ds.numSpecifications mustBe 0
      ds.numCompleted mustBe 0L
      ds.numContained mustBe 0L
      ds.numAuthors mustBe 0L
      ds.numTerms mustBe 0L
      ds.numOptions mustBe 0L
      ds.numIncludes mustBe 0L
      ds.numStatements mustBe 0L
    }
  }

  "StatsOutput" must {
    "default correctly" in { (td: TestData) =>
      val so = StatsOutput()
      so.messages mustBe empty
      so.maximum_depth mustBe 0
      so.categories mustBe Map.empty
    }
  }

  "StatsPass" must {
    "generate statistics" in { (td: TestData) =>
      val rpi = RiddlParserInput.fromCwdPath(Path.of("language/jvm/src/test/input/everything.riddl"), td)
      parseValidateAndThen(rpi) {
        (pr: PassesResult, root: AST.Root, rpi: RiddlParserInput, messages: Messages.Messages) =>
          if messages.justErrors.nonEmpty then fail(messages.justErrors.format)
          else
            val input = PassInput(root, CommonOptions())
            val outputs = pr.outputs
            val pass = StatsPass(input, outputs)
            val statsOutput: StatsOutput = Pass.runPass[StatsOutput](input, outputs, pass)
            if statsOutput.messages.nonEmpty then fail(statsOutput.messages.format)
            statsOutput.maximum_depth > 0 mustBe true
            statsOutput.categories mustNot be(empty)
            statsOutput.categories.toSeq.foreach { pair => println(pair._1 + s" => ${pair._2.count}") }
            statsOutput.categories.size must be(23)
            val ksAll: KindStats = statsOutput.categories("All")
            ksAll.count must be(22)
            ksAll.numEmpty must be(19)
            ksAll.numStatements must be(6)
            succeed
      }
    }
  }
}
