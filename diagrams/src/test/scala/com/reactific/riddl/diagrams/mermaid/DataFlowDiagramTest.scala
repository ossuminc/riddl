package com.reactific.riddl.diagrams.mermaid

import com.reactific.riddl.language.parsing.RiddlParserInput
import com.reactific.riddl.testkit.ValidatingTest
import com.reactific.riddl.language.{CommonOptions, Messages}
import com.reactific.riddl.language.AST.*
import com.reactific.riddl.passes.PassesResult

class DataFlowDiagramTest extends ValidatingTest {

  "DataFLowDiagram" must {
    "extract participants" in {
      val input = """
        |domain FlowTheData is {
        |  record TypeA is { ??? }
        |  record TypeB is { ??? }
        |  context Origination is {
        |    inlet In is FlowTheData.TypeB
        |    outlet Out is FlowTheData.TypeA
        |    flow Transformer is {
        |      inlet In is FlowTheData.TypeA
        |      outlet Out is FlowTheData.TypeB
        |    }
        |    connector Source is { flows FlowTheData.TypeA from outlet Origination.Out
        |      to inlet Transformer.In }
        |    connector Transform is { flows FlowTheData.TypeB from outlet Transformer.Out
        |      to inlet Origination.In }
        |  }
        |}
        |""".stripMargin
      parseValidateAndThen(input, CommonOptions.noWarnings) {
        case (result: PassesResult, root: RootContainer, rpi: RiddlParserInput, message: Messages.Messages) =>
          val domain = root.domains.head
          val context = domain.contexts.head
          val connectors = context.connections
          val dfd = DataFlowDiagram(result)
          val diagram = dfd.generate(context)
          println(diagram)
          succeed
      }
    }
  }
}
