/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language

import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import com.reactific.riddl.language.Messages._

class MessagesSpec extends AnyWordSpec with Matchers {

  "MessageKind" should {
    "have correct field values" in {
      MissingWarning.isWarning mustBe true
      MissingWarning.isError mustBe false
      MissingWarning.isSevereError mustBe false
      MissingWarning.toString mustBe "Missing"

      StyleWarning.isWarning mustBe true
      StyleWarning.isError mustBe false
      StyleWarning.isSevereError mustBe false
      StyleWarning.toString mustBe "Style"

      Warning.isWarning mustBe true
      Warning.isError mustBe false
      Warning.isSevereError mustBe false
      Warning.toString mustBe "Warning"

      Error.isWarning mustBe false
      Error.isError mustBe true
      Error.isSevereError mustBe false
      Error.toString mustBe "Error"

      SevereError.isWarning mustBe false
      SevereError.isError mustBe true
      SevereError.isSevereError mustBe true
      SevereError.toString mustBe "Severe"
    }
  }
}
