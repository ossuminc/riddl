package app.improving

import com.ossuminc.riddl.testkit.RunCommandSpecBase

/** Generate a test site */
class GenerateTestSiteSpec extends RunCommandSpecBase {

  "GenerateTestSite" should {
    "validate RIDDL and generate Hugo" in {
      val command = Array(
        "--show-times",
        "--verbose",
        "from",
        "src/main/riddl/riddl.conf",
        "validate"
      )
      runWith(command)
    }
  }
}
