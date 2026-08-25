/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.utils.{PlatformContext, URL}
import com.ossuminc.riddl.language.AST.{*}
import com.ossuminc.riddl.language.{Contents, *}
import com.ossuminc.riddl.language.At
import fastparse.*
import fastparse.MultiLineWhitespace.*
import wvlet.airframe.ulid.ULID

import java.lang.Character.isLowerCase
import java.net.URI
import java.nio.file.Files
import scala.reflect.{ClassTag, classTag}
import scala.concurrent.Future

/** Common Parsing Rules */
private[parsing] trait CommonParser(using pc: PlatformContext)
    extends ReferenceParser
    with Readability
    with NoWhiteSpaceParsers
    with ParsingContext {

  def open[u: P]: P[Unit] = {
    P(Punctuation.curlyOpen)
  }

  def close[u: P]: P[Unit] = {
    P(Punctuation.curlyClose)
  }

  def is[u: P]: P[Unit] = Keywords.keywords(StringIn("is", "are", ":", "=")).?

  def byAs[u: P]: P[Unit] = Keywords.keywords(StringIn("by", "as"))

  def author[u: P]: P[Author] =
    P(
      Index ~ Keywords.author ~/ identifier ~ is ~ open ~
        ((undefined(
          (
            LiteralString(At(), ""),
            LiteralString(At(), ""),
            Option.empty[LiteralString],
            Option.empty[LiteralString],
            Option.empty[URL]
          )
        ) |
          (Keywords.name ~ is ~ literalString ~ Keywords.email ~ is ~
            literalString ~ (Keywords.organization ~ is ~ literalString).? ~
            (Keywords.title ~ is ~ literalString).? ~
            (Keywords.url ~ is ~ httpUrl).?))) ~ close ~ withMetaData ~/ Index
    ).map { case (start, id, (name, email, org, title, url), descriptives, end) =>
      Author(at(start, end), id, name, email, org, title, url, descriptives.toContents)
    }
  end author

  /** A53: a version component is EITHER a name OR a natural number, never both.
    *
    * The name uses the ordinary `identifier` production so a composed coordinate can never contain
    * characters a generator would have to sanitize. The numeric form uses `naturalNumber` (not
    * `integer`) deliberately: a version component may not carry a sign, and `integer` accepts a
    * leading `+`/`-`. The two alternatives share no first character, so their order is immaterial.
    */
  private def versionComponent[u: P]: P[(Identifier, Option[Long])] =
    P(
      (Index ~ naturalNumber ~~ Index).map { case (start, n, end) =>
        (Identifier(at(start, end), n.toString), Some(n))
      } | identifier.map(id => (id, Option.empty[Long]))
    )

  /** A53: `version <identifier>` or `version <naturalNumber>` */
  def versionDef[u: P]: P[Version] =
    P(
      Index ~ Keywords.version ~/ versionComponent ~ withMetaData ~/ Index
    ).map { case (start, (id, number), descriptives, end) =>
      Version(at(start, end), id, number, descriptives.toContents)
    }
  end versionDef

  /** A47: `copyright <identifier> is "<notice>"`.
    *
    * The notice is a [[LiteralString]] taken VERBATIM and in its entirety — the © symbol, the year
    * and the holder all live inside the quotes, because notices vary by jurisdiction and holder and
    * any decomposition would be wrong somewhere. The definition is NAMED so a documentation
    * generator can gather a model's distinct notices and attribute each properly.
    */
  def copyrightDef[u: P]: P[Copyright] =
    P(
      Index ~ Keywords.copyright ~/ identifier ~ is ~ literalString ~ withMetaData ~/ Index
    ).map { case (start, id, text, descriptives, end) =>
      Copyright(at(start, end), id, text, descriptives.toContents)
    }
  end copyrightDef

  /** Parse importable definition kinds for selective imports */
  private def importableKind[u: P]: P[String] = {
    P(
      Keywords.keywords(
        StringIn(
          "domain",
          "context",
          "entity",
          "type",
          "epic",
          "saga",
          "adaptor",
          "function",
          "projector",
          "repository",
          "streamlet",
          "author",
          "module",
          "user",
          "connector",
          "constant",
          "invariant"
        ).!
      )
    )
  }

  /** Keyword "as" for aliasing in selective imports */
  private def as_[u: P]: P[Unit] = Keywords.keyword("as")

  /** Everything after the `im` + `port` keyword: the kind/selector (selective only), the path, and
    * the optional alias.
    */
  private type ImportTail = (Option[String], Option[Identifier], LiteralString, Option[Identifier])

  /** The tail of a selective load: `<kind> X from "file.bast" [as Alias]` */
  private def selectiveImportTail[u: P]: P[ImportTail] = {
    P(importableKind ~ identifier ~ from ~ literalString ~ (as_ ~ identifier).?).map {
      case (kind, selector, path, alias) => (Some(kind), Some(selector), path, alias)
    }
  }

  /** The tail of a full load: just `"file.bast"` */
  private def fullImportTail[u: P]: P[ImportTail] = {
    P(literalString).map(path => (None, None, path, None))
  }

  /** Parse a BAST load statement (selective or full).
    *
    * Syntax variants:
    *   - Full: `import "path/to/file.bast"`
    *   - Selective: `import domain X from "file.bast"`
    *   - Aliased: `import type T from "file.bast" as MyT`
    *
    * NOTE on shape: the keyword is matched ONCE, ahead of the choice between the two tails.
    * `Keywords.keyword` ends in a cut (`./`), so spelling this as `(kw ~ selectiveTail) | (kw ~
    * fullTail)` makes the first alternative's cut poison the choice and the full form becomes
    * unparseable. Hoisting the keyword out keeps the choice backtrackable (fastparse resets the cut
    * flag when it enters each alternative of a `|`).
    */
  def bastImport[u: P]: P[BASTImport] = {
    P(Index ~ Keywords.import_ ~ (selectiveImportTail | fullImportTail) ~ Index).map {
      case (start, (kind, selector, path, alias), end) =>
        doBASTImport(at(start, end), path, kind, selector, alias)
    }
  }

  /** `???` — a body with nothing in it yet, optionally preceded by COMMENTS saying what belongs
    * there. A commented stub is what a "start from scratch" template looks like, and before this
    * the comment made the body unparseable: once a comment was consumed as ordinary content, the
    * `???` branch of `<container>_body` was no longer reachable.
    *
    * Comments AFTER `???` are deliberately not accepted — `???` ends the body.
    *
    * The comments are KEPT, as the container's own contents, rather than discarded. RIDDL is
    * reflective: a comment that parsed but could not be emitted would vanish on the next prettify.
    * `Comment` is a member of every contents union that has a `???` alternative, so returning them
    * in place of the empty sequence is well-typed in practice; the one call site whose result is
    * not a sequence (a saga's requires/returns triple) keeps the empty value it asked for.
    */
  def undefined[u: P, RT](f: => RT): P[RT] = {
    P(comment.rep ~ Punctuation.undefinedMark./).map { comments =>
      if comments.isEmpty then f
      else
        f match
          case empty: Seq[?] if empty.isEmpty => comments.asInstanceOf[RT]
          case other                          => other
    }
  }

  def literalStrings[u: P]: P[Seq[LiteralString]] = { P(literalString.rep(1)) }

  /** One string bare, or several inside braces -- the same shape `docBlock` uses for prose.
    *
    * The bare form takes EXACTLY ONE string, deliberately. Making it `literalStrings` would admit
    * `do "a" "b"` by juxtaposition, and while that parses unambiguously (no statement begins with a
    * quote), nothing would mark where the statement ends except the next keyword. The braces make
    * the extent explicit, and this is the spelling RIDDL already uses for multi-line prose.
    */
  def literalStringBlock[u: P]: P[Seq[LiteralString]] = {
    P((open ~ literalStrings ~ close) | literalString.map(Seq(_)))
  }

  def markdownLines[u: P]: P[Seq[LiteralString]] = {
    P(markdownLine.rep(1))
  }

  def maybe[u: P](keyword: String): P[Unit] = P(keyword).?

  private def briefDescription[u: P]: P[BriefDescription] = {
    P(Index ~ Keywords.briefly ~ byAs.? ~ literalString ~~ Index).map {
      case (off1, brief: LiteralString, off2) =>
        BriefDescription(at(off1, off2), brief)
    }
  }

  private def docBlock[u: P]: P[Seq[LiteralString]] = {
    P(
      (open ~
        (markdownLines | literalStrings | undefined(Seq.empty[LiteralString])) ~
        close) | literalString.map(Seq(_))
    )
  }

  def description[u: P](implicit ctx: P[?]): P[Description] =
    P(
      Index ~ Keywords.described ~ (
        (byAs ~/ docBlock) |
          (at ~/ httpUrl) |
          (in ~/ Keywords.file ~ literalString)
      ) ~ Index
    ).map {
      case (off1, strings: Seq[LiteralString], off2) => BlockDescription(at(off1, off2), strings)
      // `described at <httpUrl>` is already absolute, so its text IS the path.
      case (off1, url: URL, off2) => URLDescription(at(off1, off2), url.toExternalForm)
      // `described in file "X.md"` keeps the AUTHORED string. It used to be resolved here against
      // the source root, which destroyed the relative form the author wrote and made prettify emit
      // a machine-specific absolute path. Resolution now happens in `URLDescription.toURL`, which
      // gets the basis from `loc.source.root` at the moment the content is actually loaded.
      case (off1, file: LiteralString, off2) => URLDescription(at(off1, off2), file.s)
    }

  def maybeDescription[u: P]: P[Option[Description]] =
    P(description).?

  private def inlineComment[u: P]: P[InlineComment] = {
    P(
      Index ~ "/*" ~ until('*', '/') ~ Index
    ).map { case (off1, comment, off2) =>
      val actual = comment.dropRight(2) // we don't want the */ in the comment text
      val lines = actual.split('\n').toList
      InlineComment(at(off1, off2), lines)
    }
  }

  private def endOfLineComment[u: P]: P[LineComment] = {
    P(Index ~ "//" ~ toEndOfLine ~~ Index).map { case (off1, comment, off2) =>
      LineComment(at(off1, off2), comment)
    }
  }

  def comment[u: P]: P[Comment] = {
    P(inlineComment | endOfLineComment)
  }

  def comments[u: P]: P[Seq[Comment]] = {
    P(comment).rep(0)
  }

  /** An unsigned, non-negative whole number. A53 promoted this from private so version components
    * (which may not carry a sign) can use it directly rather than `integer`.
    */
  def naturalNumber[u: P]: P[Long] = {
    // MUST be `CharsWhileIn`, not `CharIn(...).rep(1)`. Under `MultiLineWhitespace`, fastparse's
    // `.rep` skips whitespace BETWEEN repetitions regardless of `~~` at the surrounding call
    // sites, so `CharIn("0-9").rep(1)` matched "1 2" as a single run of digit-repetitions and
    // `.!` captured the literal text "1 2" -- which then threw `NumberFormatException` out of
    // `.toLong` instead of failing to parse. Verified empirically against fastparse 3.1.1.
    // `CharsWhileIn` is a run primitive with no such gap.
    CharsWhileIn("0-9").!.map(_.toLong)
  }

  /** A signed whole number. The sign used to be matched but DISCARDED, so `-3` silently parsed as
    * `3` — a range of `range(-5, 5)` came out as `range(5, 5)` and an enumerator value of `(-1)`
    * became `1`. The sign is now applied.
    */
  def integer[u: P]: P[Long] = {
    (CharIn("+\\-").!.? ~~ naturalNumber).map {
      case (Some("-"), n) => -n
      case (_, n)         => n
    }
  }

  private def simpleIdentifier[u: P]: P[String] = {
    // An identifier may not be spelled as a keyword that INTRODUCES a definition, which is what
    // made `domain domain is { … }` parse. Only that set — not every keyword — because models
    // legitimately name fields `version` or `copyright`, which A53/A47 kept working on purpose.
    // Case-sensitive, so `Domain` is unaffected; a quoted identifier ('domain') still works.
    P(CharIn("a-zA-Z") ~~ CharsWhileIn("a-zA-Z0-9_\\-").?).!.filter(id =>
      !Keyword.definitionKeywords.contains(id)
    )
  }

  private def quotedIdentifier[u: P]: P[String] = {
    P("'" ~~ CharsWhileIn("a-zA-Z0-9_+\\-|/@$%&, :", 1).! ~~ "'")
  }

  private def anyIdentifier[u: P]: P[String] = {
    P(simpleIdentifier | quotedIdentifier)
  }

  def identifier[u: P]: P[Identifier] = {
    P(Index ~ anyIdentifier ~~ Index).map { case (off1, value, off2) =>
      Identifier(at(off1, off2), value)
    }
  }

  private def dottedPathIdentifier[u: P]: P[Seq[String]] = {
    P(anyIdentifier ~~ (Punctuation.dot ~~ anyIdentifier).repX(0)).map { case (first, strings) =>
      first +: strings
    }
  }

  /** A whole path wrapped in a single pair of quotes with `.` separating the components, e.g.
    * `'a.CI/CD Pipeline.c'`. This lets an emitter quote a path containing special-character
    * components without quoting each component. The character class is `quotedIdentifier`'s set
    * plus `.`.
    */
  private def quotedPathIdentifier[u: P]: P[Seq[String]] = {
    P("'" ~~ CharsWhileIn("a-zA-Z0-9_+\\-|/@$%&, :.", 1).! ~~ "'").map { s =>
      s.split('.').toIndexedSeq
    }
  }

  def pathIdentifier[u: P]: P[PathIdentifier] = {
    // Try the dotted form first so existing inputs (including per-component
    // quoted parts like `a.'x'.b`) parse unchanged; fall back to the
    // whole-path quoted form only when a `.` appears inside the quotes.
    P(Index ~ (dottedPathIdentifier | quotedPathIdentifier) ~~ Index).map {
      case (off1, parts, off2) =>
        PathIdentifier(at(off1, off2), parts)
    }
  }

  def term[u: P]: P[Term] = {
    P(
      Index ~ Keywords.term ~ identifier ~ is ~ docBlock ~ Index
    )./.map { case (off1, id, definition, off2) =>
      Term(at(off1, off2), id, definition)
    }
  }

  private def mimeTypeChars(in: Char): Boolean =
    isLowerCase(in) | in == '.' || in == '-' || in == '*'
  end mimeTypeChars

  def mimeType[u: P]: P[String] = {
    P(
      ("application" | "audio" | "example" | "font" |
        "image" | "model" | "text" | "video") ~~ "/" ~~
        CharsWhile(mimeTypeChars)
    ).!
  }

  /** All three attachment forms, sharing ONE `attachment` keyword.
    *
    * They must be factored this way rather than listed as separate alternatives in [[metaData]],
    * because `Keywords.keyword` ends in a cut (`P(key ~~ &(isNotKeywordChar))./`). Once the keyword
    * matched, the enclosing `|` could not backtrack — so whichever attachment rule came first won
    * unconditionally, and `attachment ULID is "…"` was UNREACHABLE: it failed where the mime type
    * was expected, having never reached the ULID rule at all. The construct has no fixture and no
    * test anywhere, which is why that went unnoticed.
    *
    * With the keyword parsed once, the branches below backtrack against each other normally. The
    * ULID branch is tried first and is safe there: an ordinary attachment merely NAMED `ULID`
    * (`attachment ULID is text/plain as "x"`) fails the branch at its `literalString` and falls
    * through to the general form.
    */
  private def attachment[u: P]: P[Attachment] =
    P(Index ~ Keywords.attachment ~ (ulidAttachmentBody | namedAttachmentBody) ~ Index).map {
      case (off1, mk, off2) => mk(at(off1, off2))
    }

  private def ulidAttachmentBody[u: P]: P[At => Attachment] =
    P("ULID" ~ is ~ literalString).map { ulidString => (loc: At) =>
      ULIDAttachment(loc, ULID.fromString(ulidString.s))
    }

  private def namedAttachmentBody[u: P]: P[At => Attachment] =
    P(
      identifier ~ is ~ mimeType ~
        ((in.! ~ Keywords.file ~ literalString) | (as.! ~ literalString))
    ).map {
      case (id, mimeType, ("in", fileName)) =>
        (loc: At) => FileAttachment(loc, id, mimeType, fileName)
      case (id, mimeType, (_, value)) =>
        (loc: At) => StringAttachment(loc, id, mimeType, value)
    }

  def option[u: P]: P[OptionValue] =
    P(
      Index ~ Keywords.option ~/ is.? ~ CharsWhile(ch =>
        ch.isLower | ch.isDigit | ch == '_' | ch == '-'
      ).! ~
        (Punctuation.roundOpen ~ literalString.rep(
          0,
          Punctuation.comma
        ) ~ Punctuation.roundClose).? ~ Index
    ).map { case (start, option, params, end) =>
      OptionValue(at(start, end), option, params.getOrElse(Seq.empty[LiteralString]))
    }
  end option

  /** A42: `figma "<fileKey>" node "<nodeId>"` — a structured, machine-resolvable reference to one
    * frame of a Figma design file. Accepted in any `with` block by the parser; the rule that
    * confines it to Input, Output, Group and application-intended Context is enforced by validation
    * so a misplaced reference reports a clear error instead of failing the parse.
    */
  private def figmaRef[u: P]: P[FigmaRef] =
    P(
      Index ~ Keywords.figma ~/ literalString ~ Keywords.node ~ literalString ~ Index
    ).map { case (start, fileKey, nodeId, end) =>
      FigmaRef(at(start, end), fileKey, nodeId)
    }
  end figmaRef

  private def metaData[u: P]: P[MetaData] =
    P(
      briefDescription | description | term | option | authorRef | figmaRef | attachment |
        comment
    ).asInstanceOf[P[MetaData]]

  def withMetaData[u: P]: P[Seq[MetaData]] = {
    P(
      Keywords.`with` ~ open ~ (undefined(Seq.empty[MetaData]) | metaData.rep(1)) ~ close
    ).?./.map {
      case Some(list: Seq[MetaData]) =>
        list
      case None =>
        Seq.empty
    }
  }

  def include[u: P, CT <: RiddlValue](parser: P[?] => P[Seq[CT]]): P[Include[CT]] = {
    P(Index ~ Keywords.include ~ literalString ~~ Index)./.map {
      case (off1, str: LiteralString, off2) =>
        doIncludeParsing[CT](at(off1, off2), str.s, parser)
    }
  }

  def groupAliases[u: P]: P[String] = {
    P(
      Keywords.keywords(
        StringIn(
          Keyword.group,
          "page",
          "pane",
          "dialog",
          "menu",
          "popup",
          "frame",
          "column",
          "window",
          "section",
          "tab",
          "flow",
          "block",
          // A43: spatial/3D cohesion. Closed list, no structural change -- the input/output/group
          // triad is already the modality-free logical core, and an alias is a directional
          // heuristic for generators, never a different kind of definition.
          "scene",
          "space",
          "zone"
        ).!
      )
    )
  }

  def outputAliases[u: P]: P[String] = {
    P(
      Keywords.keywords(
        StringIn(
          Keyword.output,
          "document",
          "list",
          "table",
          "graph",
          "animation",
          "picture",
          // A43: non-visual output modalities.
          "sound",
          "speech",
          "haptic"
        ).!
      )
    )
  }

  def inputAliases[u: P]: P[String] = {
    P(
      Keywords.keywords(
        StringIn(
          Keyword.input,
          "form",
          "text",
          "button",
          "picklist",
          "selector",
          "item",
          // A43: non-keyboard input modalities.
          "voice",
          "gesture",
          "gaze"
        ).!
      )
    )
  }

  def shownBy[u: P]: P[ShownBy] = {
    P(
      Index ~ Keywords.shown ~ by ~ open ~ httpUrl.rep(1) ~ close ~ Index
    ).map { case (off1, urls, off2) =>
      ShownBy(at(off1, off2), urls)
    }
  }
}
