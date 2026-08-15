/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.{*}
import com.ossuminc.riddl.language.{Contents, *}
import fastparse.*
import fastparse.MultiLineWhitespace.*

private[parsing] trait GroupParser extends CommonParser:

  def containedGroup[u: P]: P[ContainedGroup] = {
    P(
      Index ~ Keywords.contains ~ identifier ~ as ~ groupRef ~ withMetaData ~ Index
    ).map { case (start, id, group, descriptives, end) =>
      ContainedGroup(at(start, end), id, group, descriptives.toContents)
    }
  }

  private def groupDefinitions[u: P]: P[Seq[OccursInGroup]] = {
    // Every alternative here is a member of OccursInGroup, `comment` and `shownBy` included; the
    // cast bridges fastparse's inferred common supertype, not a gap in the union.
    P(
      group | containedGroup | shownBy | groupOutput | groupInput | comment
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

  /** A46: the presentation verbs. `presents`/`shows`/`displays`/`writes`/`emits` are the original
    * five; the rest were accepted with A43's modality aliases and pair with them -- `plays` for a
    * sound or animation, `speaks`/`announces` for speech, `vibrates`/`pulses`/`nudges` for
    * haptics, `diffuses` for scent, and `serve`/`offer`/`taste` for taste.
    *
    * **The mixed grammatical person is deliberate and is the author's list verbatim** (Reid,
    * 2026-08-14, asked directly). Note this is the OPPOSITE call to the one recorded on
    * `acquisitionAliases`, which is third-person with `activate` as a single deliberate exception
    * and argues against pairing a whole vocabulary. Both stand: the verb is the SME's word choice,
    * and these are the words the author wants available. Do not "regularise" `serve`/`offer`/
    * `taste` into third person to match their neighbours.
    */
  private def presentationAliases[u: P]: P[String] = {
    Keywords
      .keywords(
        StringIn(
          "presents",
          "shows",
          "displays",
          "writes",
          "emits",
          "plays",
          "speaks",
          "announces",
          "vibrates",
          "pulses",
          "nudges",
          "diffuses",
          "serve",
          "offer",
          "taste"
        )
      )
      .!
  }

  private def outputDefinitions[u: P]: P[Seq[OccursInOutput]] = {
    P(
      is ~ open ~ (undefined(Seq.empty[OccursInOutput]) |
        (groupOutput | typeRef).asInstanceOf[P[OccursInOutput]].rep(1)) ~ close
    ).?.map {
      case Some(definitions: Seq[OccursInOutput]) => definitions
      case None                                   => Seq.empty[OccursInOutput]
    }
  }

  private def groupOutput[u: P]: P[Output] = {
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
          error(
            loc,
            s"Expected a type reference, constant reference, or literal string, not: $xval"
          )
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
        (undefined(Seq.empty[OccursInInput]) | groupInput.rep(1))
        ~ close
    ).?.map {
      case Some(definitions) => definitions
      case None              => Seq.empty[OccursInInput]
    }
  }

  private def acquisitionAliases[u: P]: P[String] = {
    // A44: input interaction verbs. Selection verbs (selects/chooses/picks) imply the
    // acquired value is one of a closed set of choices; the rest are entry/workflow verbs.
    // Keep this whitelist in sync with the EBNF `acquisition_aliases` rule and with
    // `UIVerbs.selectionVerbs` used by input validation.
    //
    // The list is THIRD-PERSON SINGULAR, with one deliberate exception: `activate`.
    // `button Checkout activate Confirmation` is the reading most authors reach for when the
    // input is a button, and the failure was a bare parse error at the verb with no
    // suggestion. The imperative forms of the neighbours (`trigger`, `start`, `submit`,
    // `select`, …) are NOT accepted -- pairing the whole vocabulary would double what a reader
    // must recognise for no gain, since only the button reading is idiomatic. Requested by
    // ossum.tech, 2026-07-31.
    StringIn(
      "acquires",
      "reads",
      "takes",
      "accepts",
      "admits",
      "enters",
      "provides",
      "selects",
      "chooses",
      "picks",
      "initiates",
      "submits",
      "triggers",
      "activates",
      "activate",
      "starts"
    ).!
  }

  private def groupInput[u: P]: P[Input] = {
    P(
      Index ~ inputAliases ~/ identifier ~/ acquisitionAliases ~/ typeRef ~ inputDefinitions ~ withMetaData ~ Index
    ).map { case (start, inputAlias, id, acquisitionAlias, putIn, contents, descriptives, end) =>
      Input(
        at(start, end),
        inputAlias,
        id,
        acquisitionAlias,
        putIn,
        contents.toContents,
        descriptives.toContents
      )
    }
  }
end GroupParser
