/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.ZoneId
import scala.jdk.CollectionConverters.*

/** JVM only: the JVM is the one platform that HAS a zone database to compare against. The bundled
  * table must never refuse a zone the JDK accepts; when this fails, regenerate [[IanaZoneIds]] from
  * `ZoneId.getAvailableZoneIds` on the new JDK and update its `tzdbVersion`.
  */
class IanaZoneIdsDriftTest extends AnyWordSpec with Matchers {
  "IanaZoneIds" should {
    "contain every zone id the running JDK knows" in {
      val missing = ZoneId.getAvailableZoneIds.asScala.toSeq.sorted.filterNot(IanaZoneIds.ids.contains)
      withClue(s"tzdb ${java.time.zone.ZoneRulesProvider.getVersions("UTC").lastKey}: ") {
        missing mustBe empty
      }
    }
    "contain only ids the JDK can resolve" in {
      val bad = IanaZoneIds.ids.toSeq.sorted.filter(z => scala.util.Try(ZoneId.of(z)).isFailure)
      bad mustBe empty
    }
    "hold the whole table, not a truncated one" in {
      IanaZoneIds.ids.size must be >= 600
    }
  }
}
