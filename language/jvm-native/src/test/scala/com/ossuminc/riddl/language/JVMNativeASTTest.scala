/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.parsing.Keyword
import com.ossuminc.riddl.language.{AST, At}
import com.ossuminc.riddl.utils.{AbstractTestingBasis, pc}
import wvlet.airframe.ulid.ULID

class JVMNativeASTTest extends AbstractTestingBasis {

  "RiddlValue" must {
    "allow attachments to be added programmatically" in {
      val container = Entity(At.empty, Identifier.empty)
      val a = StringAttachment(At.empty, Identifier(At.empty, "foo"), "application/json", LiteralString(At.empty, "{}"))
      container.metadata += a
      container.metadata.filter[StringAttachment] match
        case Seq(value) if value == a => succeed
        case _                        => fail("No go")
    }
  }
  "Include" should {
    "identify as root container, etc" in {
      import com.ossuminc.riddl.utils.URL
      val incl = Include(At.empty, URL.empty, Contents.empty)
      incl.isRootContainer mustBe true
      incl.loc mustBe At.empty
      incl.format mustBe "include \"\""
    }
  }
  "have useful FileDescription" in {
    import com.ossuminc.riddl.utils.URL
    val fd = URLDescription(At(), URL("file:///."))
    fd.format must include("/")
    fd.format must include(".")
  }
}
