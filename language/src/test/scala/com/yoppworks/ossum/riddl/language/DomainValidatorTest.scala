package com.yoppworks.ossum.riddl.language

import com.yoppworks.ossum.riddl.language.AST.Domain
import com.yoppworks.ossum.riddl.language.AST.Identifier
import com.yoppworks.ossum.riddl.language.AST.RootContainer
import com.yoppworks.ossum.riddl.language.Validation.ValidationOptions

/** Unit Tests For ValidatorTest */
class DomainValidatorTest extends ValidatingTest {

  "DomainValidator" should {
    "identify duplicate domain definitions" in {
      val errors = Validation.validate(
        RootContainer(Seq(
          Domain((0, 0), Identifier((0, 0), "foo")),
          Domain((1, 1), Identifier((1, 1), "foo"))
        )),
        ValidationOptions.Default
      )
      errors must not be (empty)
      errors.head.message must include("'foo' is defined multiple times")
    }
  }
}
