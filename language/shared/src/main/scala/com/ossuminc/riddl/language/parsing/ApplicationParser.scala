/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.*
import fastparse.*
import fastparse.MultiLineWhitespace.*

private[parsing] trait ApplicationParser {
  this: ProcessorParser &  StreamingParser  =>

  private def containedGroup[u: P]: P[ContainedGroup] = {
    P(
      location ~ Keywords.contains ~ identifier ~ as ~ groupRef ~ briefly ~ description
    ).map { case (loc, id, group, brief, description) =>
      ContainedGroup(loc, id, group, brief, description)
    }
  }

  private def groupDefinitions[u: P]: P[Seq[OccursInGroup]] = {
    P(group | containedGroup | shownBy | appOutput | appInput | comment).asInstanceOf[P[OccursInGroup]].rep(1)
  }

  private def group[u: P]: P[Group] = {
    P(
      location ~ groupAliases ~ identifier ~/ is ~ open ~
        (undefined(Seq.empty[OccursInGroup]) | groupDefinitions) ~
        close ~ briefly ~ description
    ).map { case (loc, alias, id, contents, brief, description) =>
      Group(loc, alias, id, foldDescriptions[OccursInGroup](contents, brief, description))
    }
  }

  private def presentationAliases[u: P]: P[String] = {
    Keywords
      .keywords(
        StringIn("presents", "shows", "displays", "writes", "emits")
      )
      .!
  }

  private def outputDefinitions[u: P]: P[Seq[OccursInOutput]] = {
    P(
      is ~ open ~
        (undefined(Seq.empty[OccursInOutput]) | appOutput.rep(1)) ~
        close
    ).?.map {
      case Some(definitions: Seq[OccursInInput]) => definitions
      case None                                  => Seq.empty[OccursInOutput]
    }
  }

  private def appOutput[u: P]: P[Output] = {
    P(
      location ~ outputAliases ~/ identifier ~ presentationAliases ~/ (literalString | constantRef | typeRef) ~/
        outputDefinitions ~ briefly ~ description
    ).map { case (loc, nounAlias, id, verbAlias, putOut, contents, brief, description) =>
      putOut match {
        case t: TypeRef =>
          Output(loc, nounAlias, id, verbAlias, t, foldDescriptions[OccursInOutput](contents, brief, description))
        case c: ConstantRef =>
          Output(loc, nounAlias, id, verbAlias, c, foldDescriptions[OccursInOutput](contents, brief, description))
        case l: LiteralString =>
          Output(loc, nounAlias, id, verbAlias, l, foldDescriptions[OccursInOutput](contents, brief, description))
        case x: RiddlValue =>
          // this should never happen but the derived base class, RiddlValue, demands it
          val xval = x.format
          error(s"Expected a type reference, constant reference, or literal string, not: $xval")
          Output(loc, nounAlias, id, verbAlias, LiteralString(loc, s"INVALID: `$xval``"),
            foldDescriptions[OccursInOutput](contents, brief, description))
      }
    }
  }

  private def inputDefinitions[uP: P]: P[Seq[OccursInInput]] = {
    P(
      is ~ open ~
        (undefined(Seq.empty[OccursInInput]) | appInput.rep(1))
        ~ close
    ).?.map {
      case Some(definitions) => definitions
      case None              => Seq.empty[OccursInInput]
    }
  }

  private def acquisitionAliases[u: P]: P[String] = {
    StringIn(
      "acquires",
      "reads",
      "takes",
      "accepts",
      "admits",
      "initiates",
      "submits",
      "triggers",
      "activates",
      "starts"
    ).!
  }

  private def appInput[u: P]: P[Input] = {
    P(
      location ~ inputAliases ~/ identifier ~/ acquisitionAliases ~/ typeRef ~
        inputDefinitions ~ briefly ~ description
    ).map { case (loc, inputAlias, id, acquisitionAlias, putIn, contents, brief, description) =>
      val folded = foldDescriptions[OccursInInput](contents, brief, description)
      Input(loc, inputAlias, id, acquisitionAlias, putIn, folded)
    }
  }

  private def applicationDefinition[u: P]: P[ApplicationContents] = {
    P( processorDefinitionContents(StatementsSet.ApplicationStatements) | group | applicationInclude
    ).asInstanceOf[P[ApplicationContents]]
  }

  private def applicationDefinitions[u: P]: P[Seq[ApplicationContents]] = {
    P(applicationDefinition.rep(0, comments))
  }

  private def applicationInclude[u: P]: P[Include[ApplicationContents]] = {
    include[u, ApplicationContents](applicationDefinitions(_))
  }

  private def emptyApplication[u: P]: P[Seq[ApplicationContents]] = {
    undefined(Seq.empty[ApplicationContents])
  }

  private def applicationBody[u: P]: P[Seq[ApplicationContents]] = {
    emptyApplication | applicationDefinitions
  }

  def application[u: P]: P[Application] = {
    P(
      location ~ Keywords.application ~/ identifier ~ is ~ open ~ applicationBody ~ close ~ briefly ~ description
    ).map { case (loc, id, contents, brief, description) =>
      checkForDuplicateIncludes(contents)
      Application(loc, id, foldDescriptions[ApplicationContents](contents, brief, description))
    }
  }
}
