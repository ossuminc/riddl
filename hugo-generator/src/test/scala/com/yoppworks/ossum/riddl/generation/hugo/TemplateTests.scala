package com.yoppworks.ossum.riddl.generation.hugo

import org.scalatest.Assertion
import org.scalatest.TryValues
import org.scalatest.matchers.must
import org.scalatest.wordspec.AnyWordSpec

class TemplateTests extends AnyWordSpec with must.Matchers with TryValues {

  "Templates" should {

    def testReprTemplate(repr: HugoRepr, token: String): Assertion = {
      val templateOrError = Templates.forHugo(repr)
      val template = templateOrError.toTry.success.value
      template.replacementTokens must contain(token)
      val lines = template.toString
      val repLines = template.replace(token, repr.name.capitalize)
      repLines must not equal lines
    }

    "load a valid template for domains" in
      testReprTemplate(HugoDomain("dummy", Namespace.emptyRoot, None), "domainName")

    "load a valid template for contexts" in
      testReprTemplate(HugoContext("dummy", Namespace.emptyRoot, None), "contextName")

    "load a valid template for entity" in testReprTemplate(
      HugoEntity(
        "dummy",
        Namespace.emptyRoot,
        HugoEntity.EntityOption.none,
        Set.empty,
        Set.empty,
        Set.empty,
        Set.empty,
        None
      ),
      "entityName"
    )

    "load a valid template for type" in
      testReprTemplate(HugoType.PredefinedType.Integer, "typeName")

  }

}
