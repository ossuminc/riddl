package com.reactific.riddl.language

import com.reactific.riddl.testkit.ValidatingTest
import org.scalatest.Assertion

import java.nio.file.Path

/** Unit Tests For ExamplesTest */
class ExamplesTest extends ValidatingTest {

  val dir = "testkit/src/test/input/"

  def doOne(fileName: String): Assertion = {
    parseAndValidateFile(
      Path.of(dir, fileName).toFile,
      CommonOptions(
        showTimes = true,
        showWarnings = false,
        showMissingWarnings = false,
        showStyleWarnings = false
      )
    )
  }

  "Examples" should {
    "compile Reactive BBQ" in { doOne("rbbq.riddl") }
    "compile Empty" in { doOne("empty.riddl") }
    "compile Pet Store" in { doOne("petstore.riddl") }
    "compile Everything" in { doOne("everything.riddl") }
    "compile dokn" in { doOne("dokn.riddl") }
  }
}
