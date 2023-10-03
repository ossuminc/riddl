/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language.parsing

import com.reactific.riddl.language.AST.*
import Terminals.{Keywords, *}
import fastparse.*
import fastparse.ScalaWhitespace.*

private[parsing] trait ApplicationParser {
  this: StreamingParser
    with FunctionParser
    with ReferenceParser
    with HandlerParser
    with StatementParser
    with TypeParser =>

  private def applicationOptions[u: P]: P[Seq[ApplicationOption]] = {
    options[u, ApplicationOption](StringIn(Options.technology).!) { case (loc, Options.technology, args) =>
      ApplicationTechnologyOption(loc, args)
    }
  }

  private def groupAliases[u: P]: P[String] = {
    P(
      StringIn(Keywords.group, "page", "pane", "dialog", "popup", "frame", "column", "window", "section", "tab").!
    )
  }

  private def groupDefinitions[u:P]: P[Seq[GroupDefinition]] = {
    P {
      undefined(Seq.empty[GroupDefinition]) | (group | appOutput | appInput).rep(0)
    }
  }

  private def group[u: P]: P[Group] = {
    P(
      location ~ groupAliases ~ identifier ~/ is ~ open ~
         groupDefinitions ~
      close ~ briefly ~ description
    ).map { case (loc, alias, id, elements, brief, description) =>
      Group(loc, alias, id, elements, brief, description)
    }
  }

  private def outputAliases[u: P]: P[String] = {
    P(
      StringIn(
        Keywords.output,
        "document",
        "list",
        "table",
        "graph",
        "animation",
        "picture"
      ).!
    )
  }

  private def presentationAliases[u: P]: P[Unit] = {
    StringIn(Keywords.presents, "shows", "displays", "writes", "emits")
  }

  private def outputDefinitions[u: P]: P[Seq[OutputDefinition]] = {
    P(
      is ~ open ~ appOutput.rep(1) ~ close
    ).?.map {
      case Some(definitions) => definitions
      case None              => Seq.empty[OutputDefinition]
    }
  }

  private def appOutput[u: P]: P[Output] = {
    P(
      location ~ outputAliases ~/ identifier ~ presentationAliases ~/ typeRef ~
        outputDefinitions ~ briefly ~ description
    ).map { case (loc, alias, id, putOut, outputs, brief, description) =>
      Output(loc, alias, id, putOut, outputs, brief, description)
    }
  }

  private def inputAliases[u: P]: P[String] = {
    P(
      StringIn(Keywords.input, "form", "textblock", "button", "picklist").!
    )
  }

  private def inputDefinitions[uP: P]: P[Seq[InputDefinition]] = {
    P(
      is ~ open ~
        (undefined(Seq.empty[InputDefinition]) | appInput.rep(1))
        ~ close
    ).?.map {
      case Some(definitions) => definitions
      case None              => Seq.empty[InputDefinition]
    }
  }

  private def acquisitionAliases[u: P]: P[Unit] = {
    StringIn(Keywords.acquires, "reads", "takes", "accepts", "admits")
  }

  private def appInput[u: P]: P[Input] = {
    P(
      location ~ inputAliases ~/ identifier ~ acquisitionAliases ~ typeRef ~
        inputDefinitions ~ briefly ~ description
    ).map { case (loc, alias, id, putIn, inputs, brief, description) =>
      Input(loc, alias, id, putIn, inputs, brief, description)
    }
  }

  private def applicationDefinition[u: P]: P[ApplicationDefinition] = {
    P(
      group | handler(StatementsSet.ApplicationStatements) | function |
        inlet | outlet | term | typeDef | constant | applicationInclude
    )
  }

  private def applicationDefinitions[u: P]: P[Seq[ApplicationDefinition]] = {
    P(applicationDefinition.rep(0))
  }

  private def applicationInclude[u: P]: P[Include[ApplicationDefinition]] = {
    include[ApplicationDefinition, u](applicationDefinitions(_))
  }

  private def emptyApplication[u: P]: P[(Seq[ApplicationOption], Seq[ApplicationDefinition])] = {
    undefined((Seq.empty[ApplicationOption], Seq.empty[ApplicationDefinition]))
  }

  private def applicationBody[u: P]: P[(Seq[ApplicationOption], Seq[ApplicationDefinition])] = {
    undefined((Seq.empty[ApplicationOption], Seq.empty[ApplicationDefinition]))
  }

  def application[u: P]: P[Application] = {
    P(
      location ~ Keywords.application ~/ identifier ~ authorRefs ~ is ~ open ~
        (emptyApplication | (applicationOptions ~ applicationDefinitions)) ~
        close ~ briefly ~ description
    ).map { case (loc, id, authorRefs, (options, content), brief, desc) =>
      val groups = content.groupBy(_.getClass)
      val types = mapTo[Type](groups.get(classOf[Type]))
      val constants = mapTo[Constant](groups.get(classOf[Constant]))
      val grps = mapTo[Group](groups.get(classOf[Group]))
      val handlers = mapTo[Handler](groups.get(classOf[Group]))
      val functions = mapTo[Function](groups.get(classOf[Function]))
      val inlets = mapTo[Inlet](groups.get(classOf[Inlet]))
      val outlets = mapTo[Outlet](groups.get(classOf[Outlet]))
      val terms = mapTo[Term](groups.get(classOf[Term]))
      val includes = mapTo[Include[ApplicationDefinition]](
        groups.get(
          classOf[Include[ApplicationDefinition]]
        )
      )

      Application(
        loc,
        id,
        options,
        types,
        constants,
        grps,
        handlers,
        inlets,
        outlets,
        functions,
        authorRefs,
        terms,
        includes,
        brief,
        desc
      )

    }
  }
}
