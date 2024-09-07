package com.ossuminc.riddl.hugo.writers

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.CommonOptions
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.passes.{Riddl,PassesResult}
import org.scalatest.Assertion

import java.nio.file.Path
import com.ossuminc.riddl.utils.URL
import scala.concurrent.{Await,Future}
import scala.concurrent.duration.DurationInt
import scala.concurrent.ExecutionContext.Implicits.global

class AdaptorWriterTest extends WriterTest {

  "AdaptorWriter" must {
    "handle a message rename" in {
      val path = base.resolve("adaptors.riddl")
      val input = RiddlParserInput.fromCwdPath(path)
      validateRoot(input,CommonOptions()) {
        case passesResult: PassesResult =>
          val mkd = makeMDW(output, PassesResult.empty)
          val root = passesResult.root
          val domain = root.domains.head
          val context = domain.contexts.head
          val adaptor = context.adaptors.head
          val parents = Seq(root, domain, context)
          mkd.emitAdaptor(adaptor, parents)
          val result = mkd.toString
          // info(result)
          result mustNot be(empty)
          val expected =
            """---
              |title: "FromTwo"
              |weight: 10
              |draft: "false"
              |description: "No brief description."
              |geekdocAnchor: true
              |geekdocToC: 4
              |geekdocCollapseSection: true
              |---
              |
              |## *Adaptor 'FromTwo'*
              || Item | Value |
              || :---: | :---  |
              || _Briefly_ | No brief description. |
              || _Authors_ |  |
              || _Definition Path_ | Root.Adaptors.One.FromTwo |
              || _View Source Link_ | [hugo/src/test/input/adaptors.riddl(4:5)]() |
              || _Used By_ | None |
              || _Uses_ | None |
              |
              |## *Description*
              |
              |## *RiddlOptions*
              |* option css("background: blue")
              |
              |## *Direction: from context Two*
              |
              |### _Handlers_
              |
              |### _Handler 'Adaptation'_
              || Item | Value |
              || :---: | :---  |
              || _Briefly_ | No brief description. |
              || _Definition Path_ | FromTwo.Root.Adaptors.One.Adaptation |
              || _View Source Link_ | [hugo/src/test/input/adaptors.riddl(6:15)]() |
              || _Used By_ | None |
              || _Uses_ | None |
              |
              |## *Description*
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
