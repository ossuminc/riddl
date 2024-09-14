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
  this: ProcessorParser & StreamingParser & CommonParser =>

  def containedGroup[u: P]: P[ContainedGroup] = {
    P(
      location ~ Keywords.contains ~ identifier ~ as ~ groupRef ~ withDescriptives
    ).map { case (loc, id, group, descriptives) =>
      ContainedGroup(loc, id, group, descriptives)
    }
  }

  private def groupDefinitions[u: P]: P[Seq[OccursInGroup]] = {
    P(
      group | containedGroup | shownBy | output | appInput | comment
    ).asInstanceOf[P[OccursInGroup]].rep(1)
  }

  def group[u: P]: P[Group] = {
    P(
      location ~ groupAliases ~ identifier ~/ is ~ open ~
        (undefined(Seq.empty[OccursInGroup]) | groupDefinitions) ~
        close ~ withDescriptives
    ).map { case (loc, alias, id, contents, descriptives) =>
      Group(loc, alias, id, contents, descriptives)
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
      is ~ open ~ (undefined(Seq.empty[OccursInOutput]) | (output | typeRef).rep(1)) ~ close
    ).?.map {
      case Some(definitions: Seq[OccursInOutput]) => definitions
      case None                                  => Seq.empty[OccursInOutput]
    }
  }

  def output[u: P]: P[Output] = {
    P(
      location ~ outputAliases ~/ identifier ~ presentationAliases ~/
        (literalString | constantRef | typeRef) ~/ outputDefinitions ~ withDescriptives
    ).map { case (loc, nounAlias, id, verbAlias, putOut, contents, descriptives) =>
      putOut match {
        case t: TypeRef =>
          Output(loc, nounAlias, id, verbAlias, t, contents, descriptives)
        case c: ConstantRef =>
          Output(loc, nounAlias, id, verbAlias, c, contents, descriptives)
        case l: LiteralString =>
          Output(loc, nounAlias, id, verbAlias, l, contents, descriptives)
        case x: RiddlValue =>
          // this should never happen but the derived base class, RiddlValue, demands it
          val xval = x.format
          error(s"Expected a type reference, constant reference, or literal string, not: $xval")
          Output(loc, nounAlias, id, verbAlias, LiteralString(loc, s"INVALID: `$xval``"), contents)
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

  def appInput[u: P]: P[Input] = {
    P(
      location ~ inputAliases ~/ identifier ~/ acquisitionAliases ~/ typeRef ~ inputDefinitions ~ withDescriptives
    ).map { case (loc, inputAlias, id, acquisitionAlias, putIn, contents, descriptives) =>
      Input(loc, inputAlias, id, acquisitionAlias, putIn, contents, descriptives)
    }
  }

  private def applicationDefinition[u: P]: P[ApplicationContents] = {
    P(processorDefinitionContents(StatementsSet.ApplicationStatements) | group | applicationInclude)
      .asInstanceOf[P[ApplicationContents]]
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
      location ~ Keywords.application ~/ identifier ~ is ~ open ~ applicationBody ~ close ~ withDescriptives
    )./ map { case (loc, id, contents, descriptives) =>
      checkForDuplicateIncludes(contents)
      Application(loc, id, contents, descriptives)
    }
  }
}
