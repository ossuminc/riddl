/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.parsing.Keyword
import com.ossuminc.riddl.language.{AST, At}
import com.ossuminc.riddl.utils.{AbstractTestingBasis, pc}
import wvlet.airframe.ulid.ULID

class JVMASTTest extends AbstractTestingBasis {

  "Include" should {
    "have useful URLDescription" in {
      import com.ossuminc.riddl.utils.URL
      val url_text = "https://raw.githubusercontent.com/ossuminc/riddl/main/project/plugins.sbt"
      val url: URL = URL(url_text)
      val ud = URLDescription(At(), url_text)
      ud.loc.isEmpty mustBe true
      // An already-absolute path is used AS-IS by `toURL`; only a relative one is resolved
      // against `loc.source.root`.
      ud.toURL.toExternalForm must be(url_text)
      ud.format must be(url_text)
      val lines: scala.collection.Seq[String] = ud.lines.map(_.s)
      val head = lines.head
      head must include("ossuminc")
    }
  }
}
