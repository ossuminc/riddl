/** Unit Tests To Check Documentation Examples */

import com.yoppworks.ossum.riddl.language.ParsingTest

/** Tests For CheckExamples */
class CheckExamplesSpec extends ParsingTest {

  "RBBQ Documentation" should {
    "compile example correctly" in {
      val directory = "doc/src/hugo/content/examples/rbbq/"
      val file = "ReactiveBBQ.riddl"
      checkFile("Reactive BBQ", file, directory)
    }
    "compile streaming correctly" in {
      val directory = "doc/src/hugo/content/language/hierarchy/domain/streaming/riddl/"
      val file = "plant.riddl"
      checkFile("Streaming", file, directory)
    }
  }
}
