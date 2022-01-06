package com.yoppworks.ossum.riddl.examples

import com.yoppworks.ossum.riddl.language.ValidatingTest
import com.yoppworks.ossum.riddl.language.Validation.ValidationOptions

import java.io.File

/** Unit Tests To Check Documentation Examples */
class CheckExampleSpec extends ValidatingTest {

  "Reactive BBQ Example" should {
    "parse and validate correctly" in {
      val directory = "example/src/riddl/ReactiveBBQ/"
      val file = new File(directory + "ReactiveBBQ.riddl")
      val options = ValidationOptions(showTimes = true)
      parseAndValidateFile("Reactive BBQ", file, options)
    }
  }
}
