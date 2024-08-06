package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.CommonOptions
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.passes.{Pass, PassesResult}
import java.nio.file.Path

/** Unit Tests For ValidationPass */
class ValidationPassTest extends ValidatingTest {

  "ValidationPass" should {
    "parse and validation rbbq.riddl" in {
      val input = RiddlParserInput(Path.of("language/src/test/input/domains/rbbq.riddl"))
      parseAndValidateAggregate(input, CommonOptions.noMinorWarnings) { (vo: PassesResult) =>
        // info(vo.messages.format)
        vo.messages.justErrors.size mustBe 0
        if vo.refMap.size != 24 then info(vo.refMap.toString)

        if vo.usage.usesSize != 24 || vo.usage.usedBySize != 17 then info(vo.usage.toString)

        vo.refMap.size must be(29)
        vo.usage.usesSize must be(28)
        vo.usage.usedBySize must be(23)
      }
    }

    "Validate All Things" must {
      var sharedRoot: Root = Root.empty

      "parse correctly" in {
        val rootFile = "language/src/test/input/full/domain.riddl"
        val parseResult = parseTopLevelDomains(Path.of(rootFile))
        parseResult match {
          case Left(errors) => fail(errors.format)
          case Right(root) =>
            sharedRoot = root
            val result = Pass.runStandardPasses(
              root,
              CommonOptions(showMissingWarnings = false, showStyleWarnings = true)
            )
            if result.messages.hasErrors then fail(result.messages.format)
            else result.root mustBe sharedRoot
        }
      }
      "handle includes" in {
        val incls = sharedRoot.domains.head.includes
        incls mustNot be(empty)
        incls.head.contents mustNot be(empty)
        incls.head.contents.head.getClass mustBe classOf[Application]
        incls(1).contents.head.getClass mustBe classOf[Context]
      }
      "have terms and author refs in applications" in {
        val includes = sharedRoot.domains.head.includes
        includes mustNot be(empty)
        val apps = includes.head.contents.filter[Application]
        apps mustNot be(empty)
        apps.head mustBe a[Application]
        val app = apps.head
        app.terms mustNot be(empty)
        app.hasAuthors mustBe false
        app.hasAuthorRefs mustBe true
        app.authorRefs mustNot be(empty)
      }
    }
  }
}
