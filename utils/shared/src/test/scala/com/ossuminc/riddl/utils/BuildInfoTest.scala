package com.ossuminc.riddl.utils

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.TestData
import java.time.Instant
import java.io.InputStream

class BuildInfoTest extends TestingBasis {

  "BuildInfo" must {
    "have all the fields" in { (td: TestData) =>
      RiddlBuildInfo.name must be("utils")
      RiddlBuildInfo.version must startWith regex """[0-9]+."""
      RiddlBuildInfo.scalaVersion must startWith("3")
      RiddlBuildInfo.sbtVersion must startWith("1.")
      RiddlBuildInfo.normalizedName must be("utils")
      RiddlBuildInfo.moduleName must be("riddl-utils")
      RiddlBuildInfo.description must be("Various utilities used throughout riddl libraries")
      RiddlBuildInfo.organization must include("com.ossuminc")
      RiddlBuildInfo.organizationName must be("Ossum, Inc.")
      RiddlBuildInfo.gitHubOrganization must be("ossuminc")
      RiddlBuildInfo.gitHubRepository must be("riddl")
      RiddlBuildInfo.buildInfoPackage must be("com.ossuminc.riddl.utils")
      RiddlBuildInfo.buildInfoObject must be("RiddlBuildInfo")
      RiddlBuildInfo.copyrightHolder must be("Ossum, Inc.")
      RiddlBuildInfo.organizationHomepage must be("https://ossuminc.com/")
      RiddlBuildInfo.projectHomepage must be("https://github.com/ossuminc/riddl")
      RiddlBuildInfo.licenses must be("Apache-2.0")
      RiddlBuildInfo.buildInfoPackage must be("com.ossuminc.riddl.utils")
      RiddlBuildInfo.buildInfoObject must be("RiddlBuildInfo")
      RiddlBuildInfo.startYear must be("2019")
      RiddlBuildInfo.scalaCompatVersion must be("3.4.3")
      val now: Long = Instant.now().toEpochMilli
      val yesterday: Long = now - 1000 * 24 * 60 * 60
      RiddlBuildInfo.builtAtMillis must be > yesterday
      RiddlBuildInfo.builtAtMillis must be < now
    }
    "has functioning toMap" in { (td: TestData) =>
      val map = RiddlBuildInfo.toMap
      map.size must be(23)
    }
    "has functioning toJson" in { (td: TestData) =>
      val json = RiddlBuildInfo.toJson
      json must not be (empty)
    }
  }
}
