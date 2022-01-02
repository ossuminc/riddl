/** Unit Tests To Check Documentation Examples */

import com.yoppworks.ossum.riddl.language.ParsingTest

/** Tests For CheckExamples */
class CheckExampleSpec extends ParsingTest {

  "RBBQ Documentation" should {
    "compile example correctly" in {
      val directory = "example/src/riddl/ReactiveBBQ/"
      val file = "ReactiveBBQ.riddl"
      checkFile("Reactive BBQ", file, directory)
    }
  }
}
