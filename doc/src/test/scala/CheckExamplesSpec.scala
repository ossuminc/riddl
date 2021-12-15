/** Unit Tests To Check Documentation Examples */

import com.yoppworks.ossum.riddl.language.ParsingTest

/** Tests For CheckExamples */
class CheckExamplesSpec extends ParsingTest {

  "RBBQ Documentation Example" should {
    "compile correctly" in {
      val directory = "doc/src/hugo/content/examples/rbbq/"
      val file = "ReactiveBBQ.riddl"
      checkFile("Reactive BBQ", file, directory)
    }
  }
}
