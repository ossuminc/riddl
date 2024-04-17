package com.ossuminc.riddl.hugo.writers

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.CommonOptions
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.passes.PassesResult
import org.scalatest.Assertion

import java.nio.file.Path

class AdaptorWriterTest extends WriterTest {

  val base = Path.of("hugo", "src", "test", "input")
  "AdaptorWriter" must {
    "handle a message rename" in {
      val output = Path.of("hugo", "target", "test", "adaptors")
      val testFile = base.resolve("adaptors.riddl")
      val input = RiddlParserInput(testFile)
      val options = CommonOptions()
      validateRoot(input,options) {
        case (passesResult: PassesResult) =>
          val mkd = makeMDW(output, PassesResult.empty)
          val root = passesResult.root
          val domain = root.domains.head
          val context = domain.contexts.head
          val adaptor = context.adaptors.head
          val parents = Seq(root, domain, context)
          mkd.emitAdaptor(adaptor, parents)
          val result = mkd.toString
          info(result)
          result mustNot be(empty)
          val expected = """---
              |title: "FromTwo: Adaptor"
              |weight: 10
              |draft: "false"
              |description: "FromTwo has no brief description."
              |geekdocAnchor: true
              |geekdocToC: 4
              |geekdocCollapseSection: true
              |geekdocFilePath: no-such-file
              |---
              |
              |## *Adaptor 'FromTwo'*
              || Item | Value |
              || :---: | :---  |
              || _Briefly_ | Brief description missing. |
              || _Authors_ |  |
              || _Definition Path_ | Root.Adaptors.One.FromTwo |
              || _View Source Link_ | [adaptors.riddl(4:5)]() |
              |
              |## *RiddlOptions*
              |* css("background: blue")
              |
              |## *Used By None*
              |
              |## *Uses Nothing*
              |
              |## *Direction: from context Two*
              |
              |### _Handlers_
              |
              |### _Handler 'Adaptation'_
              || Item | Value |
              || :---: | :---  |
              || _Briefly_ | Brief description missing. |
              || _Definition Path_ | FromTwo.Root.Adaptors.One.Adaptation |
              || _View Source Link_ | [adaptors.riddl(6:15)]() |
              |
              |## *Used By None*
              |
              |## *Uses Nothing*
              |
              |####  On event Adaptors.Two.DidIt
              |```
              |"convert Two.DidIt to One.TwoIsDone"
              |tell command Adaptors.One.TwoIsDone to context One
              |```
              |""".stripMargin

          result must be(expected)
      }
    }
  }
}
