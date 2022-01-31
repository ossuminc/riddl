package com.yoppworks.ossum.riddl.examples

import com.yoppworks.ossum.riddl.language.ValidatingTest
import com.yoppworks.ossum.riddl.language.Validation.ValidationOptions

import java.io.File

/** Unit Tests To Check Documentation Examples */
class CheckExampleSpec extends ValidatingTest {

  "Reactive BBQ Example" should {
    "parse and validate ReactiveBBQ correctly" in {
      val directory = "example/src/riddl/ReactiveBBQ/"
      val file = new File(directory + "ReactiveBBQ.riddl")
      val options = ValidationOptions(showTimes = true)
      parseAndValidateFile("Reactive BBQ", file, options)
    }
    "parse and validate dokn correctly" in {
      val directory = "example/src/riddl/dokn/"
      val file = new File(directory + "dokn.riddl")
      val options = ValidationOptions(showTimes = true)
      parseAndValidateFile("DokN", file, options)
    }
  }
}
