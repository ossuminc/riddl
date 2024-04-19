package com.ossuminc.riddl.hugo.writers

import com.ossuminc.riddl.hugo.writers.MarkdownWriter
import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.passes.symbols.Symbols.Parents

trait SagaWriter { this: MarkdownWriter =>

  private def emitSagaSteps(actions: Seq[SagaStep]): this.type = {
    h2("Saga Actions")
    actions.foreach { step =>
      h3(step.identify)
      emitShortDefDoc(step)
      list(typeOfThing = "Do Statements", step.doStatements.map(_.format), 4)
      list(typeOfThing = "Undo Statements", step.doStatements.map(_.format), 4)
    }
    this
  }

  def emitSaga(saga: Saga, parents: Parents): Unit = {
    containerHead(saga, "Saga")
    emitDefDoc(saga, parents)
    emitOptions(saga.options)
    emitInputOutput(saga.input, saga.output)
    emitSagaSteps(saga.sagaSteps)
    // emitProcessorDetails(saga, parents)
    emitUsage(saga)
    emitTerms(saga.terms)
  }
}
