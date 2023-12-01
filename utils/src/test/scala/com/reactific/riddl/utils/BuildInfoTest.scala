package com.ossuminc.riddl.utils
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

class BuildInfoTest extends AnyWordSpec with Matchers {

  "BuildInfo" must {
    "have all the fields" in {
      RiddlBuildInfo.name must be("riddl-utils")
      RiddlBuildInfo.version must startWith regex """[0-9]+."""
      RiddlBuildInfo.builtAtMillis > 0 must be(true)
      RiddlBuildInfo.copyright must include("Ossum")
      RiddlBuildInfo.organization must include("com.ossuminc")
      RiddlBuildInfo.scalaVersion must startWith("3")
    }
  }
}
