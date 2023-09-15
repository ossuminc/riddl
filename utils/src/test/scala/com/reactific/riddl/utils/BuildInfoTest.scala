package com.reactific.riddl.utils
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

class BuildInfoTest extends AnyWordSpec with Matchers {

  "BuildInfo" must {
    "have all the fields" in {
      RiddlBuildInfo.name mustBe "riddl-utils"
      RiddlBuildInfo.version must startWith regex """[0-9]+."""
      RiddlBuildInfo.builtAtMillis > 0 must be(true)
      RiddlBuildInfo.copyright must include("Ossum")
      RiddlBuildInfo.organization must include("com.reactific")
      RiddlBuildInfo.scalaVersion must startWith("3")
    }
  }
}
