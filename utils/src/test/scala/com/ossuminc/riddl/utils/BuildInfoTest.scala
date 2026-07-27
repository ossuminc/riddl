/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.utils

import org.scalatest.TestData
import java.time.Instant

class BuildInfoTest extends AbstractTestingBasisWithTestData {

  "BuildInfo" must {
    "have all the fields" in { (td: TestData) =>
      println(td.name)
      RiddlBuildInfo.name must be("utils")
      RiddlBuildInfo.version must startWith regex """[0-9]+."""
      RiddlBuildInfo.gitCommit must not be empty
      RiddlBuildInfo.scalaVersion must startWith("3")
      RiddlBuildInfo.sbtVersion must startWith("2.")
      RiddlBuildInfo.normalizedName must be("utils")
      RiddlBuildInfo.moduleName must be("riddl-utils")
      RiddlBuildInfo.description must be("Various utilities used throughout riddl libraries")
      RiddlBuildInfo.organization must include("com.ossuminc")
      RiddlBuildInfo.organizationName must be("Ossum Inc.")
      RiddlBuildInfo.gitHubOrganization must be("ossuminc")
      RiddlBuildInfo.gitHubRepository must be("riddl")
      RiddlBuildInfo.buildInfoPackage must be("com.ossuminc.riddl.utils")
      RiddlBuildInfo.buildInfoObject must be("RiddlBuildInfo")
      RiddlBuildInfo.copyrightHolder must be("Ossum Inc.")
      RiddlBuildInfo.organizationHomepage must be("https://ossuminc.com/")
      RiddlBuildInfo.projectHomepage must be("https://github.com/ossuminc/riddl")
      RiddlBuildInfo.licenses must include("Apache-2.0")
      RiddlBuildInfo.buildInfoPackage must be("com.ossuminc.riddl.utils")
      RiddlBuildInfo.buildInfoObject must be("RiddlBuildInfo")
      RiddlBuildInfo.startYear must be("2019")
      RiddlBuildInfo.scalaCompatVersion must be(RiddlBuildInfo.scalaVersion)
      val now: Long = Instant.now().toEpochMilli
      val yesterday: Long = now - 1000 * 24 * 60 * 60
      RiddlBuildInfo.builtAtMillis must be > yesterday
      RiddlBuildInfo.builtAtMillis must be < now
    }
    "has functioning toMap" in { (td: TestData) =>
      println(td.name)
      val map = RiddlBuildInfo.toMap
      // The JVM row has 25 keys (24 from sbt-buildinfo plus the gitCommit key sbt-ossuminc adds);
      // the JS and Native rows add one more for their platform version (e.g. scalaJSVersion). A
      // hardcoded count is therefore platform-dependent, so assert the actual contract: every
      // field BuildInfo advertises is present in the map.
      map.size must be >= 25
      map.keySet must contain allOf (
        "name",
        "version",
        "scalaVersion",
        "sbtVersion",
        "normalizedName",
        "moduleName",
        "description",
        "organization",
        "organizationName",
        "gitHubOrganization",
        "gitHubRepository",
        "copyrightHolder",
        "organizationHomepage",
        "projectHomepage",
        "licenses",
        "isSnapshot",
        "buildInfoPackage",
        "buildInfoObject",
        "startYear",
        "copyright",
        "scalaCompatVersion",
        "buildInstant",
        "gitCommit",
        "builtAtString",
        "builtAtMillis"
      )
      map("moduleName") must be(RiddlBuildInfo.moduleName)
    }
    "has functioning toJson" in { (td: TestData) =>
      println(td.name)
      val json = RiddlBuildInfo.toJson
      json must not be (empty)
    }
  }
}
