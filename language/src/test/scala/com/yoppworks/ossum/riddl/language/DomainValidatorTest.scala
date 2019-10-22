package com.yoppworks.ossum.riddl.language

import com.yoppworks.ossum.riddl.language.AST.DomainDef
import com.yoppworks.ossum.riddl.language.AST.Identifier
import com.yoppworks.ossum.riddl.language.AST.RootContainer

/** Unit Tests For ValidatorTest */
class DomainValidatorTest extends ValidatingTest {

  "DomainValidator" should {
    "identify duplicate domain definitions" in {
      val errors = Validation.validate(
        RootContainer(
          Seq(
            DomainDef((0, 0), Identifier((0, 0), "foo")),
            DomainDef((1, 1), Identifier((1, 1), "foo"))
          )
        ),
        Seq.empty[Validation.ValidationOptions]
      )
      errors.nonEmpty mustBe true
      errors.head.message.contains("'foo' is defined multiple times") mustBe
        true
    }
  }
}
