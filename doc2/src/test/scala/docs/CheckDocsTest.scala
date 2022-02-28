package docs

import com.yoppworks.ossum.riddl.translator.hugo.HugoTranslateExamplesBase

import java.nio.file.Path

/** Unit Tests To Check Documentation Examples */
class CheckDocsTest extends HugoTranslateExamplesBase {

  override val directory: String = "doc2/src/main/hugo/"
  val output: String = "doc2/src/main/hugo"

  "Docs" should {
    "run successfully with hugo" in {
      runHugo("")
    }
  }
}
