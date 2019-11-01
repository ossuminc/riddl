package com.yoppworks.ossum.riddl.language

import com.yoppworks.ossum.riddl.language.AST.Domain
import com.yoppworks.ossum.riddl.language.AST.Identifier
import com.yoppworks.ossum.riddl.language.AST.Strng
import com.yoppworks.ossum.riddl.language.AST.Type

/** Unit Tests For Includes  */
class IncludesTest extends ParsingTest {

  "Include" should {
    "handle inclusions into domain" in {
      val domain = checkFile("Domain Includes", "domainIncludes.riddl")
      domain.contents.head.asInstanceOf[Domain].types.head mustBe (
        Type(
          1 -> 1,
          Identifier(1 -> 6, "foo"),
          Strng(1 -> 13),
          None
        )
      )
    }
    "handle inclusions into contexts" in {
      val domain = checkFile("Context Includes", "contextIncludes.riddl")
      domain.contents.head
        .asInstanceOf[Domain]
        .contexts
        .head
        .types
        .head mustBe (
        Type(
          1 -> 1,
          Identifier(1 -> 6, "foo"),
          Strng(1 -> 12),
          None
        )
      )
    }
    "generate error messages from included files" in {
      pending
    }
  }
}
