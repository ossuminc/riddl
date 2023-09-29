package com.reactific.riddl.passes.validate

import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.CommonOptions
import com.reactific.riddl.language.parsing.RiddlParserInput
import com.reactific.riddl.passes.{Pass, PassesResult}
import java.nio.file.Path

/** Unit Tests For ValidationPass */
class ValidationPassTest extends ValidatingTest {

  "ValidationPass" should {
    "parse and validation rbbq.riddl" in {
      val input = RiddlParserInput(Path.of("language/src/test/input/domains/rbbq.riddl"))
      parseAndValidateAggregate(input, CommonOptions.noMinorWarnings) { (vo: PassesResult) =>
        info(vo.messages.format)
        vo.messages.justErrors.size mustBe (0)
        if vo.refMap.size != 24 then info(vo.refMap.toString)

        if vo.usage.usesSize != 24 || vo.usage.usedBySize != 17 then info(vo.usage.toString)

        vo.refMap.size must be(29)
        vo.usage.usesSize must be(28)
        vo.usage.usedBySize must be(23)
      }
    }

    "Validate All Things" must {
      var sharedRoot: RootContainer = RootContainer.empty

      "parse correctly" in {
        val rootFile = "language/src/test/input/full/domain.riddl"
        val parseResult = parseTopLevelDomains(Path.of(rootFile))
        parseResult match {
          case Left(errors) => fail(errors.format)
          case Right(root) =>
            sharedRoot = root
            Pass(root, CommonOptions(showMissingWarnings = false, showStyleWarnings = false), true) match {
              case Left(errors) =>
                fail(errors.format)
              case Right(ao) =>
                ao.root mustBe (sharedRoot)
            }
            succeed
        }
      }
      "handle includes" in {
        val incls = sharedRoot.domains.head.includes
        incls mustNot be(empty)
        incls.head.contents mustNot be(empty)
        incls.head.contents.head.getClass mustBe (classOf[Application])
        incls(1).contents.head.getClass mustBe classOf[Context]
      }
      "have terms and author refs in applications" in {
        val apps = sharedRoot.domains.head.contents
        apps mustNot be(empty)
        apps.head mustBe a[Application]
        val app = apps.head.asInstanceOf[Application]
        app.terms mustNot be(empty)
        app.hasAuthors mustBe true
        app.authors mustNot be(empty)
      }
    }
  }
}
