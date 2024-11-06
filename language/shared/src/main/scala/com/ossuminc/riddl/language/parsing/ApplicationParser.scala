/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.utils.PlatformContext
import fastparse.*
import fastparse.MultiLineWhitespace.*

private[parsing] trait ApplicationParser(using PlatformContext) {
  this: ProcessorParser & StreamingParser & CommonParser =>

  def containedGroup[u: P]: P[ContainedGroup] = {
    P(
      Index ~ Keywords.contains ~ identifier ~ as ~ groupRef ~ withMetaData ~ Index
    ).map { case (start, id, group, descriptives, end) =>
      ContainedGroup(at(start, end), id, group, descriptives.toContents)
    }
  }

  private def groupDefinitions[u: P]: P[Seq[OccursInGroup]] = {
    P(
      group | containedGroup | shownBy | appOutput | appInput | comment
    ).asInstanceOf[P[OccursInGroup]].rep(1)
  }

  def group[u: P]: P[Group] = {
    P(
      Index ~ groupAliases ~ identifier ~/ is ~ open ~
        (undefined(Seq.empty[OccursInGroup]) | groupDefinitions) ~
        close ~ withMetaData ~ Index
    ).map { case (start, alias, id, contents, descriptives, end) =>
      Group(at(start, end), alias, id, contents.toContents, descriptives.toContents)
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
      is ~ open ~ (undefined(Seq.empty[OccursInOutput]) | (appOutput | typeRef).rep(1)) ~ close
    ).?.map {
      case Some(definitions: Seq[OccursInOutput]) => definitions
      case None                                   => Seq.empty[OccursInOutput]
    }
  }

  def appOutput[u: P]: P[Output] = {
    P(
      Index ~ outputAliases ~/ identifier ~ presentationAliases ~/
        (literalString | constantRef | typeRef) ~/ outputDefinitions ~ withMetaData ~ Index
    ).map { case (start, nounAlias, id, verbAlias, putOut, contents, descriptives, end) =>
      val loc = at(start, end)
      putOut match {
        case t: TypeRef =>
          Output(loc, nounAlias, id, verbAlias, t, contents.toContents, descriptives.toContents)
        case c: ConstantRef =>
          Output(loc, nounAlias, id, verbAlias, c, contents.toContents, descriptives.toContents)
        case l: LiteralString =>
          Output(loc, nounAlias, id, verbAlias, l, contents.toContents, descriptives.toContents)
        case x: RiddlValue =>
          // this should never happen but the derived base class, RiddlValue, demands it
          val xval = x.format
          error(loc, s"Expected a type reference, constant reference, or literal string, not: $xval")
          Output(
            loc,
            nounAlias,
            id,
            verbAlias,
            LiteralString(loc, s"INVALID: `$xval``"),
            contents.toContents,
            descriptives.toContents
          )
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
      Index ~ inputAliases ~/ identifier ~/ acquisitionAliases ~/ typeRef ~ inputDefinitions ~ withMetaData ~ Index
    ).map { case (start, inputAlias, id, acquisitionAlias, putIn, contents, descriptives, end) =>
      Input(at(start, end), inputAlias, id, acquisitionAlias, putIn, contents.toContents, descriptives.toContents)
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
      Index ~ Keywords.application ~/ identifier ~ is ~ open ~ applicationBody ~ close ~ withMetaData ~ Index
    )./ map { case (start, id, contents, descriptives, end) =>
      checkForDuplicateIncludes(contents)
      Application(at(start, end), id, contents.toContents, descriptives.toContents)
    }
  }
}
