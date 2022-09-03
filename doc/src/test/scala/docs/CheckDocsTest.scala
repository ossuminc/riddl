package docs

import com.reactific.riddl.translator.hugo.HugoTranslateExamplesBase

/** Unit Tests To Check Documentation Examples */
class CheckDocsTest extends HugoTranslateExamplesBase {

  override val directory: String = "doc/src/hugo/"
  val output: String = "doc/src/hugo"

  "Docs" should {
    "run successfully with hugo" in {
      runHugo("")
    }
  }
}
