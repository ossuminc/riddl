/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.utils

/** Pins the SHAPE of the attribution block, not its contents.
  *
  * These assertions cannot tell that a dependency was added — no test can, since the list is a
  * hand-maintained constant. What they can do is stop the ways it silently rots: a line growing
  * past 73 columns so it wraps in an 80-column terminal once the `[info] ` prefix is added, a
  * single-platform project losing its marker, and the pointers to the full texts drifting away from
  * the file that actually ships.
  */
class ThirdPartyNoticesTest extends AbstractTestingBasis {

  "ThirdPartyNotices" should {

    "keep every line within 73 columns" in {
      // 73, not 80: `pc.log.info` prefixes each line with "[info] ", which costs 7 columns.
      // 73 + 7 is what actually lands in an 80-column terminal, and budgeting 80 here wrapped
      // the widest lines on screen.
      val tooWide = ThirdPartyNotices.formatted.linesIterator
        .filter(_.length > 73)
        .toSeq
      tooWide mustBe empty
    }

    "mark the projects that ship on only one platform" in {
      val text = ThirdPartyNotices.summary
      text must include("Scala.js runtime library (JS)")
      text must include("Scala Native runtime (Native)")
      text must include("Apache Commons Codec (JVM)")
      text must include("sttp (Native)")
      // ...and explain what the marks mean, rather than leaving them cryptic.
      text must include("ships only in that distribution")
    }

    "name the notices file that the distribution actually ships" in {
      // build.sbt maps THIRD-PARTY-NOTICES.txt into the Universal package under this exact
      // name. If either side is renamed, `riddlc info` points at a file that is not there.
      ThirdPartyNotices.noticesFile mustBe "THIRD-PARTY-NOTICES.txt"
      ThirdPartyNotices.formatted must include("THIRD-PARTY-NOTICES.txt")
    }

    "point at the published license page" in {
      ThirdPartyNotices.noticesUrl mustBe "https://ossum.tech/riddl/2.0/licenses/"
      ThirdPartyNotices.formatted must include(ThirdPartyNotices.noticesUrl)
    }

    "group every license we actually ship under" in {
      val text = ThirdPartyNotices.summary
      text must include("Apache License 2.0")
      text must include("MIT License")
      text must include("BSD 3-Clause License")
    }

    "carry NO copyleft dependency" in {
      // logback-core (EPL-1.0 / LGPL-2.1) used to reach the distribution transitively
      // through airframe-json -> airframe-log, referenced by no riddl source. It is
      // excluded in Dependencies.scala. If it ever returns, this list gains a section
      // and riddl gains an obligation it does not want -- so assert on the ABSENCE.
      val text = ThirdPartyNotices.summary
      text must not include "logback"
      text must not include "LGPL"
      text must not include "Eclipse Public License"
    }

    "not advertise a test framework as a shipped dependency" in {
      // ScalaTest/Scalactic were runtime deps of the JS and Native artifacts because
      // `scalatest_nojvm` was added without `% Test`. Now correctly test-scoped, so
      // they ship in riddl-testkit only -- which is a TEST KIT and not covered here.
      ThirdPartyNotices.summary must not include "ScalaTest"
    }

    "attribute rather than merely list" in {
      // Every dependency line carries a copyright holder; a bare project name is
      // not attribution. Checks a representative from each license group.
      val text = ThirdPartyNotices.summary
      text must include("© The Apache Software Foundation")
      text must include("© Li Haoyi")
      text must include("© Carlos Quiroz")
      text must include("© EPFL")
    }
  }

  "InfoFormatter" should {
    "end with the third-party notices" in {
      // The whole point of the change: `riddlc info` carries the attribution.
      InfoFormatter.formatInfo must include(ThirdPartyNotices.summary)
      InfoFormatter.formatInfo.trim must endWith(
        "riddl itself is © 2019-2026 Ossum Inc., Apache License 2.0."
      )
    }
  }
}
