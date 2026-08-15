/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language

import com.ossuminc.riddl.language.AST.{WithContexts, WithDomains, WithModules}
import com.ossuminc.riddl.language.{Contents, given}
import com.ossuminc.riddl.utils.{Await, PlatformContext, URL}
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.language.parsing.{Keyword, RiddlParserInput}

import scala.collection.{immutable, mutable}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.reflect.{classTag, ClassTag}
import scala.annotation.{tailrec, targetName, unused}
import scala.io.{BufferedSource, Codec}
import scala.scalajs.js.annotation.*
import wvlet.airframe.ulid.ULID

import scala.collection.SeqFactory

/** Abstract Syntax Tree This object defines the model for representing RIDDL as an Abstract Syntax
  * Tree. This raw AST has no referential integrity, it just results from applying the parsing rules
  * to the input. The RawAST models produced from parsing are syntactically correct but have no
  * semantic validation.
  */
@JSExportTopLevel("AST")
object AST:

///////////////////////////////////////////////////////////////////////////////////////////////////////// RIDDL VALUES

  /** The root trait of all parsed values. If a parser returns something, its a [[RiddlValue]].
    * Every node in the AST is a RiddlNode. Subclasses implement the definitions in various ways
    * because this is the most abstract notion of what is parsed.
    */
  sealed trait RiddlValue:

    /** The point location in the parse at which this RiddlValue occurs */
    def loc: At

    /** The character span this value occupies in its source file, as `(start, end)` offsets.
      *
      * `None` when the location is unknown ([[At.empty]]) — a value built programmatically or
      * rebuilt from a serialization that carried no offsets. Any tool that EDITS RIDDL source in
      * place needs this, and deriving it by re-scanning the text is how you delete the wrong range.
      *
      * These are character offsets into [[declaringFile]], not line/column: a definition needs a
      * start AND an end, which costs two integers here and two pairs in line/column form.
      */
    def span: Option[(Int, Int)] =
      if loc == At.empty then None else Some(loc.offset -> loc.endOffset)

    /** The file that DECLARED this value, as an origin string, or `None` if unknown.
      *
      * This survives [[com.ossuminc.riddl.passes.FlattenPass]]: it comes from the parser input the
      * value was parsed from, not from the enclosing `Include` wrapper, so it is still correct
      * after includes are folded away. That is the property multi-file editing tools need — "which
      * file do I write this change into?" — and it is why they should NOT reconstruct provenance
      * from `Include.origin` before flattening, nor key definitions by a synthetic `(kind, id,
      * line, col)` tuple, which collides across files.
      */
    def declaringFile: Option[String] =
      val o = loc.source.origin
      if o.isEmpty || o == "empty" then None else Some(o)

    /** Provide a string to specify the kind of thing this value is with default derived from class
      * name
      */
    def kind: String = this.getClass.getSimpleName

    /** Format the node to a string in a form suitable for use in error messages */
    def format: String

    /** Whether or not this instance has an id: [[Identifier]] field or not */
    def isIdentified: Boolean = false

    /** True only if this value does not have a name or has an empty name */
    def isAnonymous: Boolean = true

    /** Determine if this [[RiddlValue]] contains other values or not */
    def isContainer: Boolean = false

    /** Determine if this [[RiddlValue]] is the top most container, appearing at the root of the AST
      */
    def isRootContainer: Boolean = false

    /** Determine if this [[RiddlValue]] has definitions it contains */
    def hasDefinitions: Boolean = false

    /** Determine if this [[RiddlValue]] is a definition or not */
    def isDefinition: Boolean = false

    def isParent: Boolean = false

    /** Determine if this [[RiddlValue]] is empty or not. Non-containers are always empty */
    def isEmpty: Boolean = true

    /** Determines if this [[RiddlValue]] is a comment or not */
    def isComment: Boolean = false

    /** Determines if this node is a vital node or not */
    def isVital: Boolean = false

    /** Determines if this [[RiddlValue]] is a processor (handles messages) or not */
    def isProcessor: Boolean = false

    /** Determines if this [[RiddlValue]] has any options set or not */
    def hasOptions: Boolean = false

    /** Determines if this [[RiddlValue]]defines any [[Author]]s or not */
    def hasAuthors: Boolean = false

    /** Determines if this [[RiddlValue]] references any [[Author]]s or not */
    def hasAuthorRefs: Boolean = false

    /** Determines if this [[RiddlValue]] contains any type definitions */
    def hasTypes: Boolean = false

    /** Determines if this [[RiddlValue]] has any includes in it */
    def hasIncludes: Boolean = false

    /** implements the nonEmpty function based on the isEmpty function */
    @deprecatedOverriding(
      "nonEmpty is defined as !isEmpty; override isEmpty instead"
    ) final def nonEmpty: Boolean = !isEmpty

  end RiddlValue

  // Contents type and extensions defined in Contents.scala (package level)

  /** Base trait of any [[RiddlValue]] that Contains other [[RiddlValue]]
    *
    * @tparam CV
    *   The kind of contained value that is contained within.
    */
  sealed trait Container[CV <: RiddlValue] extends RiddlValue:
    /** The definitional contents of this Container value. The [[contents]] are constrained by the
      * type parameter CV so subclasses must honor that constraint.
      */
    def contents: Contents[CV]

    /** A container is empty when it holds no DEFINITIONS — comments do not count.
      *
      * `context C is { // TODO }` is a stub, not a defined context, and treating it as non-empty
      * let it slip past every "should not be empty" completeness check. Comment-tolerance is what
      * makes `isEmpty` mean what the validator needs it to mean.
      *
      * Note this is a SEMANTIC predicate, not a structural one. Code that needs to know whether
      * there are any children AT ALL — a text emitter deciding whether to open a brace, say — must
      * ask `contents.isEmpty` directly.
      */
    override def isEmpty: Boolean = contents.toSeq.forall(_.isComment)

    /** Force all subclasses to return true as they are containers */
    final override def isContainer: Boolean = true
  end Container

  /** A simple container for utility purposes in code. The parser never returns one of these */
  case class SimpleContainer[CV <: RiddlValue](contents: Contents[CV]) extends Container[CV]:
    def format: String = ""
    def loc: At = At.empty
  end SimpleContainer

  /** Represents a literal string parsed between quote characters in the input
    *
    * @param loc
    *   The location in the input of the opening quote character
    * @param s
    *   The parsed value of the string content
    */

  case class LiteralString(loc: At, s: String) extends RiddlValue:
    override def format = s"\"$s\""

    override def toString: String = format

    /** Only empty if the string is empty too */
    override def isEmpty: Boolean = s.isEmpty
  end LiteralString

  /** Companion for LiteralString class to provide the empty value */
  object LiteralString:

    /** Definition of the empty LiteralString */
    val empty: LiteralString = LiteralString(At.empty, "")
  end LiteralString

  /** A RiddlValue that is a parsed identifier, typically the name of a definition.
    *
    * @param loc
    *   The location in the input where the identifier starts
    * @param value
    *   The parsed value of the [[Identifier]]
    */
  case class Identifier(loc: At, value: String) extends RiddlValue:
    override def format: String = Identifier.format(value)
    override def isEmpty: Boolean = value.isEmpty
  end Identifier

  /** Companion object for the Identifier class to provide the empty value */
  object Identifier:
    /** Definition of the empty [[Identifier]] */
    val empty: Identifier = Identifier(At.empty, "")

    private def isAsciiLetter(c: Char): Boolean =
      (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')

    /** A character permitted after the first in a bare identifier. Kept in sync with
      * `CommonParser.simpleIdentifier` (`[A-Za-z][A-Za-z0-9_\-]*`) and the EBNF `simple_identifier`
      * rule.
      */
    private def isBareIdChar(c: Char): Boolean =
      isAsciiLetter(c) || (c >= '0' && c <= '9') || c == '_' || c == '-'

    /** True when `value` is a bare (unquoted) identifier and therefore can be emitted to RIDDL
      * source verbatim. Matches the parser's `simpleIdentifier` rule exactly: an ASCII letter
      * followed by any number of ASCII letters, digits, underscores, or hyphens, AND not a
      * definition keyword.
      *
      * The keyword clause is what makes prettify's output re-parsable. `handler 'projector' is …`
      * uses the quoting escape valve precisely because a bare `projector` there is ambiguous with
      * the start of a projector definition; emitting it back unquoted produced source that this
      * very parser then rejected, breaking the round-trip contract on `everything.riddl`.
      */
    def isBareIdentifier(value: String): Boolean =
      value.nonEmpty && isAsciiLetter(value.head) && value.tail.forall(isBareIdChar) &&
        !Keyword.definitionKeywords.contains(value)

    /** Render an identifier `value` as valid RIDDL source. A bare identifier is emitted unchanged;
      * anything else is single-quoted using the parser's `quotedIdentifier` form (`'...'`). An
      * empty value is preserved as empty.
      *
      * Note: RIDDL's quoted-identifier syntax has no escape for a single quote and only admits
      * `[A-Za-z0-9_+\-|/@$%&, :]`; a value outside that set (e.g. containing `.` or `'`) cannot be
      * represented in RIDDL at all, but such values never arise from parsing (only from in-memory /
      * JSON construction). Quoting is the best available rendering.
      */
    def format(value: String): String =
      if value.isEmpty || isBareIdentifier(value) then value else s"'$value'"
  end Identifier

  /** Represents a segmented identifier to a definition in the model. Path Identifiers are parsed
    * from a dot-separated list of identifiers in the input. Path identifiers are used to reference
    * other definitions in the model.
    *
    * @param loc
    *   Location in the input of the first letter of the path identifier
    * @param value
    *   The list of strings that make up the path identifier
    */
  case class PathIdentifier(loc: At, value: Seq[String]) extends RiddlValue:
    /** Render the path to RIDDL source. When every component is bare (or empty), emit the plain
      * dotted form `a.b.c`. When any component carries special characters, wrap the whole dotted
      * path in a single pair of quotes — `'a.CI/CD Pipeline.c'` — using the parser's quoted-path
      * form, rather than quoting each component individually.
      */
    override def format: String =
      if value.forall(p => p.isEmpty || Identifier.isBareIdentifier(p)) then value.mkString(".")
      else s"'${value.mkString(".")}'"
    override def isEmpty: Boolean = value.isEmpty || value.forall(_.isEmpty)
  end PathIdentifier

  /** Companion object of the PathIdentifier class to provide its empty value */
  object PathIdentifier:
    /** The empty [[PathIdentifier]] */
    val empty: PathIdentifier = PathIdentifier(At.empty, Seq.empty[String])
  end PathIdentifier

  /** A descriptive is something non-definitional that can be added to a definition. They occurs in
    * the `with` section of the definition
    */
  trait Meta extends RiddlValue:
    /** All MetaData have a location provided by an [[At]] value. */
    def loc: At
  end Meta

  /** A single line description for any vital definition
    * @param brief
    *   The brief description
    */
  case class BriefDescription(
    loc: At,
    brief: LiteralString
  ) extends Meta:
    def format: String = s"briefly \"${brief.s}\""
  end BriefDescription

  /** The description of a definition. All definitions have a name and an optional description. This
    * class provides the description part.
    */
  sealed trait Description extends Meta:
    /** The lines of the description abstractly defined to be provided by subclasses */
    def lines: Seq[LiteralString]
  end Description

  /** Companion class for Description only to define the empty value */
  @JSExportTopLevel("Description$")
  object Description:
    /** The empty [[Description]] definition */
    lazy val empty: Description = new Description {
      val loc: At = At.empty
      val lines = Seq.empty[LiteralString]
      def format: String = ""
    }
  end Description

  /** An implementation of a [[Description]] that implements the lines directly as a [[Seq]] of
    * [[LiteralString]]
    * @param loc
    *   The [[At]] value that provides the location of this [[BlockDescription]]
    * @param lines
    *   The literal lines of this description as a [[Seq]] of [[LiteralString]]
    */
  case class BlockDescription(
    loc: At = At.empty,
    lines: Seq[LiteralString] = Seq.empty[LiteralString]
  ) extends Description:
    override def isEmpty: Boolean = lines.isEmpty || lines.forall(_.isEmpty)
    def format: String = ""
  end BlockDescription

  /** An URL based implementation of [[Description]] that provides the description in a Markdown
    * file
    * @param loc
    *   The location in the parse source where this description occurs
    * @param url
    *   The path EXACTLY AS THE AUTHOR WROTE IT -- `"ReactiveSummit.md"`, not the absolute
    *   `file:///Users/.../ReactiveSummit.md` it resolves to.
    */
  case class URLDescription(loc: At, path: String)(using urlLoader: PlatformContext)
      extends Description:

    /** The URL this path denotes, computed AT USE rather than stored.
      *
      * The parser used to resolve `described in file "X.md"` against the source root and store
      * the absolute result, which destroyed the authored string: prettify then emitted
      * `file:///Users/reid/...`, so the round trip produced a model that would not resolve on any
      * other checkout, in CI, or for another developer. Reid ruled (2026-08-09) that the authored
      * string is what the AST holds and the URL is derived on demand.
      *
      * The basis comes from `loc.source.root` -- the At already carries the input the path was
      * written in, so nothing extra needs storing to resolve it later. An already-absolute path
      * (`described at https://...`) is used as-is; only a relative one is resolved.
      */
    def toURL: URL =
      if path.matches("^(file|https?)://.*") then URL(path)
      else loc.source.root.resolve(path)

    lazy val lines: Seq[LiteralString] = {
      val future = urlLoader.load(toURL).map(_.split("\n").toSeq.map(LiteralString(loc, _)))
      Await.result(future, 10)
    }

    /** The AUTHORED path, so a round trip reproduces what was written. */
    override def format: String = path
  end URLDescription

  sealed trait Attachment extends Meta with WithIdentifier

  /** */
  case class FileAttachment(
    loc: At,
    id: Identifier,
    mimeType: String,
    inFile: LiteralString
  ) extends Attachment:
    def format: String = identify
  end FileAttachment

  /** */
  case class StringAttachment(
    loc: At,
    id: Identifier,
    mimeType: String,
    value: LiteralString
  ) extends Attachment:
    def format: String = identify
  end StringAttachment

  case class ULIDAttachment(
    loc: At,
    ulid: ULID
  ) extends Attachment:
    override def id: Identifier = Identifier(At.empty, "ULID")
    def format: String = identify
  end ULIDAttachment

  /** This trait represents the base trait of all comments recognized by the parser */
  sealed trait Comment extends RiddlValue:
    final override def isComment: Boolean = true
  end Comment

  /** The AST Representation of a single line comment in the input. LineComments can only occur
    * after the closing brace, }, of a definition. The comment is stored within the [[Definition]]
    *
    * @param loc
    *   Location in the input of the // comment introducer
    * @param text
    *   The text of the comment, everything after the // to the end of line
    */
  case class LineComment(loc: At, text: String = "") extends Comment:
    def format: String = "// " + text
  end LineComment

  /** The AST representation of a comment that can span across lines and is inline with the
    * definitions.
    *
    * @param loc
    *   The location at which the comment occurs
    * @param lines
    *   The lines of the comment without line terminators
    */
  case class InlineComment(loc: At, lines: Seq[String] = Seq.empty) extends Comment:
    def format: String = lines.mkString("/* ", "\n", "*/")
  end InlineComment

  /** Provides a meta-data value for processing options. A given named option may have one or more
    * values of string types.
    *
    * @param loc
    *   The location at which the OptionValue occurs
    * @param name
    *   The name of the option
    * @param args
    *   THe arguments of the option as [[LiteralString]] which may be empty
    */
  case class OptionValue(loc: At, name: String, args: Seq[LiteralString] = Seq.empty)
      extends RiddlValue:
    override def format: String = "option " + name + args.map(_.format).mkString("(", ", ", ")")
  end OptionValue

  /** A reference to a definition of a specific type.
    *
    * @tparam T
    *   The type of definition to which the references refers.
    */
  sealed abstract class Reference[+T <: Definition: ClassTag] extends RiddlValue:
    /** The Path identifier to the referenced definition
      */
    def pathId: PathIdentifier

    /** The optional identifier of the reference to be used locally in some other reference.
      */
    def id: Option[Identifier] = None

    /** @return
      *   String A string that describes this reference
      */
    def identify: String =
      // Human-readable display: supply our own quotes around the raw path, so
      // special-character names are not double-quoted by Identifier.format.
      s"${classTag[T].runtimeClass.getSimpleName} ${
          if id.nonEmpty then {
            id.map(_.value + ": ")
          } else ""
        }'${pathId.value.mkString(".")}'"
    end identify

    override def isEmpty: Boolean = pathId.isEmpty
  end Reference

  /////////////////////////////////////////////////////////////////////////////////////////// WITHS
  ////////////// Defines a bunch of traits that can be used to compose the definitions via trait inheritance

  /** A trait that includes an `id` field and various methods to support it. This is used by
    * [[Definition]] and any other thing that needs to be identified by name.
    */
  sealed trait WithIdentifier extends RiddlValue:

    /** the name/identifier of this value. All definitions have one */
    def id: Identifier

    def errorLoc: At = loc.copy(endOffset = id.loc.endOffset)

    final override def isIdentified: Boolean = true

    /** This one has an identifier so it is only anonymous if that identifier is empty */
    override final def isAnonymous: Boolean = id.value.isEmpty

    /** Convert the identifier into a string format with its [[kind]] and dealing with anonymity. */
    def identify: String =
      if id.isEmpty then {
        s"Anonymous $kind"
      } else {
        // Display supplies its own quotes; use the raw value so that
        // special-character names are not double-quoted by Identifier.format.
        s"$kind '${id.value}'"
      }
    end identify

    /** Same as [[identify]] but also adds the value's location via [[loc]] */
    def identifyWithLoc: String = s"$identify at ${loc.format}"
  end WithIdentifier

  sealed trait WithMetaData extends RiddlValue:
    def metadata: Contents[MetaData]

    /** Return the AuthorRef instances from the metadata */
    def authorRefs: Seq[AuthorRef] = metadata.filter[AuthorRef]

    /** Determine if the metadata has any author refs */
    override def hasAuthorRefs: Boolean = authorRefs.nonEmpty

    /** AN optional [[BriefDescription]] */
    def brief: Option[BriefDescription] = metadata.filter[BriefDescription].headOption

    /** A reliable extractor of the brief description, dealing with the optionality and plurality of
      * it
      */
    def briefString: String = brief.map(_.brief.s).getOrElse("No brief description.")

    /** A lazily constructed [[scala.Seq]] of [[Description]] */
    def descriptions: Seq[Description] = metadata.filter[Description]

    /** A reliable extractor of the description, dealing with the optionality and plurality of it */
    def descriptionString: String =
      if descriptions.isEmpty then "No descriptions."
      else descriptions.map(_.lines.map(_.s).mkString("\n")).mkString("\n")
    end descriptionString

    /** A lazily constructed mutable [[Seq]] of [[AuthorRef]] */
    def terms: Seq[Term] = metadata.filter[Term]

    /** The [[FigmaRef]]s attached to this definition (A42) */
    def figmaRefs: Seq[FigmaRef] = metadata.filter[FigmaRef]

    def stringAttachments: Seq[StringAttachment] = metadata.filter[StringAttachment]

    def fileAttachments: Seq[FileAttachment] = metadata.filter[FileAttachment]

    /** Return the [[OptionValue]]s in the meta data */
    def options: Seq[OptionValue] = metadata.filter[OptionValue]

    /** Determine if the metadata has any option values */
    def hasOption(name: String): Boolean = options.exists(_.name == name)

    /** Get the value of `name`'d option, if there is one. */
    def getOptionValue(name: String): Option[OptionValue] = options.find(_.name == name)

    /** Get the ULID associated with the definition. There can only be one and they are assigned
      * when this method is called, spreading their definition across the access patterns, on
      * purpose.
      */
    lazy val ulid: ULID =
      metadata.find("ULID") match
        case Some(ulid: ULIDAttachment) => ulid.ulid
        case _ =>
          val result = ULID.newULID
          // Use the node's location rather than At.empty
          metadata += ULIDAttachment(this.loc, result)
          result
      end match
    end ulid

  end WithMetaData

  /** A trait that includes the `comments` field to extract the comments from the contents */
  sealed trait WithComments[CV <: RiddlValue] extends Container[CV]:

    /** A lazily constructed [[Seq]] of [[Comment]] filtered from the contents */
    def comments: Seq[Comment] = contents.filterThroughWrappers[Comment]
  end WithComments

  /** Added to definitions that support includes */
  sealed trait WithIncludes[CV <: RiddlValue] extends Container[CV]:

    /** A lazily constructed [[Seq]] of [[Include]] filtered from the contents */
    def includes: Seq[Include[CV]] = contents.filter[Include[CV]]
    final override def hasIncludes = true
  end WithIncludes

  /** Base trait of any definition that is a container and contains types */
  sealed trait WithTypes[CV <: RiddlValue] extends Container[CV]:

    /** A lazily constructed [[Seq]] of [[Type]] filtered from the contents */
    def types: Seq[Type] = contents.filterThroughWrappers[Type]
    override def hasTypes: Boolean = types.nonEmpty
  end WithTypes

  /** Base trait to use in any definition that can define a constant */
  sealed trait WithConstants[CV <: RiddlValue] extends Container[CV]:

    /** A lazily constructed [[Seq]] of [[Constant]] filtered from the contents */
    def constants: Seq[Constant] = contents.filterThroughWrappers[Constant]
  end WithConstants

  /** Base trait to use in any [[Definition]] that can define an invariant */
  sealed trait WithInvariants[CV <: RiddlValue] extends Container[CV]:

    /** A lazily constructed [[Seq]] of [[Invariant]] filtered from the contents */
    def invariants: Seq[Invariant] = contents.filterThroughWrappers[Invariant]
  end WithInvariants

  /** Base trait to use in any [[Definition]] that can define a [[Function]] */
  sealed trait WithFunctions[CV <: RiddlValue] extends Container[CV]:

    /** A lazily constructed [[Seq]] of [[Function]] filtered from the contents */
    def functions: Seq[Function] = contents.filterThroughWrappers[Function].toSeq
  end WithFunctions

  /** Base trait to use in any [[Processor]] because they define [[Handler]]s */
  sealed trait WithHandlers[CV <: RiddlValue] extends Container[CV]:

    /** A lazily constructed [[Seq]] of [[Handler]] filtered from the contents */
    def handlers: Seq[Handler] = contents.filterThroughWrappers[Handler]
  end WithHandlers

  /** Base trait to use in any [[Definition]] that can define an [[Inlet]] */
  sealed trait WithInlets[CV <: RiddlValue] extends Container[CV]:

    /** A lazily constructed [[Seq]] of [[Inlet]] filtered from the contents */
    def inlets: Seq[Inlet] = contents.filterThroughWrappers[Inlet]
  end WithInlets

  /** Base trait to use in any [[Definition]] that can define an [[Outlet]] */
  sealed trait WithOutlets[CV <: RiddlValue] extends Container[CV]:

    /** A lazily constructed [[Seq]] of [[Outlet]] filtered from the contents */
    def outlets: Seq[Outlet] = contents.filterThroughWrappers[Outlet]
  end WithOutlets

  /** Base trait to use in any [[Definition]] that can define a [[State]] */
  sealed trait WithStates[CV <: RiddlValue] extends Container[?]:

    /** A lazily constructed [[Seq]] of [[State]] filtered from the contents */
    def states: Seq[State] = contents.filterThroughWrappers[State]
  end WithStates

  /** Base trait to use in any [[Definition]] that can define a [[Group]] */
  sealed trait WithGroups[CV <: RiddlValue] extends Container[CV]:

    /** A lazily constructed [[Seq]] of [[Group]] filtered from the contents */
    def groups: Seq[Group] = contents.filterThroughWrappers[Group]
  end WithGroups

  /** Base trait to use in any [[Definition]] that can define a [[Output]] */
  sealed trait WithOutputs[CV <: RiddlValue] extends Container[CV]:

    /** A lazily constructed [[Seq]] of [[Output]] filtered from the contents */
    def outputs: Seq[Output] = contents.filterThroughWrappers[Output]
  end WithOutputs

  /** Base trait to use in any [[Definition]] that can define a [[Output]] */
  sealed trait WithInputs[CV <: RiddlValue] extends Container[CV]:

    /** A lazily constructed [[Seq]] of [[Output]] filtered from the contents */
    def inputs: Seq[Input] = contents.filterThroughWrappers[Input]
  end WithInputs

  /** Base trait to use to define the [[AST.Statement]]s that form the body of a [[Function]] or
    * [[OnClause]]
    */
  sealed trait WithStatements[CV <: RiddlValue] extends Container[CV]:

    /** A lazily constructed [[Seq]] of [[Statement]] filtered from the contents */
    def statements: Seq[Statement] = contents.filterThroughWrappers[Statement]
  end WithStatements

  /** Base trait to use in a [[Domain]] to define the bounded [[Context]] it contains */
  sealed trait WithContexts[CV <: RiddlValue] extends Container[CV]:

    /** A lazily constructed [[Seq]] of [[Context]] filtered from the contents */
    def contexts: Seq[Context] = contents.filterThroughWrappers[Context]
  end WithContexts

  /** Base trait to use in any [[Definition]] that can define [[Author]]s */
  sealed trait WithAuthors[CV <: RiddlValue] extends Container[CV]:

    /** A lazily constructed [[Seq]] of [[Author]] filtered from the contents */
    def authors: Seq[Author] = contents.filterThroughWrappers[Author]
    override def hasAuthors: Boolean = authors.nonEmpty
  end WithAuthors

  /** Base trait to use in any [[Definition]] that establishes a version scope (A53).
    *
    * A scope may declare AT MOST ONE [[Version]]; a second one is a validation Error. `versions`
    * exposes every one found so validation can detect the duplicate; `version` is the accessor
    * everything else should use.
    */
  sealed trait WithVersion[CV <: RiddlValue] extends Container[CV]:

    /** Every [[Version]] declared directly in this scope. At most one is legal. */
    def versions: Seq[Version] = contents.filterThroughWrappers[Version]

    /** The [[Version]] this scope declares, if any (the first one, if the model is invalid). */
    def version: Option[Version] = versions.headOption
  end WithVersion

  /** Base trait to use in any [[Definition]] that establishes a copyright scope (A47).
    *
    * A scope may declare AT MOST ONE [[Copyright]]; a second one is a validation Error.
    * `copyrights` exposes every one found so validation can detect the duplicate; `copyright` is
    * the accessor everything else should use.
    */
  sealed trait WithCopyright[CV <: RiddlValue] extends Container[CV]:

    /** Every [[Copyright]] declared directly in this scope. At most one is legal. */
    def copyrights: Seq[Copyright] = contents.filterThroughWrappers[Copyright]

    /** The [[Copyright]] this scope declares, if any (the first one, if the model is invalid). */
    def copyright: Option[Copyright] = copyrights.headOption
  end WithCopyright

  /** Base trait to use in any [[Definition]] that can define [[User]]s */
  sealed trait WithUsers[CV <: RiddlValue] extends Container[CV]:

    /** A lazily constructed [[Seq]] of [[User]] filtered from the contents */
    def users: Seq[User] = contents.filterThroughWrappers[User]
  end WithUsers

  /** Base trait to use in any [[Definition]] that can define [[Epic]]s */
  sealed trait WithEpics[CV <: RiddlValue] extends Container[CV]:

    /** A lazily constructed [[Seq]] of [[Epic]] filtered from the contents */
    def epics: Seq[Epic] = contents.filterThroughWrappers[Epic]
  end WithEpics

  /** Base trait to use in any [[Definition]] that can define [[Domain]]s */
  sealed trait WithDomains[CV <: RiddlValue] extends Container[CV]:

    /** A lazily constructed [[Seq]] of [[Domain]] filtered from the contents */
    def domains: Seq[Domain] = contents.filterThroughWrappers[Domain]
  end WithDomains

  /** Base trait to use in any [[Definition]] that can define [[Projector]]s */
  sealed trait WithProjectors[CV <: RiddlValue] extends Container[CV]:

    /** A lazily constructed [[Seq]] of [[Projector]] filtered from the contents */
    def projectors: Seq[Projector] = contents.filterThroughWrappers[Projector]
  end WithProjectors

  /** Base trait to use in any [[Definition]] that can define [[Repository]]s */
  sealed trait WithRepositories[CV <: RiddlValue] extends Container[CV]:

    /** A lazily constructed [[Seq]] of [[Repository]] filtered from the contents */
    def repositories: Seq[Repository] = contents.filterThroughWrappers[Repository]
  end WithRepositories

  /** Base trait to use in any [[Definition]] that can define [[Entity]]s */
  sealed trait WithEntities[CV <: RiddlValue] extends Container[CV]:

    /** A lazily constructed [[Seq]] of [[Entity]] filtered from the contents */
    def entities: Seq[Entity] = contents.filterThroughWrappers[Entity]
  end WithEntities

  /** Base trait to use in any [[Definition]] that can define [[Streamlet]]s */
  sealed trait WithStreamlets[CV <: RiddlValue] extends Container[CV]:

    /** A lazily constructed [[Seq]] of [[Streamlet]] filtered from the contents */
    def streamlets: Seq[Streamlet] = contents.filterThroughWrappers[Streamlet]
  end WithStreamlets

  /** Base trait to use in any [[Definition]] that can define [[Connector]]s */
  sealed trait WithConnectors[CV <: RiddlValue] extends Container[CV]:

    /** A lazily constructed [[Seq]] of [[Connector]] filtered from the contents */
    def connectors: Seq[Connector] = contents.filterThroughWrappers[Connector]
  end WithConnectors

  /** Base trait to use in any [[Definition]] that can define [[Adaptor]]s */
  sealed trait WithAdaptors[CV <: RiddlValue] extends Container[CV]:

    /** A lazily constructed [[Seq]] of [[Adaptor]] filtered from the contents */
    def adaptors: Seq[Adaptor] = contents.filterThroughWrappers[Adaptor]
  end WithAdaptors

  /** Base trait to use in any [[Definition]] that can define [[Saga]]s */
  sealed trait WithSagas[CV <: RiddlValue] extends Container[CV]:

    /** A lazily constructed [[Seq]] of [[Saga]] filtered from the contents */
    def sagas: Seq[Saga] = contents.filterThroughWrappers[Saga]
  end WithSagas

  /** Base trait to use in any [[Definition]] that can define [[SagaStep]]s */
  sealed trait WithSagaSteps[CV <: RiddlValue] extends Container[CV]:

    /** A lazily constructed [[Seq]] of [[SagaStep]] filtered from the contents */
    def sagaSteps: Seq[SagaStep] = contents.filterThroughWrappers[SagaStep]
  end WithSagaSteps

  /** Base trait to use in any [[Definition]] that can define [[UseCase]]s */
  sealed trait WithUseCases[CV <: RiddlValue] extends Container[CV]:

    /** A lazily constructed [[Seq]] of [[UseCase]] filtered from the contents */
    def cases: Seq[UseCase] = contents.filterThroughWrappers[UseCase]
  end WithUseCases

  /** Base trait to use in any [[Definition]] that can define [[ShownBy]]s */
  sealed trait WithShownBy[CV <: RiddlValue] extends Container[CV]:

    /** A lazily constructed [[Seq]] of [[ShownBy]] filtered from the contents */
    def shownBy: Seq[ShownBy] = contents.filterThroughWrappers[ShownBy]
  end WithShownBy

  /** Base trait to use anywhere that can contain [[Module]]s */
  sealed trait WithModules[CV <: RiddlValue] extends Container[CV]:
    /** A lazily constructed [[Contents]] of [[Module]] */
    def modules: Seq[Module] = contents.filterThroughWrappers[Module]
  end WithModules

  ///////////////////////////////////////////////////////////////////////////// ABSTRACT DEFINITIONS
  ///// This section defines various abstract things needed by the rest of the definitions

  /** The list of definitions to which a reference cannot be made */
  type NonReferencableDefinitions = Enumerator | Root | SagaStep | Term | Invariant | Version |
    Copyright

  /** THe list of RiddlValues that are not Definitions for excluding them in match statements */
  type NonDefinitionValues = LiteralString | Identifier | PathIdentifier | Description |
    Interaction | Include[?] | TypeExpression | Comment | Reference[?] | OptionValue |
    StreamletShape | AdaptorDirection | UserStory | MethodArgument | Schema | ShownBy |
    SimpleContainer[?] | BriefDescription | BlockDescription | URLDescription | FileAttachment |
    StringAttachment | ULIDAttachment | Meta | Statement | Constructor | ConstructorArg | ValueRef |
    GetValue | PromptValue | BooleanExpression | Call | Ask | SelfValue | Initiate | Requires |
    Returns | InvariantBlock

  /** Type of definitions that occur in a [[Root]] without [[Include]]. [[Root]] deliberately stays
    * narrow: it is the file parse-root, not the reuse unit. [[Module]] is the reuse unit and is
    * widened to any top-level definition (see [[OccursInModule]]).
    */
  private[language] type OccursInRoot = Domain | Author | Comment | Version | Copyright

  /** Type of definitions that occur in a [[Module]] without [[Include]].
    *
    * A [[Module]] is a FLAT collection of ANY top-level definition — no hierarchy is enforced at
    * its top level (the internal rules of each contained definition still apply). This is the same
    * "any standalone definition" union that [[NebulaContents]] expresses, plus [[Comment]].
    */
  type OccursInModule = NebulaContents | Comment

  /** Type of definitions that can occur in a [[Module]].
    *
    * A [[Module]] may also contain a [[BASTImport]]: `import` is legal wherever a flat collection
    * of definitions is legal (root, domain, context) and a module is exactly such a collection.
    */
  type ModuleContents = OccursInModule | Include[OccursInModule] | BASTImport

  /** The root is a module that can have other modules and BAST imports */
  type RootContents = OccursInRoot | Include[OccursInRoot] | Module | BASTImport

  /** Whether `value` may occur DIRECTLY inside `container`, i.e. whether writing it there would
    * have parsed.
    *
    * The answer is a type test against the very `OccursInX` union that defines the container's
    * contents, so this cannot drift out of step with the AST or the parser: widen a union and this
    * predicate widens with it.
    *
    * Only the containers in which a [[BASTImport]] is legal (Root, Module, Domain, Context) have a
    * rule. Everywhere else the parser is the only gatekeeper and there is nothing to re-check, so
    * the answer is `None` — "no rule, do not judge".
    *
    * @param container
    *   The container the value would sit in
    * @param value
    *   The value in question
    * @return
    *   `Some(true)`/`Some(false)` when a placement rule applies, `None` when none does
    */
  def mayOccurDirectlyIn(container: Container[?], value: RiddlValue): Option[Boolean] =
    container match
      case _: Root    => Some(value.isInstanceOf[OccursInRoot])
      case _: Module  => Some(value.isInstanceOf[OccursInModule])
      case _: Domain  => Some(value.isInstanceOf[OccursInDomain])
      case _: Context => Some(value.isInstanceOf[OccursInContext])
      case _          => None
    end match
  end mayOccurDirectlyIn

  /** Things that can occur in the "With" section of a leaf definition */
  type MetaData =
    BriefDescription | Description | Term | AuthorRef | FigmaRef | FileAttachment |
      StringAttachment | ULIDAttachment | Comment | OptionValue

  /** Type of definitions that occurs within all Vital Definitions */
  type OccursInVitalDefinition = Type | Comment

  /** Type of definitions that occur within all Processor types.
    *
    * A47 widened this with [[Version]] and [[Copyright]]: both are scope-inherited leaves, and a
    * [[Processor]] — Adaptor, Context, Entity, Projector, Repository or Streamlet — is exactly the
    * set of things whose messages form an API, and therefore a versioned, attributable contract.
    * Adding them HERE (rather than naming Context and Entity individually, as A53 first did) is
    * what gives all six the same spread, and is why [[OccursInContext]] and [[OccursInEntity]] no
    * longer mention [[Version]] themselves.
    */
  type OccursInProcessor = OccursInVitalDefinition | Constant | Invariant | Function | Handler |
    Streamlet | Connector | Relationship | Inlet | Outlet | Version | Copyright

  /** Type of definitions that occur in a [[Domain]] without [[Include]] */
  type OccursInDomain =
    OccursInVitalDefinition | Author | Context | Domain | User | Epic | Saga | Repository |
      Connector | Version | Copyright

  /** Type of definitions that occur in a [[Domain]] with [[Include]] and [[BASTImport]] */
  type DomainContents = OccursInDomain | Include[OccursInDomain] | BASTImport

  /** Type of definitions that occur in a [[Context]] without [[Include]].
    *
    * [[Version]] and [[Copyright]] arrive via [[OccursInProcessor]] (A47) — naming them here too
    * would be a redundant re-admission.
    */
  type OccursInContext = OccursInProcessor | Entity | Adaptor | Group | Saga | Projector |
    Repository

  /** Type of definitions that occur in a [[Context]] with [[Include]] and [[BASTImport]] */
  type ContextContents = OccursInContext | Include[OccursInContext] | BASTImport

  /** Type of definitions that occur in a [[Group]].
    *
    * [[Comment]] and [[ShownBy]] are here because `GroupParser.groupDefinitions` has always parsed
    * both inside a group body. It reaches this union through an `asInstanceOf`, so for a long time
    * the parser produced contents the union did not admit and nothing complained — `Contents`
    * erases to an `ArrayBuffer`, so the mismatch is invisible at runtime. It surfaced as a JSON
    * round trip that could not rebuild a comment written at the top of a group: there was no legal
    * way to put it back. [[ShownBy]] is admitted for the same reason and by the same precedent as
    * `OccursInEpic`, which names it explicitly; a [[Group]] is a `WithShownBy` too.
    */
  type OccursInGroup = Group | ContainedGroup | Input | Output | Comment | ShownBy

  /** Type of definitions that occur in an [[Input]] */
  type OccursInInput = Input | TypeRef

  /** Type of definitions that occur in an [[Output]] */
  type OccursInOutput = Output | TypeRef

  type GroupRelated = Group | Input | Output

  /** Type of definitions that occur in an [[Entity]] without [[Include]]. [[Version]] and
    * [[Copyright]] arrive via [[OccursInProcessor]] (A47).
    */
  private type OccursInEntity = OccursInProcessor | State

  /** Type of definitions that occur in an [[Entity]] with [[Include]] */
  type EntityContents = OccursInEntity | Include[OccursInEntity]

  /** Type of definitions that occur in a [[State]] */
  type StateContents = Handler | Invariant | Comment

  /** Type of definitions that occur in a [[Handler]] */
  type HandlerContents = OnClause | Comment

  /** Type of definitions that occur in an [[Adaptor]] without [[Include]] */
  private type OccursInAdaptor = OccursInProcessor

  /** Type of definitions that occur in an [[Adaptor]] with [[Include]] */
  type AdaptorContents = OccursInAdaptor | Include[OccursInAdaptor]

  /** Type of definitions that occur in a [[Saga]] without [[Include]].
    *
    * [[Requires]] and [[Returns]] are content rather than fields on [[Saga]] so that comments may
    * precede or separate them; see [[Requires]].
    */
  private type OccursInSaga = OccursInVitalDefinition | SagaStep | Requires | Returns

  /** Type of definitions that occur in a [[Saga]] with [[Include]] */
  type SagaContents = OccursInSaga | Include[OccursInSaga]

  /** Type of definitions that occur in a [[Streamlet]] without [[Include]] */
  // Inlet/Outlet/Connector are all members of OccursInProcessor now (ports are
  // available on every processor kind), so OccursInStreamlet adds nothing extra.
  private type OccursInStreamlet = OccursInProcessor

  /** Type of definitions that occur in a [[Streamlet]] with [[Include]] */
  type StreamletContents = OccursInStreamlet | Include[OccursInStreamlet]

  /** Type of definitions that occur in an [[Epic]] without [[Include]] */
  private type OccursInEpic = OccursInVitalDefinition | ShownBy | UseCase

  /** Type of definitions that occur in an [[Epic]] with [[Include]] */
  type EpicContents = OccursInEpic | Include[OccursInEpic]

  /** Type of definitions that occur in a [[UseCase]] */
  type UseCaseContents = Interaction | Comment

  /** Type of definitions that occur in a [[InteractionContainer]] */
  type InteractionContainerContents = Interaction | Comment

  /** Type of definitions that occur in a [[Projector]] without [[Include]] */
  private type OccursInProjector = OccursInProcessor | RepositoryRef | Correlation

  /** Type of definitions that occur in a [[Projector]] with [[Include]] */
  type ProjectorContents = OccursInProjector | Include[OccursInProjector]

  /** Type of definitions that occur in a [[Correlation]] (A70).
    *
    * A Correlation holds exactly one [[Handler]] whose on-clauses are its folds. It is the same
    * shape as [[StateContents]] minus [[Invariant]]: a projector cannot refuse an event, so an
    * invariant-as-guard has nothing to mean here.
    */
  type CorrelationContents = Handler | Comment

  /** Type of definitions that occur in a [[Repository]] without [[Include]] */
  private type OccursInRepository = OccursInProcessor | Schema

  /** Type of definitions that occur in a [[Repository]] with [[Include]] */
  type RepositoryContents = OccursInRepository | Include[OccursInRepository]

  /** Type of definitions that occur in a [[Function]] */
  /** [[Requires]] and [[Returns]] are content rather than fields on [[Function]]; see [[Requires]]. */
  private type OccursInFunction = OccursInVitalDefinition | Statement | Function | Requires | Returns

  /** Type of definitions that occur in a [[Function]]. Functions are self-contained and do not
    * support includes.
    */
  type FunctionContents = OccursInFunction

  /** Type of definitions that occur in a [[Type]] */
  type TypeContents = Field | Method | Enumerator

  type AggregateContents = Field | Method | Comment

  /** Type of definitions that occur in a block of [[Statement]] */
  type Statements = Statement | Comment

  type NebulaContents = Adaptor | Author | Connector | Constant | Context | Domain | Entity | Epic |
    Function | Invariant | Module | Projector | Relationship | Repository | Saga | Streamlet |
    Type | User | Version | Copyright

  ///////////////////////////////////////////////////////////////////////////////////// DEFINITIONS
  //////// The Abstract classes for defining Definitions by using the foregoing traits

  /** Base trait for all Definitions. Their mere distinction at this level of abstraction is to
    * simply have an identifier and can have attachments
    *
    * @see
    *   [[Branch]] and [[Leaf]]
    */
  sealed trait Definition extends WithIdentifier with WithMetaData:
    /** Yes anything deriving from here is a definition */
    override def isDefinition: Boolean = true
    override def isParent: Boolean = false
    override def hasDefinitions: Boolean = false

    /** Cheap hash based on id + loc to avoid O(subtree) traversal of contents fields. Case class
      * auto-generated hashCode traverses ALL constructor fields including Contents
      * (mutable.ArrayBuffer), making every HashMap operation O(subtree). Note: overriding hashCode
      * here suppresses case class auto-generated equals too (Scala 3 spec), so we must also
      * override equals to preserve structural comparison.
      */
    override def hashCode: Int =
      val h = id.hashCode * 31 + loc.hashCode
      h * 31 + getClass.hashCode
    override def equals(that: Any): Boolean = that match
      case other: Definition =>
        (this eq other) || (
          getClass == other.getClass &&
            id == other.id &&
            loc == other.loc &&
            metadata == other.metadata &&
            productEquals(other)
        )
      case _ => false

    /** Structural comparison of product elements, skipping Contents fields to avoid O(subtree)
      * traversal. For two Definitions at the same loc with the same id, the non-contents fields
      * determine equality.
      */
    private def productEquals(other: Definition): Boolean =
      (this, other) match
        case (a: Product, b: Product) =>
          a.productArity == b.productArity &&
          (0 until a.productArity).forall: i =>
            (a.productElement(i), b.productElement(i)) match
              case (_: Contents[?], _: Contents[?]) => true // skip contents
              case (x, y)                           => x == y
        case _ => false
  end Definition

  object Definition:
    /** The canonical value for "empty" Definition which can usually be interpeted as "Not Found" */
    lazy val empty: Definition = new Definition {
      def id: Identifier = Identifier.empty
      def format: String = ""
      def loc: At = At.empty
      override def isEmpty: Boolean = true
      override def metadata: Contents[MetaData] = Contents.empty[MetaData](0)
    }
  end Definition

  /** The Base trait for a definition that contains some unrestricted kind of content, RiddlValue */
  sealed trait Branch[CV <: RiddlValue] extends Definition with Container[CV]:
    override def isParent: Boolean = true
    // Comment-tolerant, in step with `isEmpty`: a body holding only comments has no
    // definitions in it.
    override def hasDefinitions: Boolean = !isEmpty
    opaque type ContentType <: RiddlValue = CV

    /** May `content` be a DIRECT child of this container?
      *
      * Answered from [[Containment]], which derives it from the very `XContents` union the parser
      * is checked against — so this cannot disagree with what riddlc accepts. Every structural
      * editor (Synapify's drag-and-drop, the IDEA plugin, the VS Code extension) previously kept a
      * hand-written copy of these rules; each copy drifts the moment the grammar gains a
      * construct, and Synapify's was wrong in two ways before anyone noticed.
      *
      * DIRECT containment only: a Domain cannot contain an Entity, even though it can contain a
      * Context that can. Kind-level only: names, duplicates and reference validity are not
      * considered — a caller wanting "and the name is free" layers that on top.
      *
      * Cheap enough to call per animation frame: no parse, no pass, no IO, no allocation — a class
      * walk over a small precomputed `Set`.
      */
    final def canContain(content: RiddlValue): Boolean = content match
      // `Include` and `BASTImport` are PROVENANCE, not structure: a container that can hold X can
      // hold an include wrapping X. So the wrapper is transparent and the question descends to
      // what it carries, matching how the content accessors read through them. An empty wrapper is
      // legal anywhere, which is what `forall` on an empty list says.
      case i: Include[?] => i.contents.toSeq.forall(canContain)
      case b: BASTImport => b.contents.toSeq.forall(canContain)
      case other         => Containment.of(this)(other)
    end canContain

    /** As [[canContain]], by simple kind name, for a caller holding no instance — a palette
      * offering definitions the user has not created yet. Case-insensitive.
      */
    final def canContainKind(kind: String): Boolean = Containment.of(this).named(kind)

    /** Every kind this container admits directly, sorted — for a palette or a diagnostic. */
    final def containableKinds: Seq[String] = Containment.of(this).kinds
  end Branch

  /** A leaf node in the hierarchy of definitions. Leaves have no content, unlike [[Branch]]. They
    * do permit a single [[BriefDescription]] value and single [[Description]] value. There are no
    * contents.
    */
  sealed trait Leaf extends Definition

  type Definitions = Seq[Definition] // TODO: Make this opaque some day

  object Definitions:
    def empty: Definitions = Seq.empty[Definition]
  end Definitions

  /** A simple sequence of Parents from the closest all the way up to the Root. Contains only Branch
    * (Definition) nodes - Include nodes are tracked separately via includeContext.
    */
  type Parents = Seq[Branch[?]]

  object Parents:
    def empty[CV <: RiddlValue]: Parents = Seq.empty[Branch[?]]
    def apply(contents: Branch[?]*) = Seq(contents: _*)
  end Parents

  /** A mutable stack of Branch[?] for keeping track of the parent hierarchy. Contains only Branch
    * (Definition) nodes - Include nodes are tracked separately via includeContext in Pass.
    *
    * Caches the `toParents` (toSeq) result for performance. The cache is invalidated on push/pop.
    * This avoids O(N*D) allocations during AST traversal where N is the number of nodes and D is
    * average depth.
    */
  final class ParentStack private (
    private val stack: mutable.Stack[Branch[?]]
  ):
    private var cachedSeq: Parents | Null = null

    def push(item: Branch[?]): Unit =
      stack.push(item)
      cachedSeq = null
    end push

    def pop(): Branch[?] =
      cachedSeq = null
      stack.pop()
    end pop

    /** Convert the mutable ParentStack into an immutable Parents Seq. Result is cached until the
      * next push or pop.
      */
    def toParents: Parents =
      if cachedSeq == null then cachedSeq = stack.toSeq
      cachedSeq.nn
    end toParents

    inline def head: Branch[?] = stack.head
    inline def headOption: Option[Branch[?]] = stack.headOption
    inline def top: Branch[?] = stack.top
    inline def isEmpty: Boolean = stack.isEmpty
    inline def nonEmpty: Boolean = stack.nonEmpty
    inline def size: Int = stack.size

    /** Find the first element matching the predicate (top to bottom). */
    inline def find(p: Branch[?] => Boolean): Option[Branch[?]] =
      stack.find(p)
  end ParentStack

  /** Companion to the ParentStack class */
  object ParentStack:
    /** @return an empty ParentStack */
    def empty[CV <: RiddlValue]: ParentStack =
      new ParentStack(mutable.Stack.empty[Branch[?]])
    def apply(items: Branch[?]*): ParentStack =
      new ParentStack(mutable.Stack(items*))
  end ParentStack

  type DefinitionStack = mutable.Stack[Definition] // TODO: Make this opaque some day

  extension (ds: DefinitionStack)
    def toDefinitions: Definitions = ds.toSeq.asInstanceOf[Definitions]
    def isOnlyParents: Boolean = ds.forall(_.isParent)
    def toParentsSeq[CV <: RiddlValue]: Seq[Branch[CV]] =
      ds.filter(_.isParent).map(_.asInstanceOf[Branch[CV]]).toSeq
  end extension

  object DefinitionStack:
    def empty: DefinitionStack = mutable.Stack.empty[Definition]
    def apply(items: Definition*): DefinitionStack = mutable.Stack(items: _*)
  end DefinitionStack

  /** The kind of thing that can be returned by PathId Resolution Pass optionally providing the
    * referent and its Parental referent, or None
    */
  type Resolution[T <: Definition] = Option[(T, Parents)]

  /** The base class of the primary, or vital, definitions. Most of the important definitions are
    * derivatives of this sealed trait. All vital definitions contain comments, documentation,
    * options, authors that defined it, include statements, and term definitions.
    * @tparam CT
    *   The type of the contents of the Vital Definition which must be rooted in RiddlValue
    */
  sealed trait VitalDefinition[CT <: RiddlValue]
      extends Branch[CT]
      with WithTypes[CT]
      with WithIncludes[CT]
      with WithComments[CT]:
    final override def isVital: Boolean = true
  end VitalDefinition

  /** Definition of a Processor. This is a base class for all Processor definitions (things that
    * have inlets, outlets, handlers, functions, and take messages directly with a reference).
    * Processors are the active portion of a model since they handle messages and do the associated
    * processing.
    * @tparam CT
    *   The type of content that the [[Processor]] may contain
    */
  sealed trait Processor[CT <: RiddlValue]
      extends VitalDefinition[CT]
      with WithConstants[CT]
      with WithInvariants[CT]
      with WithFunctions[CT]
      with WithHandlers[CT]
      with WithStreamlets[CT]
      with WithInlets[CT]
      with WithOutlets[CT]
      // A47: every Processor is a version AND copyright scope. A processor's messages form an API,
      // and therefore a contract, so both attribution and versioning must be expressible per
      // component — Adaptor, Context, Entity, Projector, Repository and Streamlet alike. Mixing
      // them in HERE is what gives all six the same spread; Context and Entity must NOT list
      // WithVersion again (a trait inherited twice in one extends clause is an error).
      with WithVersion[CT]
      with WithCopyright[CT]:
    final override def isProcessor: Boolean = true

    /** The shape explicitly ascribed by the author via `as <shape>`, if any. */
    def ascribedShape: Option[StreamletShape]

    /** This processor's inlets, minus any marked `option error-sink`.
      *
      * An `error-sink` inlet is infrastructure, not dataflow: it receives failure notifications a
      * GENERATOR emits at run time, and nothing in the model produces them. A processor ascribed
      * `as flow` that also hosts its domain's error sink should still BE a flow.
      *
      * Deliberately NOT used by [[arityShape]], which reports the honest port counts -- subtracting
      * there would make a dedicated `as sink` receiver whose only inlet IS the error sink compute
      * as `void`. Shape VALIDATION accepts either reading; see `validateProcessorShape`.
      *
      * It is an ordinary inlet everywhere else: it must be connected, and it is subject to the
      * usual cardinality rules.
      */
    def dataflowInlets: Seq[Inlet] =
      inlets.filterNot(_.metadata.filter[OptionValue].exists(_.name == "error-sink"))

    /** The shape derived purely from arity (the counts of inlets and outlets), ignoring any
      * ascribed shape. [[shapeForArity]] is TOTAL over non-negative arities, so there is no
      * fallback and no arity this cannot name.
      */
    def arityShape: StreamletShape = shapeForArity(outlets.size, inlets.size)

    /** The shape a given (outlet, inlet) arity denotes.
      *
      * Extracted so that shape VALIDATION can ask the same question of a second reading -- the
      * arity excluding `error-sink` inlets -- without restating the mapping and letting the two
      * drift apart.
      */
    def shapeForArity(out: Int, in: Int): StreamletShape = {
      val loc = this.loc
      (out, in) match
        case (0, 0)                     => Void(loc)
        // A SINK is any pure drain and a SOURCE any pure origin, whatever the port count (Reid,
        // 2026-08-12). Both used to be pinned to exactly one port, which left `(0, >=2)` and
        // `(>=2, 0)` -- an ordinary fan-in drain and fan-out origin -- with no shape at all. They
        // fell to a catch-all returning Void, so `repository R as sink` with two inlets was
        // rejected with "its arity is void": a confident, wrong diagnosis for a correct model.
        // A31 already says fan-in/out is modelled by declaring MULTIPLE ports, so these arities
        // were always meant to be expressible.
        case (0, i) if i >= 1           => Sink(loc)
        case (o, 0) if o >= 1           => Source(loc)
        case (1, 1)                     => Flow(loc)
        case (1, i) if i >= 2           => Merge(loc)
        case (o, 1) if o >= 2           => Split(loc)
        case (o, i) if o >= 2 && i >= 2 => Router(loc)
        // TOTAL over non-negative arities -- every (out, in) is named above, so this arm is
        // reachable only for a negative count, which a `.size` cannot produce. It THROWS rather
        // than returning a shape, because returning one is exactly how the old catch-all turned a
        // gap in the vocabulary into a wrong answer that validation then reported as fact.
        case (o, i) =>
          throw new IllegalStateException(
            s"shapeForArity received a negative arity ($o outlets, $i inlets); port counts come " +
              "from collection sizes and cannot be negative"
          )
      end match
    }

    /** The effective shape: the ascribed shape if present, otherwise derived from arity (the counts
      * of inlets and outlets). Arity validation is performed by a later pass, so degenerate arities
      * fall back to [[Void]] here rather than crashing.
      */
    def effectiveShape: StreamletShape = ascribedShape.getOrElse(arityShape)

    /** Every port on this processor, inlets and outlets together, in that order.
      *
      * A convenience over `inlets ++ outlets` that UI consumers were each writing by hand.
      */
    def ports: Seq[Portlet] = inlets ++ outlets

    /** True when [[effectiveShape]] is a [[Source]] — no inlets, one outlet.
      *
      * Asks about the EFFECTIVE shape, so an ascribed `as source` and a shape derived from arity
      * answer the same. Consumers hand-rolling this off `ascribedShape` alone get the wrong answer
      * for the (common) unascribed case.
      */
    def isSource: Boolean = effectiveShape.isInstanceOf[Source]

    /** True when [[effectiveShape]] is a [[Sink]] — one inlet, no outlets. See [[isSource]]. */
    def isSink: Boolean = effectiveShape.isInstanceOf[Sink]

    /** True when [[effectiveShape]] is a [[Flow]] — one inlet, one outlet. See [[isSource]]. */
    def isFlow: Boolean = effectiveShape.isInstanceOf[Flow]
  end Processor

  ///////////////////////////////////////////////////////////////////////////// UTILITY DEFINITIONS
  //// The types defined in this section provide utility to the other definitions for includes
  //// and references.

  /** A value to record an inclusion of a file while parsing.
    *
    * @param loc
    *   The location of the include statement in the source
    * @param contents
    *   The Vital Definitions read from the file
    * @param origin
    *   The string that indicates the origin of the inclusion
    * @tparam CT
    *   The type of things that may be included as the contents of the [[Include]]'s parent.
    */
  case class Include[CT <: RiddlValue](
    loc: At = At.empty,
    origin: URL = URL.empty,
    contents: Contents[CT]
  ) extends Container[CT]:
    type ContentType = CT

    override def isRootContainer: Boolean = true

    def format: String = s"include \"$origin\""
    override def toString: String = format
  end Include

  /** An import of a BAST (Binary AST) file.
    *
    * Imports bring in pre-compiled definitions from .bast files. Can appear at the root level,
    * inside a domain, or inside a context. The imported definitions become children of the
    * containing scope and are accessible via normal domain path resolution.
    *
    * Syntax variants:
    *   - Full import: `import "path/to/file.bast"`
    *   - Selective import: `import domain X from "file.bast"`
    *   - Aliased import: `import type T from "file.bast" as MyT`
    *
    * @param loc
    *   The location of the import statement in the source
    * @param path
    *   The path to the .bast file to import
    * @param kind
    *   Optional: the kind of definition to import ("domain", "context", "type", etc.)
    * @param selector
    *   Optional: the name of the specific definition to import
    * @param alias
    *   Optional: an alternate name for the imported definition
    * @param contents
    *   The loaded Nebula contents from the BAST file (populated by BASTLoader)
    */
  case class BASTImport(
    loc: At = At.empty,
    path: LiteralString,
    kindOpt: Option[String] = None,
    selector: Option[Identifier] = None,
    alias: Option[Identifier] = None,
    contents: Contents[NebulaContents] = Contents.empty[NebulaContents]()
  ) extends Container[NebulaContents]:
    type ContentType = NebulaContents

    override def kind: String = kindOpt.getOrElse(super.kind)
    override def isRootContainer: Boolean = false

    /** Check if this is a selective import (imports a specific definition) */
    def isSelective: Boolean = kindOpt.isDefined && selector.isDefined

    // NOTE: The RIDDL keyword "import" must NOT appear adjacent to a
    // quote character (' or ") in any string literal that reaches the
    // JS bundle.  ESM shim plugins (e.g. esmShimPlugin / Vite) scan
    // the compiled JS for patterns like  import '…  or  import "…
    // and rewrite them, corrupting the output.  We split the keyword
    // with string concatenation so the pattern never appears in the
    // bundle as a single token.  Do NOT "simplify" these into a
    // single interpolated string.
    private val imp = "im" + "port"

    def format: String =
      if isSelective then
        val kindStr = kindOpt.getOrElse("")
        val selectorStr = selector.map(_.value).getOrElse("")
        val aliasStr = alias.map(a => s" as ${a.value}").getOrElse("")
        s"""$imp $kindStr $selectorStr from "${path.s}"$aliasStr"""
      else imp + " \"" + path.s + "\""
    override def toString: String = format
  end BASTImport

  /** Base trait of a reference to definitions that can accept a message directly via a reference
    *
    * @tparam T
    *   The kind of reference needed
    */
  sealed trait ProcessorRef[+T <: Processor[?]] extends Reference[T]

  ///////////////////////////////////////////////////////////////////////////////////////////// ROOT

  /** The root of the containment hierarchy, corresponding roughly to a level about a file.
    *
    * @param contents
    *   The sequence top level definitions contained by this root container
    */
  case class Root(
    loc: At = At(),
    contents: Contents[RootContents] = Contents.empty[RootContents]()
  ) extends Branch[RootContents]
      with WithModules[RootContents]
      with WithDomains[RootContents]
      with WithAuthors[RootContents]
      with WithComments[RootContents]
      with WithVersion[RootContents]
      with WithCopyright[RootContents]
      with WithIncludes[RootContents]:

    def metadata: Contents[MetaData] = Contents.empty[MetaData](0)

    override def isRootContainer: Boolean = true

    override def id: Identifier = Identifier(loc, "Root")

    override def identify: String = "Root"

    override def identifyWithLoc: String = "Root"

    def format: String = "Root"
  end Root

  object Root:

    /** The value to use for an empty [[Root]] instance */
    def empty: Root = Root(At.empty, Contents.apply[RootContents]())
  end Root
  ////////////////////////////////////////////////////////////////////////////////////////// NEBULA

  /** Deprecated alias for [[Module]].
    *
    * `Nebula` was RIDDL 1.x's flat scratchpad of arbitrary definitions. [[Module]] subsumes it
    * entirely — same wide contents union, same absence of enforced hierarchy, and it is the BAST
    * serialization root — so nothing in RIDDL has produced a `Nebula` since 2.0: the anonymous
    * `nebula` parse entry point yields a [[Module]] with [[Module.syntheticId]].
    *
    * It is an ALIAS rather than a deprecated class because a deprecated member of a sealed
    * hierarchy is a trap for consumers: an exhaustive match over [[Branch]] had to either omit the
    * case (`[E029] match may not be exhaustive`) or include it (deprecation warning), and under
    * `-Werror` both are build failures with no clean way out. Synapify and riddl-generator both hit
    * it. As an alias the name keeps compiling in type positions and stops existing as a separate
    * case to match — the same treatment [[Abstract]] and [[ReplyStatement]] received.
    */
  @deprecated("Use Module instead; a Module is a flat bag of any top-level definition", "2.0.0")
  type Nebula = Module

  ////////////////////////////////////////////////////////////////////////////////////////// MODULE

  /** A Module is a named, FLAT collection of any top-level definition. No hierarchy is enforced at
    * a Module's top level — the internal rules of each contained definition still apply. Modules
    * are the unit of reuse: they compile to BAST and are the BAST serialization root.
    *
    * @param loc
    *   The location of the module in the source
    * @param id
    *   The name of the module
    * @param contents
    *   The definitions the module holds — any [[ModuleContents]]
    * @param metadata
    *   The metadata for the Module
    */
  case class Module(
    loc: At,
    id: Identifier,
    contents: Contents[ModuleContents] = Contents.empty[ModuleContents](),
    metadata: Contents[MetaData] = Contents.empty[MetaData]()
  ) extends VitalDefinition[ModuleContents]
      with WithAdaptors[ModuleContents]
      with WithAuthors[ModuleContents]
      with WithConstants[ModuleContents]
      with WithContexts[ModuleContents]
      with WithDomains[ModuleContents]
      with WithEntities[ModuleContents]
      with WithEpics[ModuleContents]
      with WithFunctions[ModuleContents]
      with WithInvariants[ModuleContents]
      with WithModules[ModuleContents]
      with WithProjectors[ModuleContents]
      with WithRepositories[ModuleContents]
      with WithSagas[ModuleContents]
      with WithStreamlets[ModuleContents]
      with WithVersion[ModuleContents]
      with WithCopyright[ModuleContents]
      with WithUsers[ModuleContents]:
    def format: String = s"${Keyword.module} ${id.format}"
  end Module

  object Module:

    /** The identifier given to a [[Module]] that stands in for an anonymous, id-less source.
      *
      * Two places produce such a Module: the deprecated `nebula` parse entry point (a bare,
      * unwrapped sequence of definitions has no name to take), and the BAST serialization root
      * written from a [[Root]] (a Root has no id either). Keeping one documented convention means a
      * reader can always recognize "this Module was synthesized, not written by a human".
      */
    val syntheticId: String = "nebula"

    /** Construct a Module for content that has no name of its own. */
    def anonymous(loc: At, contents: Contents[ModuleContents]): Module =
      Module(loc, Identifier(At.empty, syntheticId), contents)

    /** True if `module` is one this compiler synthesized for anonymous content. */
    def isSynthetic(module: Module): Boolean = module.id.value == syntheticId

    /** Unwrap a Module into a [[Root]], keeping only the contents that are legal at Root level
      * (`ModuleContents ∩ RootContents` = Domain | Module | Author | Comment | BASTImport). Used
      * wherever a Module-rooted BAST file has to be handed to code that expects a Root.
      */
    def toRoot(module: Module): Root =
      // RECURSES into Include wrappers. Without this an `include` at the top level matched the
      // catch-all below and the entire included file vanished with no diagnostic -- the caller
      // got a Root that simply lacked those definitions. The Include node itself is kept, so
      // structure survives for callers that have not flattened yet; its CONTENTS are what needed
      // lifting into the Root-legal set.
      def rootItemsOf(values: Seq[RiddlValue]): Seq[RootContents] = values.flatMap {
        case d: Domain       => Some(d: RootContents)
        case m: Module       => Some(m: RootContents)
        case a: Author       => Some(a: RootContents)
        case c: Comment      => Some(c: RootContents)
        case v: Version      => Some(v: RootContents)
        case c: Copyright    => Some(c: RootContents)
        case bi: BASTImport  => Some(bi: RootContents)
        case inc: Include[?] => rootItemsOf(inc.contents.toSeq)
        case _               => None // genuinely not valid at Root level
      }
      Root(module.loc, Contents[RootContents](rootItemsOf(module.contents.toSeq)*))
    end toRoot

    /** The value to use for an empty [[Module]] instance */
    def empty: Module = Module.anonymous(At.empty, Contents.empty[ModuleContents]())
  end Module

  //////////////////////////////////////////////////////////////////////////////////////////// USER

  /** An User (Role) who is the initiator of the user story. Users may be persons or machines
    *
    * @param loc
    *   The location of the user in the source
    * @param id
    *   The name (role) of the user
    * @param is_a
    *   What kind of thing the user is
    * @param metadata
    *   The metadata for the User
    */
  case class User(
    loc: At,
    id: Identifier,
    is_a: LiteralString,
    metadata: Contents[MetaData] = Contents.empty[MetaData]()
  ) extends Leaf:
    def format: String = s"${Keyword.user} ${id.format} is ${is_a.format}"
  end User

  //////////////////////////////////////////////////////////////////////////////////////////// USER

  /** A term definition for the glossary
    * @param loc
    *   The [[At]] at which this glossary term occurs
    * @param id
    *   The term being defined
    * @param definition
    *   The definition of the term
    */
  case class Term(
    loc: At,
    id: Identifier,
    definition: Seq[LiteralString]
  ) extends Meta
      with WithIdentifier:
    def format: String = s"${Keyword.term} ${id.format}"
  end Term

  ////////////////////////////////////////////////////////////////////////////////////////// AUTHOR

  /** A value that holds the author's information
    *
    * @param loc
    *   The location of the author information
    * @param name
    *   The full name of the author
    * @param email
    *   The author's email address
    * @param organization
    *   The name of the organization the author is associated with
    * @param title
    *   The author's title within the organization
    * @param url
    *   A URL associated with the author
    * @param metadata
    *   The metadata for the Author
    */
  case class Author(
    loc: At,
    id: Identifier,
    name: LiteralString,
    email: LiteralString,
    organization: Option[LiteralString] = None,
    title: Option[LiteralString] = None,
    url: Option[com.ossuminc.riddl.utils.URL] = None,
    metadata: Contents[MetaData] = Contents.empty[MetaData]()
  ) extends Leaf:
    override def isEmpty: Boolean = {
      name.isEmpty && email.isEmpty && organization.isEmpty && title.isEmpty
    }

    def format: String = Keyword.author + " " + id.format
  end Author

  /** A reference to an [[Author]]
    * @param loc
    *   The [[At]] at which the reference is located
    * @param pathId
    *   The [[PathIdentifier]] providing the path to the [[Author]]
    */
  @JSExportTopLevel("AuthorRef")
  case class AuthorRef(loc: At, pathId: PathIdentifier) extends Reference[Author] with Meta:
    override def format: String = Keyword.author + " " + pathId.format
  end AuthorRef

  /////////////////////////////////////////////////////////////////////////////////////////// FIGMA

  /** A structured reference to a node (frame) in a Figma design file (A42).
    *
    * Unlike an opaque URL in a `briefly` or an attachment, this reference is machine-resolvable to
    * one specific frame: the file key identifies the document and the node id identifies the frame
    * within it, which is exactly what the Figma REST API's `/v1/files/{fileKey}/nodes?ids={nodeId}`
    * endpoint takes. That makes it possible to check the model against the design and report drift
    * (see the drift validation in the validation pass) rather than discovering it months later.
    *
    * It is metadata rather than a definition because it decorates a definition instead of being
    * one: it has no identifier, nothing in the model can path-reference it, and it contributes
    * nothing to the definition hierarchy.
    *
    * Only the UI-bearing definitions may carry one — [[Input]], [[Output]], [[Group]] and a
    * [[Context]] whose intention is `application`. The parser accepts it in any `with` block, as it
    * does every other [[MetaData]]; the placement rule is enforced by validation so that a
    * misplaced reference gets a clear error rather than a parse failure.
    *
    * @param loc
    *   The [[At]] at which the reference is located
    * @param fileKey
    *   The Figma file key — the opaque document identifier from a Figma file URL
    * @param nodeId
    *   The Figma node id of the referenced frame, conventionally of the form "1:23"
    */
  case class FigmaRef(loc: At, fileKey: LiteralString, nodeId: LiteralString) extends Meta:
    override def format: String =
      s"${Keyword.figma} ${fileKey.format} ${Keyword.node} ${nodeId.format}"
  end FigmaRef

  ///////////////////////////////////////////////////////////////////////////////////////// VERSION

  /** A version component contributed by the scope that declares it (A53).
    *
    * A `Version` is a [[Leaf]] whose component is EITHER a NAME or a NATURAL NUMBER — never both,
    * and never a name-plus-number pair:
    * {{{
    *   version Garibaldi   // the component is the identifier `Garibaldi`
    *   version 4           // the component is the natural number 4
    * }}}
    * Organizations routinely NAME their releases (Ubuntu "Jammy Jellyfish", the Android desserts),
    * so both forms are first-class and may be mixed freely across scopes. The name must use the
    * IDENTIFIER production — the same one that names every other definition — so a composed
    * coordinate never contains characters a generator would have to sanitize.
    *
    * ==Representation==
    *
    * `id.value` is ALWAYS the rendered component, so [[component]] is simply `id.value`. `number`
    * is the discriminator: it is `Some(n)` exactly when the component was written as a natural
    * number, and then `number.get.toString == id.value`; for a named version it is `None`. Carrying
    * the number separately gives generators typed access without re-parsing, while keeping
    * [[Definition]]'s `id: Identifier` contract honest for both forms.
    *
    * ==Scope and composition==
    *
    * A `Version` may be declared in a [[Root]], [[Module]], [[Domain]] or any [[Processor]] —
    * [[Adaptor]], [[Context]], [[Entity]], [[Projector]], [[Repository]], [[Streamlet]] (A47
    * widened A53's original five scopes to all six processors, since a processor's messages form a
    * versioned contract) — and AT MOST ONCE per scope (a second one in the same scope is a
    * validation Error). It follows the [[Author]] precedent of scope inheritance: the *precise*
    * version of any definition is COMPOSED from the versions of its versioned ancestors, root→leaf,
    * by [[AST.composedVersion]], and rendered by joining the components with `.` — a domain
    * `Garibaldi` over contexts and entities numbered `4` and `3` yields `Garibaldi.4.3`. A [[Type]]
    * or message therefore takes the composed version of its containing definition — types are
    * notoriously hard to attach metadata to and some have no body at all.
    *
    * Only scopes that actually BEAR a version contribute a component ("missing-level rule"), so
    * adoption is incremental: version the domain first, refine inward later.
    *
    * ==Caveats — this is NOT semver==
    *
    *   - The composed form (`3.1.6`) LOOKS like a semantic version but is a '''hierarchical
    *     coordinate''': each component names a scope's own version, not a major/minor/patch role.
    *     '''Compatibility semantics must not be read into it''' — a change from `3.1.6` to `3.2.1`
    *     says nothing about breakage. Named components make this plainer still: `Garibaldi.4.3` is
    *     not orderable against `Jellyfish.1.1` at all.
    *   - Generators targeting ecosystems that DEMAND semver (npm, Maven, …) must define an
    *     '''explicit mapping rule''' from the composed coordinate to a semantic version; there is
    *     no canonical one, and a named component has no numeric meaning to map.
    *   - The coordinate's '''length varies with nesting depth''' and with which ancestors bear a
    *     version, so two coordinates must be compared '''component-wise''' (and only when they
    *     denote the same scope chain). Lexical or dotted-string comparison is meaningless.
    *
    * @param loc
    *   The location of the version definition in the source
    * @param id
    *   The rendered component: the written name, or the number's decimal text
    * @param number
    *   `Some(n)` when the component was written as a natural number; `None` when it was named
    * @param metadata
    *   The metadata for the Version
    */
  @JSExportTopLevel("Version")
  case class Version(
    loc: At,
    id: Identifier,
    number: Option[Long] = None,
    metadata: Contents[MetaData] = Contents.empty[MetaData]()
  ) extends Leaf:

    /** The component this scope contributes to a composed version coordinate. */
    def component: String = id.value

    /** True when the component was written as a natural number rather than a name. */
    def isNumeric: Boolean = number.isDefined

    override def format: String = s"${Keyword.version} ${id.format}"
  end Version

  object Version:

    /** Build the numeric form, keeping `id.value` in step with `number`. */
    def numeric(
      loc: At,
      idLoc: At,
      value: Long,
      metadata: Contents[MetaData] = Contents.empty[MetaData]()
    ): Version =
      Version(loc, Identifier(idLoc, value.toString), Some(value), metadata)
  end Version

  /////////////////////////////////////////////////////////////////////////////////////// COPYRIGHT

  /** The copyright notice that applies across the scope that declares it (A47).
    *
    * A `Copyright` is a NAMED [[Leaf]] carrying the notice verbatim:
    * {{{
    *   copyright C is "© 2026 Ossum Inc."
    * }}}
    * The [[LiteralString]] is the notice '''in its entirety''' — including the © symbol, the year
    * and the holder. RIDDL does not decompose it, because notices vary by jurisdiction, by holder
    * and by license, and any decomposition would be wrong somewhere.
    *
    * ==Why it is NAMED==
    *
    * Copyrights at lower scopes routinely DIFFER — a vendored `external context` carries a foreign
    * holder's notice — and they vary in detail. The name lets a documentation generator gather the
    * distinct copyrights of a model and attribute each one properly (e.g. in a page's front matter)
    * rather than emitting the same string many times or guessing at identity.
    *
    * ==Scope and inheritance — NEAREST WINS==
    *
    * A `Copyright` may be declared in a [[Root]], [[Module]], [[Domain]] or any [[Processor]]
    * ([[Adaptor]], [[Context]], [[Entity]], [[Projector]], [[Repository]], [[Streamlet]]), and AT
    * MOST ONCE per scope (a second one in the same scope is a validation Error).
    *
    * Unlike [[Version]], which COMPOSES a coordinate out of every versioned ancestor, a copyright
    * does '''not''' accumulate: the applicable notice is the one from the '''nearest ancestor that
    * declares it''', exactly like [[AST.findAuthors]]. That is the whole point of allowing it at
    * inner scopes — an `external context` bearing a third party's notice must OVERRIDE its
    * enclosing domain's for everything inside it, not be appended to it. See [[AST.findCopyright]].
    *
    * @param loc
    *   The location of the copyright definition in the source
    * @param id
    *   The name of this copyright, used to identify and group distinct notices
    * @param text
    *   The notice, verbatim and in its entirety
    * @param metadata
    *   The metadata for the Copyright
    */
  @JSExportTopLevel("Copyright")
  case class Copyright(
    loc: At,
    id: Identifier,
    text: LiteralString,
    metadata: Contents[MetaData] = Contents.empty[MetaData]()
  ) extends Leaf:

    /** The notice this scope declares, verbatim. */
    def notice: String = text.s

    override def format: String = s"${Keyword.copyright} ${id.format}"
  end Copyright

  //////////////////////////////////////////////////////////////////////////////////// RELATIONSHIP

  enum RelationshipCardinality(val proportion: String):
    case OneToOne extends RelationshipCardinality("1:1")
    case OneToMany extends RelationshipCardinality("1:N")
    case ManyToOne extends RelationshipCardinality("N:1")
    case ManyToMany extends RelationshipCardinality("N:N")

  /** A relationship between the Processor containing this value and another Processors
    *
    * @param loc
    *   The location in the source where this relationship occurs
    * @param id
    *   The identifier of this relationship which uniquely defines it within the containing
    *   processor.
    * @param processorRef
    *   The referenced processor towards which this relationship is formed
    * @param cardinality
    *   The cardinality of the relationship between processors
    * @param label
    *   The label for this relationship as if drawn on a line connecting processors. This is
    *   optional and if not set, the [[id]] of the relationship is used instead
    */
  case class Relationship(
    loc: At,
    id: Identifier,
    withProcessor: ProcessorRef[?],
    cardinality: RelationshipCardinality,
    label: Option[LiteralString] = None,
    metadata: Contents[MetaData] = Contents.empty[MetaData]()
  ) extends Leaf:
    def format: String = Keyword.relationship + " " + id.format + " to " + withProcessor.format
  end Relationship

  /////////////////////////////////////////////////////////////////////////////////////////// TYPES

  /** Base trait of an expression that defines a type
    */
  sealed trait TypeExpression extends RiddlValue:

    /** Determines whether the `other` type is assignable to `this` type. */
    def isAssignmentCompatible(other: TypeExpression): Boolean =
      (other == this) || (other.getClass == this.getClass) ||
        (other.getClass == classOf[Anything]) ||
        (this.getClass == classOf[Anything])
    end isAssignmentCompatible

    /** Indicates whether this type has/is a [[Cardinality]] expression. */
    def hasCardinality: Boolean = false

    /** Determines if `this` [[TypeExpression]] is an [[AggregateTypeExpression]] of a specific
      * [[AggregateUseCase]]
      */
    def isAggregateOf(useCase: AggregateUseCase): Boolean =
      this match
        case AliasedTypeExpression(_, keyword, _)
            if keyword.compareToIgnoreCase(useCase.useCase) == 0 =>
          true
        case AggregateUseCaseTypeExpression(_, usecase, _, _) if usecase == useCase => true
        case _                                                                      => false
      end match
    end isAggregateOf
  end TypeExpression

  /** Base of all Numeric types */
  sealed trait NumericType extends TypeExpression:

    override def isAssignmentCompatible(other: TypeExpression): Boolean =
      super.isAssignmentCompatible(other) || other.isInstanceOf[NumericType]
    end isAssignmentCompatible
  end NumericType

  /** Base of all the Integer Numeric types */
  sealed trait IntegerTypeExpression extends NumericType

  /** Base of all the Real Numeric types */
  sealed trait RealTypeExpression extends NumericType

  /** A TypeExpression that references another type by PathIdentifier
    * @param loc
    *   The location of the AliasedTypeExpression
    * @param pathId
    *   The path identifier to the aliased type
    */
  @JSExportTopLevel("AliasedTypeExpression")
  case class AliasedTypeExpression(loc: At, keyword: String, pathId: PathIdentifier)
      extends TypeExpression:
    override def format: String = s"$keyword ${pathId.format}"
  end AliasedTypeExpression

  /** An enumeration for the fix kinds of message types */
  enum AggregateUseCase(val useCase: String):
    override inline def toString: String = useCase
    case CommandCase extends AggregateUseCase("Command")
    case EventCase extends AggregateUseCase("Event")
    case QueryCase extends AggregateUseCase("Query")
    case ResultCase extends AggregateUseCase("Result")
    case RecordCase extends AggregateUseCase("Record")
    case TypeCase extends AggregateUseCase("Type")
    case GraphCase extends AggregateUseCase("Graph")
    case TableCase extends AggregateUseCase("Table")
  end AggregateUseCase

  /** Base trait of the cardinality for type expressions */
  sealed trait Cardinality extends TypeExpression:

    /** The [[TypeExpression]] that this cardinality expression modified */
    def typeExp: TypeExpression
    final override def hasCardinality: Boolean = true
  end Cardinality

  /** A cardinality type expression that indicates another type expression as being optional; that
    * is with a cardinality of 0 or 1.
    *
    * @param loc
    *   The location of the optional cardinality
    * @param typeExp
    *   The type expression that is indicated as optional
    */
  @JSExportTopLevel("Optional")
  case class Optional(loc: At, typeExp: TypeExpression) extends Cardinality:
    override def format: String = s"${typeExp.format}?"
  end Optional

  /** A cardinality type expression that indicates another type expression as having zero or more
    * instances.
    *
    * @param loc
    *   The location of the zero-or-more cardinality
    * @param typeExp
    *   The type expression that is indicated with a cardinality of zero or more.
    */
  @JSExportTopLevel("ZeroOrMore")
  case class ZeroOrMore(loc: At, typeExp: TypeExpression) extends Cardinality:
    override def format: String = s"${typeExp.format}*"
  end ZeroOrMore

  /** A cardinality type expression that indicates another type expression as having one or more
    * instances.
    *
    * @param loc
    *   The location of the one-or-more cardinality
    * @param typeExp
    *   The type expression that is indicated with a cardinality of one or more.
    */
  @JSExportTopLevel("OneOrMore")
  case class OneOrMore(loc: At, typeExp: TypeExpression) extends Cardinality:
    override def format: String = s"${typeExp.format}+"
  end OneOrMore

  /** A cardinality type expression that indicates another type expression as having a specific
    * range of instances
    *
    * @param loc
    *   The location of the one-or-more cardinality
    * @param typeExp
    *   The type expression that is indicated with a cardinality of one or more.
    * @param min
    *   The minimum number of items
    * @param max
    *   The maximum number of items
    */
  @JSExportTopLevel("SpecificRange")
  case class SpecificRange(
    loc: At,
    typeExp: TypeExpression,
    min: Long,
    max: Long
  ) extends Cardinality:
    override def format: String = s"${typeExp.format}{$min,$max}"
  end SpecificRange

  /** Represents one variant among (one or) many variants that comprise an [[Enumeration]]
    *
    * @param id
    *   the identifier (name) of the Enumerator
    * @param enumVal
    *   the optional int value
    */
  @JSExportTopLevel("Enumerator")
  case class Enumerator(
    loc: At,
    id: Identifier,
    enumVal: Option[Long] = None,
    metadata: Contents[MetaData] = Contents.empty[MetaData]()
  ) extends Definition:
    override def format: String = id.format + enumVal.map(x => s"($x)").getOrElse("")
  end Enumerator

  /** A type expression that defines its range of possible values as being one value from a set of
    * enumerated values.
    *
    * @param loc
    *   The location of the enumeration type expression
    * @param enumerators
    *   The set of enumerators from which the value of this enumeration may be chosen.
    */
  @JSExportTopLevel("Enumeration")
  case class Enumeration(loc: At, enumerators: Contents[Enumerator]) extends IntegerTypeExpression:
    override def format: String = "{ " + enumerators.toSeq
      .map(_.format)
      .mkString(",") + " }"
  end Enumeration

  /** A type expression that that defines its range of possible values as being any one of the
    * possible values from a set of other type expressions.
    *
    * @param loc
    *   The location of the alternation type expression
    * @param of
    *   The set of type expressions from which the value for this alternation may be chosen
    */
  @JSExportTopLevel("Alternation")
  case class Alternation(loc: At, of: Contents[AliasedTypeExpression]) extends TypeExpression:
    override def format: String =
      s"one of { ${of.toSeq.map(_.format).mkString(", ")} }"
  end Alternation

  /** A type expression for a sequence of some other type expression
    *
    * @param loc
    *   Where this type expression occurs in the source code
    * @param of
    *   The type expression of the sequence's elements
    */
  @JSExportTopLevel("Sequence")
  case class Sequence(loc: At, of: TypeExpression) extends TypeExpression:
    override def format: String = s"sequence of ${of.format}"
  end Sequence

  /** A type expressions that defines a mapping from a key to a value. The value of a Mapping is the
    * set of mapped key -> value pairs, based on which keys have been provided values.
    *
    * @param loc
    *   The location of the mapping type expression
    * @param from
    *   The type expression for the keys of the mapping
    * @param to
    *   The type expression for the values of the mapping
    */
  @JSExportTopLevel("Mapping")
  case class Mapping(loc: At, from: TypeExpression, to: TypeExpression) extends TypeExpression:
    override def format: String = s"mapping from ${from.format} to ${to.format}"
  end Mapping

  /** A mathematical set of some other type of value
    *
    * @param loc
    *   Where the type expression occurs in the source
    * @param of
    *   The type of the elements of the set.
    */
  @JSExportTopLevel("Set")
  case class Set(loc: At, of: TypeExpression) extends TypeExpression:

    /** Format the node to a string */
    override def format: String = s"set of ${of.format}"
  end Set

  /** A graph of homogenous nodes. This implies the nodes are augmented with additional data to
    * support navigation in the graph but that detail is left to the implementation of the model.
    *
    * @param loc
    *   Where the type expression occurs in the source
    * @param of
    *   The type of the elements of the graph
    */
  @JSExportTopLevel("Graph")
  case class Graph(loc: At, of: TypeExpression) extends TypeExpression:

    /** Format the node to a string */
    override def format: String = s"graph of ${of.format}"
  end Graph

  /** A vector, table, or array of homogeneous cells.
    *
    * @param loc
    *   Where the type expression occurs in the source
    * @param of
    *   The type of the elements of the table
    * @param dimensions
    *   The size of the dimensions of the table. There can be as many dimensions as needed.
    */
  @JSExportTopLevel("Table")
  case class Table(loc: At, of: TypeExpression, dimensions: Seq[Long]) extends TypeExpression:
    override def format: String = s"table of ${of.format}(${dimensions.mkString(",")})"
  end Table

  /** A value that is replicated across nodes in a cluster. Usage requirements placement in a
    * definition such as [[Context]] or [[Entity]] that supports the `clustered` value for the
    * `kind` option.
    *
    * @param loc
    *   Where the replica type expression occurs in the source
    * @param of
    *   The kind of data value that is replicated across cluster nodes. Because replicas imply use
    *   of a Conflict-free Replicated Data Type, the kind of type expression for `of` is restricted
    *   to numeric, set, and map types
    */
  @JSExportTopLevel("Replica")
  case class Replica(loc: At, of: TypeExpression) extends TypeExpression:
    override def format: String = s"replica of ${of.format}"
  end Replica

  /** The base trait of values of an aggregate type to provide the required `typeEx` field to give
    * the [[TypeExpression]] for that value of the aggregate
    */
  sealed trait AggregateValue extends Leaf:
    def typeEx: TypeExpression
  end AggregateValue

  /** A definition that is a field of an aggregation type expressions. Fields associate an
    * identifier with a type expression.
    *
    * @param loc
    *   The location of the field definition
    * @param id
    *   The name of the field
    * @param typeEx
    *   The type of the field
    * @param metadata
    *   The metadata for the Field
    */
  @JSExportTopLevel("Field")
  case class Field(
    loc: At,
    id: Identifier,
    typeEx: TypeExpression,
    metadata: Contents[MetaData] = Contents.empty[MetaData]()
  ) extends AggregateValue:
    override def format: String = s"${id.format}: ${typeEx.format}"
  end Field

  /** An argument of a method value for an aggregate
    *
    * @param loc
    *   The parse location of the argument
    * @param name
    *   The name of the argument
    * @param typeEx
    *   The type of the argument as a [[TypeExpression]]
    */
  @JSExportTopLevel("MethodArgument")
  case class MethodArgument(
    loc: At,
    name: String,
    typeEx: TypeExpression
  ) extends RiddlValue:
    def format: String = s"$name: ${typeEx.format}"
  end MethodArgument

  /** A leaf definition that is a callable method (function) of an aggregate type expression.
    * Methods associate an identifier with a computed [[TypeExpression]].
    *
    * @param loc
    *   The location of the field definition
    * @param id
    *   The name of the field
    * @param args
    *   The type of the field
    * @param metadata
    *   The metadata for the Method
    */
  @JSExportTopLevel("Method")
  case class Method(
    loc: At,
    id: Identifier,
    typeEx: TypeExpression,
    args: Seq[MethodArgument] = Seq.empty[MethodArgument],
    metadata: Contents[MetaData] = Contents.empty[MetaData]()
  ) extends AggregateValue:
    override def format: String =
      s"${id.format}(${args.map(_.format).mkString(", ")}): ${typeEx.format}"
  end Method

  /** A type expression that contains an aggregation of fields (named values) or methods (named
    * functions)
    *
    * This is used as the [[TypeExpression]] of Aggregations and Messages
    */
  sealed trait AggregateTypeExpression(contents: Contents[AggregateContents])
      extends Container[AggregateContents]
      with TypeExpression
      with WithComments[AggregateContents]:

    /** The list of aggregated [[Field]] */
    def fields: Seq[Field] = contents.filterThroughWrappers[Field]

    /** Thelist of aggregated [[Method]] */
    def methods: Seq[Method] = contents.filterThroughWrappers[Method]

    override def format: String = s"{ ${contents.toSeq.map(_.format).mkString(", ")} }"
    override def isAssignmentCompatible(other: TypeExpression): Boolean =
      other match
        case oate: AggregateTypeExpression =>
          val validity: Seq[Boolean] = for
            ofield: AggregateValue <- oate.contents.filter[AggregateValue].toSeq
            named <- contents.find(ofield.id.value)
            myField: Field = named.asInstanceOf[Field] if named.isInstanceOf[Field]
            myTypEx = myField.typeEx
            oTypeEx = ofield.typeEx
          yield {
            myTypEx.isAssignmentCompatible(oTypeEx)
          }
          (validity.size == oate.contents.size) && validity.forall(_ == true)
        case _ =>
          super.isAssignmentCompatible(other)
      end match
    end isAssignmentCompatible
  end AggregateTypeExpression

  /** A type expression that takes a set of named fields as its value.
    *
    * @param loc
    *   The location of the aggregation definition
    * @param contents
    *   The content of the aggregation
    */
  @JSExportTopLevel("Aggregation")
  case class Aggregation(
    loc: At,
    contents: Contents[AggregateContents] = Contents.empty[AggregateContents]()
  ) extends AggregateTypeExpression(contents)

  @JSExportTopLevel("Aggregation$")
  /** Companion object for [[Aggregation]] to provide the empty value */
  object Aggregation:

    /** The empty value for an [[Aggregation]] */
    def empty(loc: At = At.empty): Aggregation = { Aggregation(loc) }
  end Aggregation

  /** A type expression for an aggregation that is marked as being one of the use cases. This is
    * used for messages, records, and other aggregate types that need to have their purpose
    * distinguished.
    *
    * @param loc
    *   The location of the message type expression
    * @param usecase
    *   The kind of message defined
    * @param contents
    *   The contents of the message's aggregation
    * @param yields
    *   For a command/query use case, an optional reference to the message it produces in response.
    *   ONE field, TWO keywords: a command declares it with `yields` and a query with `replies`
    *   (2.0), because a command yields an EVENT and a query replies a RESULT. Which keyword is
    *   legal follows from `usecase`, so a second field would always be None. The field keeps its
    *   original name to avoid churning the JSON DTO for no semantic gain. None for other use cases.
    */
  @JSExportTopLevel("AggregateUseCaseTypeExpression")
  case class AggregateUseCaseTypeExpression(
    loc: At,
    usecase: AggregateUseCase,
    contents: Contents[AggregateContents] = Contents.empty[AggregateContents](),
    yields: Option[MessageRef] = None
  ) extends AggregateTypeExpression(contents):
    /** The keyword this use case declares its response with: `replies` for a query, `yields` for
      * a command. Emitting `yields` for a query would produce source that no longer PARSES, which
      * is how a prettify round trip caught this.
      */
    def responseKeyword: String =
      if usecase == AggregateUseCase.QueryCase then "replies" else "yields"

    override def format: String =
      usecase.useCase.toLowerCase() +
        yields.map(y => s" $responseKeyword " + y.format).getOrElse("") + " " + super.format
  end AggregateUseCaseTypeExpression

  /** A type expression whose value is a reference to an instance of an entity.
    *
    * @param loc
    *   The location of the reference type expression
    * @param entity
    *   The type of entity referenced by this type expression.
    */
  @JSExportTopLevel("EntityReferenceTypeExpression")
  case class EntityReferenceTypeExpression(loc: At, entity: PathIdentifier) extends TypeExpression:
    override def format: String = s"entity ${entity.format}"
  end EntityReferenceTypeExpression

  /** Base class of all pre-defined type expressions
    */
  abstract class PredefinedType extends TypeExpression:
    override def isEmpty: Boolean = true

    def loc: At

    override def format: String = kind
  end PredefinedType

  @JSExportTopLevel("PredefinedType")
  object PredefinedType:
    final def unapply(preType: PredefinedType): Option[String] =
      Option(preType.kind)
  end PredefinedType

  /** A type expression that defines a string value constrained by a Java Regular Expression
    *
    * @param loc
    *   The location of the pattern type expression
    * @param pattern
    *   The Java Regular Expression to which values of this type expression must obey.
    *
    * @see
    *   https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/regex/Pattern.html
    */
  @JSExportTopLevel("Pattern")
  case class Pattern(loc: At, pattern: Seq[LiteralString]) extends PredefinedType:
    override def format: String =
      s"$kind(${pattern.map(_.format).mkString(", ")})"

    override def isAssignmentCompatible(other: TypeExpression): Boolean =
      super.isAssignmentCompatible(other) || other.isInstanceOf[String_]
    end isAssignmentCompatible
  end Pattern

  /** A type expression for values of arbitrary string type, possibly bounded by length.
    *
    * @param loc
    *   The location of the Strng type expression
    * @param min
    *   The minimum length of the string (default: 0)
    * @param max
    *   The maximum length of the string (default: MaxInt)
    */
  @JSExportTopLevel("String_")
  case class String_(loc: At, min: Option[Long] = None, max: Option[Long] = None)
      extends PredefinedType {
    override inline def kind: String = "String"

    /** Renders only the bounds that are NOT the defaults.
      *
      * A bare `String` IS exactly `String(0,255)` — one type, two spellings — so writing the
      * defaults back out is noise, and worse, it makes two equal types render differently. That
      * broke round-trip checks that compare rendered source: a model whose author wrote `String`
      * came back rendered as `String(0,255)` after any surface that canonicalizes the bounds.
      *
      * `String(7,)` and not `String(7)` because the comma is MANDATORY in the grammar
      * (`TypeParser.stringType` is `"(" ~ integer.? ~ "," ~ integer.? ~ ")"`), and this must only
      * ever emit source that parses back.
      */
    override def format: String = {
      val lo = min.getOrElse(String_.DefaultMin)
      val hi = max.getOrElse(String_.DefaultMax)
      if lo == String_.DefaultMin && hi == String_.DefaultMax then kind
      else
        val loText = if lo == String_.DefaultMin then "" else lo.toString
        val hiText = if hi == String_.DefaultMax then "" else hi.toString
        s"$kind($loText,$hiText)"
      end if
    }

    override def isAssignmentCompatible(other: TypeExpression): Boolean = {
      super.isAssignmentCompatible(other) || other.isInstanceOf[Pattern]
    }
  }

  /** The bounds a bare `String` carries. `String` and `String(0,255)` are the SAME type, which is
    * why [[String_.format]] renders both as `String`.
    */
  object String_ {
    val DefaultMin: Long = 0L
    val DefaultMax: Long = 255L
  }

  /** A type expression for values that ensure a unique identifier for a specific Processor
    * (Adaptor, Context, Entity, Projector, Repository, or Streamlet) -- widened from
    * Entity-only 2026-08-13.
    *
    * @param loc
    *   The location of the unique identifier type expression
    * @param entityPath
    *   The path identifier of the referenced Processor
    * @param kindKeyword
    *   The optional processor-kind keyword as written (`Id(entity Order)` -> `Some("entity")`,
    *   `Id(Order)` -> `None`)
    */
  @JSExportTopLevel("UniqueId")
  case class UniqueId(
    loc: At,
    entityPath: PathIdentifier,
    // The kind keyword AS WRITTEN -- `Id(entity Order)` -> Some("entity"), `Id(Order)` -> None.
    // Kept rather than deprecated (Reid, 2026-08-13): keyword-name disambiguation is a
    // RIDDL-wide idiom and a bare `Order` could be a context, a message or an entity. Storing
    // the literal keyword (not an enum) keeps prettify byte-exact without a mapping table.
    kindKeyword: Option[String] = None
  ) extends PredefinedType {
    inline override def kind: String = "Id"

    override def format: String =
      s"$kind(${kindKeyword.map(_ + " ").getOrElse("")}${entityPath.format})"

    override def isAssignmentCompatible(other: TypeExpression): Boolean = {
      super.isAssignmentCompatible(other) || other.isInstanceOf[String_] ||
      other.isInstanceOf[Pattern]
    }
  }

  /** The type representation of a national monetary currency
    * @param loc
    *   Location at which the currency type occurs
    * @param country
    *   The ISO 3166 A-3 three letter code for the country
    */
  @JSExportTopLevel("Currency")
  case class Currency(loc: At, country: String) extends PredefinedType

  /** The simplest type expression: `Anything`. An `Anything` type expression is one that is not
    * defined explicitly. It is treated as a concrete type but without any structural or type
    * information. This is useful for types that are defined only at implementation time or for
    * types whose variations are so complicated they need to remain abstract at the specification
    * level. It is the DUAL of [[Nothing]]: every type is assignment compatible with `Anything` in
    * both directions.
    *
    * @param loc
    *   The location of the `Anything` type expression
    */
  @JSExportTopLevel("Anything")
  @JSExportTopLevel("Abstract") // deprecated JS name; kept for JS/TS API source compatibility
  case class Anything(loc: At) extends PredefinedType {

    override def isAssignmentCompatible(other: TypeExpression): Boolean = true
  }

  /** Deprecated spelling of [[Anything]]. Retained so downstream Scala code that names `Abstract`
    * keeps compiling; `Abstract(loc)` and `case Abstract(loc)` both still work because the
    * companion of [[Anything]] is aliased below.
    */
  @deprecated("Use Anything instead", "2.0.0")
  type Abstract = Anything

  /** Deprecated companion alias of [[Anything]]. See [[Abstract]]. */
  @deprecated("Use Anything instead", "2.0.0")
  val Abstract: Anything.type = Anything

  @JSExportTopLevel("UserId")
  case class UserId(loc: At) extends PredefinedType {
    override def isAssignmentCompatible(other: TypeExpression): Boolean = {
      super.isAssignmentCompatible(other) || {
        other match
          case _: String_ => true
          case _          => false
      }
    }
  }

  /** A predefined type expression for boolean values (true / false)
    *
    * @param loc
    *   The location of the Bool type expression
    */
  @JSExportTopLevel("Bool")
  case class Bool(loc: At) extends PredefinedType with IntegerTypeExpression {
    override def kind: String = "Boolean"
  }

  /** A predefined type expression for an arbitrary number value
    *
    * @param loc
    *   The location of the number type expression
    */
  @JSExportTopLevel("Number")
  case class Number(loc: At)
      extends PredefinedType
      with IntegerTypeExpression
      with RealTypeExpression {}

  /** A predefined type expression for an integer value
    *
    * @param loc
    *   The location of the integer type expression
    */
  @JSExportTopLevel("Integer")
  case class Integer(loc: At) extends PredefinedType with IntegerTypeExpression

  @JSExportTopLevel("Whole")
  case class Whole(loc: At) extends PredefinedType with IntegerTypeExpression

  @JSExportTopLevel("Natural")
  case class Natural(loc: At) extends PredefinedType with IntegerTypeExpression

  /** A type expression that defines a set of integer values from a minimum value to a maximum
    * value, inclusively.
    *
    * @param loc
    *   The location of the RangeType type expression
    * @param min
    *   The minimum value of the RangeType
    * @param max
    *   The maximum value of the RangeType
    */
  @JSExportTopLevel("RangeType")
  case class RangeType(loc: At, min: Long, max: Long) extends IntegerTypeExpression {
    override def format: String = s"$kind($min,$max)"

    /** Lower case so `format` yields `range(2,4)` — the ONLY spelling that parses, and the one
      * `PrettifyPass` already emits. It was `"Range"`, so `AST.errorDescription` printed
      * `Range(2,4)`: before 2.0 that at least matched a reserved name, and after it matched
      * nothing, so error text named a form no author could have written.
      *
      * This is a DISPLAY label only. The JSON discriminator is the string literal `"Range"`,
      * hardcoded at both ends (`JsonModel.scala` read `:1384` / write `:1476`) rather than derived
      * from here, and it is a WIRE FORMAT that must NOT move with this.
      */
    inline override def kind: String = "range"

    override def isAssignmentCompatible(other: TypeExpression): Boolean = {
      super.isAssignmentCompatible(other) || other.isInstanceOf[NumericType]
    }
  }

  /** A predefined type expression for a decimal value including IEEE floating point syntax.
    *
    * @param loc
    *   The location of the decimal integer type expression
    */
  @JSExportTopLevel("Decimal")
  case class Decimal(loc: At, whole: Long, fractional: Long) extends RealTypeExpression {

    /** Format the node to a string */
    override def format: String = s"Decimal($whole,$fractional)"
  }

  /** A predefined type expression for a real number value.
    *
    * @param loc
    *   The location of the real number type expression
    */
  @JSExportTopLevel("Real")
  case class Real(loc: At) extends PredefinedType with RealTypeExpression

  /** A predefined type expression for the SI Base unit for Current (amperes)
    * @param loc
    *   \- The locaitonof the current type expression
    */
  @JSExportTopLevel("Current")
  case class Current(loc: At) extends PredefinedType with RealTypeExpression

  /** A predefined type expression for the SI Base unit for Length (meters)
    * @param loc
    *   The location of the current type expression
    */
  @JSExportTopLevel("Length")
  case class Length(loc: At) extends PredefinedType with RealTypeExpression

  /** A predefined type expression for the SI Base Unit for Luminosity (candela)
    * @param loc
    *   The location of the luminosity expression
    */
  @JSExportTopLevel("Luminosity")
  case class Luminosity(loc: At) extends PredefinedType with RealTypeExpression

  @JSExportTopLevel("Mass")
  case class Mass(loc: At) extends PredefinedType with RealTypeExpression

  /** A predefined type expression for the SI Base Unit for Mole (mole)
    * @param loc
    *   The location of the mass type expression
    */
  @JSExportTopLevel("Mole")
  case class Mole(loc: At) extends PredefinedType with RealTypeExpression

  /** A predefined type expression for the SI Base Unit for Temperature (Kelvin)
    * @param loc
    *   The location of the mass type expression
    */
  @JSExportTopLevel("Temperature")
  case class Temperature(loc: At) extends PredefinedType with RealTypeExpression

  sealed trait TimeType extends PredefinedType

  /** A predefined type expression for a calendar date.
    *
    * @param loc
    *   The location of the date type expression.
    */
  @JSExportTopLevel("Date")
  case class Date(loc: At) extends TimeType {

    override def isAssignmentCompatible(other: TypeExpression): Boolean = {
      super.isAssignmentCompatible(other) || other.isInstanceOf[DateTime] ||
      other.isInstanceOf[TimeStamp] || other.isInstanceOf[String_] ||
      other.isInstanceOf[Pattern]
    }
  }

  /** A predefined type expression for a clock time with hours, minutes, seconds.
    *
    * @param loc
    *   The location of the time type expression.
    */
  @JSExportTopLevel("Time")
  case class Time(loc: At) extends TimeType {

    override def isAssignmentCompatible(other: TypeExpression): Boolean = {
      super.isAssignmentCompatible(other) || other.isInstanceOf[DateTime] ||
      other.isInstanceOf[TimeStamp] || other.isInstanceOf[String_] ||
      other.isInstanceOf[Pattern]
    }
  }

  /** A predefined type expression for a calendar date and clock time combination.
    *
    * @param loc
    *   The location of the datetime type expression.
    */
  @JSExportTopLevel("DateTime")
  case class DateTime(loc: At) extends TimeType {

    override def isAssignmentCompatible(other: TypeExpression): Boolean = {
      super.isAssignmentCompatible(other) || other.isInstanceOf[Date] || other
        .isInstanceOf[DateTime] ||
      other.isInstanceOf[ZonedDateTime] || other.isInstanceOf[TimeStamp] || other
        .isInstanceOf[String_] ||
      other.isInstanceOf[Pattern]
    }
  }

  @JSExportTopLevel("ZonedDate")
  case class ZonedDate(loc: At, zone: Option[LiteralString] = None) extends TimeType {

    override def isAssignmentCompatible(other: TypeExpression): Boolean = {
      super.isAssignmentCompatible(other) || other.isInstanceOf[ZonedDateTime] ||
      other.isInstanceOf[DateTime] || other.isInstanceOf[Date] || other.isInstanceOf[String_] ||
      other.isInstanceOf[Pattern]
    }

    override def format: String = s"ZonedDateTime(${zone.map(_.format).getOrElse("\"UTC\"")})"
  }

  @JSExportTopLevel("ZonedDateTime")
  case class ZonedDateTime(loc: At, zone: Option[LiteralString] = None) extends TimeType {

    override def isAssignmentCompatible(other: TypeExpression): Boolean = {
      super.isAssignmentCompatible(other) || other.isInstanceOf[ZonedDateTime] ||
      other.isInstanceOf[DateTime] || other.isInstanceOf[Date] ||
      other.isInstanceOf[TimeStamp] || other.isInstanceOf[String_] ||
      other.isInstanceOf[Pattern]
    }

    override def format: String = s"ZonedDateTime(${zone.map(_.format).getOrElse("\"UTC\"")})"
  }

  /** A predefined type expression for a timestamp that records the number of milliseconds from the
    * epoch.
    *
    * @param loc
    *   The location of the timestamp
    */
  @JSExportTopLevel("TimeStamp")
  case class TimeStamp(loc: At) extends TimeType {
    override def isAssignmentCompatible(other: TypeExpression): Boolean = {
      super.isAssignmentCompatible(other) || other.isInstanceOf[DateTime] ||
      other.isInstanceOf[Date] || other.isInstanceOf[String_] ||
      other.isInstanceOf[Pattern]
    }
  }

  /** A predefined type expression for a time duration that records the number of milliseconds
    * between two fixed points in time
    *
    * @param loc
    *   The location of the duration type expression
    */
  @JSExportTopLevel("Duration")
  case class Duration(loc: At) extends TimeType

  /** A predefined type expression for a universally unique identifier as defined by the Java
    * Virtual Machine.
    *
    * @param loc
    *   The location of the UUID type expression
    */
  @JSExportTopLevel("UUID")
  case class UUID(loc: At) extends PredefinedType

  /** A predefined type expression for a Uniform Resource Locator of a specific schema.
    *
    * @param loc
    *   The location of the URL type expression
    * @param scheme
    *   The scheme to which the URL is constrained.
    */
  @JSExportTopLevel("URI")
  case class URI(loc: At, scheme: Option[LiteralString] = None) extends PredefinedType {
    override def format: String = s"$kind(${scheme.map(_.format).getOrElse("\"https\"")})"
    override def kind: String = "URL"
  }

  /** A predefined type expression for a location on earth given in latitude and longitude.
    *
    * @param loc
    *   The location of the LatLong type expression.
    */
  @JSExportTopLevel("Location")
  case class Location(loc: At) extends PredefinedType

  enum BlobKind:
    case Text, XML, JSON, Image, Audio, Video, CSV, FileSystem

  @JSExportTopLevel("Blob")
  case class Blob(loc: At, blobKind: BlobKind) extends PredefinedType {
    override def format: String = s"$kind($blobKind)"
  }

  /** A predefined type expression for a type that can have no values
    *
    * @param loc
    *   The location of the nothing type expression.
    */
  @JSExportTopLevel("Nothing")
  case class Nothing(loc: At) extends PredefinedType {
    override def isAssignmentCompatible(other: TypeExpression): Boolean = false
  }

  /** Base trait for a reference to an aggregate type: the four messages
    * (command/event/query/result) plus record. A Record is data, not a message (A9b), so it is an
    * `AggregateRef` but NOT a [[MessageRef]]. `messageKind` is the aggregate use-case
    * discriminator.
    */
  sealed trait AggregateRef extends Reference[Type] {
    def messageKind: AggregateUseCase

    override def format: String =
      s"${messageKind.useCase.toLowerCase} ${pathId.format}"
  }

  /** Base trait for the four kinds of message references (command/event/query/result). */
  sealed trait MessageRef extends AggregateRef

  @JSExportTopLevel("MessageRef")
  object MessageRef {
    // A9b: a concrete CommandRef (not an anonymous MessageRef) so that sealed
    // matches over the four MessageRef subclasses stay exhaustive.
    lazy val empty: MessageRef = CommandRef(At.empty, PathIdentifier.empty)
  }

  /** A Reference to a command message type
    *
    * @param loc
    *   The location of the reference
    * @param pathId
    *   The path identifier to the event type
    */
  @JSExportTopLevel("CommandRef")
  case class CommandRef(
    loc: At,
    pathId: PathIdentifier
  ) extends MessageRef {
    def messageKind: AggregateUseCase = AggregateUseCase.CommandCase
  }

  /** A Reference to an event message type
    *
    * @param loc
    *   The location of the reference
    * @param pathId
    *   The path identifier to the event type
    */
  @JSExportTopLevel("EventRef")
  case class EventRef(
    loc: At,
    pathId: PathIdentifier
  ) extends MessageRef:
    def messageKind: AggregateUseCase = AggregateUseCase.EventCase
  end EventRef

  /** A reference to a query message type
    *
    * @param loc
    *   The location of the reference
    * @param pathId
    *   The path identifier to the query type
    */
  @JSExportTopLevel("QueryRef")
  case class QueryRef(
    loc: At,
    pathId: PathIdentifier
  ) extends MessageRef:
    def messageKind: AggregateUseCase = AggregateUseCase.QueryCase
  end QueryRef

  /** A reference to a result message type
    *
    * @param loc
    *   The location of the reference
    * @param pathId
    *   The path identifier to the result type
    */
  @JSExportTopLevel("ResultRef")
  case class ResultRef(
    loc: At,
    pathId: PathIdentifier
  ) extends MessageRef:
    def messageKind: AggregateUseCase = AggregateUseCase.ResultCase
  end ResultRef

  /** A reference to a record type. A9b: a record is DATA, so `RecordRef` is an [[AggregateRef]] but
    * NOT a [[MessageRef]] — it cannot be sent/told/handled/replied, only used for state data, morph
    * values, and repository schemas.
    *
    * @param loc
    *   The location of the reference
    * @param pathId
    *   The path identifier to the record type
    */
  @JSExportTopLevel("RecordRef")
  case class RecordRef(
    loc: At = At.empty,
    pathId: PathIdentifier = PathIdentifier.empty
  ) extends AggregateRef:
    def messageKind: AggregateUseCase = AggregateUseCase.RecordCase
    override def isEmpty: Boolean =
      super.isEmpty && loc.isEmpty && pathId.isEmpty
  end RecordRef

  /** A type definition which associates an identifier with a type expression.
    *
    * @param loc
    *   The location of the type definition
    * @param id
    *   The name of the type being defined
    * @param typEx
    *   The type expression of the type being defined
    * @param metadata
    *   The metadata for the Type
    */
  @JSExportTopLevel("Type")
  case class Type(
    loc: At,
    id: Identifier,
    typEx: TypeExpression,
    metadata: Contents[MetaData] = Contents.empty[MetaData]()
  ) extends Branch[TypeContents]:
    def contents: Contents[TypeContents] = {
      val type_contents: Seq[TypeContents] =
        typEx match
          case a: Aggregation                    => a.fields ++ a.methods
          case a: AggregateUseCaseTypeExpression => a.fields ++ a.methods
          case Enumeration(_, enumerators)       => enumerators.toSeq
          case _                                 => Seq.empty[TypeContents]
        end match
      type_contents.toContents
    }

    final override def kind: String = {
      typEx match {
        case AggregateUseCaseTypeExpression(_, useCase, _, _) => useCase.useCase
        case _                                                => "Type"
      }
    }

    def format: String =
      Declaration.typeKeyword(this) + " " + id.format + Declaration.ascription(this)
  end Type

  /** A reference to a type definition
    *
    * @param loc
    *   The location in the source where the reference to the type is made
    * @param keyword
    *   The keyword used to designate the type at the point of reference
    * @param pathId
    *   The path identifier of the reference type
    */
  @JSExportTopLevel("TypeRef")
  case class TypeRef(
    loc: At = At.empty,
    keyword: String = "type",
    pathId: PathIdentifier = PathIdentifier.empty
  ) extends Reference[Type] {
    override def format: String = s"$keyword ${pathId.format}"
  }
  object TypeRef { def empty: TypeRef = TypeRef() }

  @JSExportTopLevel("FieldRef")
  case class FieldRef(
    loc: At = At.empty,
    pathId: PathIdentifier = PathIdentifier.empty
  ) extends Reference[Field] {
    override def format: String = s"field ${pathId.format}"
  }

  @JSExportTopLevel("InvariantRef")
  case class InvariantRef(
    loc: At = At.empty,
    pathId: PathIdentifier = PathIdentifier.empty
  ) extends Reference[Invariant] {
    override def format: String = s"invariant ${pathId.format}"
  }

  //////////////////////////////////////////////////////////////////////////////////////// CONSTANT

  /** A definition that represents a constant value for reference in behaviors
    *
    * @param loc
    *   The location in the source of the Constant
    * @param id
    *   The unique identifier of the Constant
    * @param typeEx
    *   The type expression goverining the range of values the constant can have
    * @param value
    *   The value of the constant
    * @param metadata
    *   The metadata for the Constant
    */
  @JSExportTopLevel("Constant")
  case class Constant(
    loc: At,
    id: Identifier,
    typeEx: TypeExpression,
    value: LiteralString,
    metadata: Contents[MetaData] = Contents.empty[MetaData]()
  ) extends Leaf {

    /** Format the node to a string */
    override def format: String =
      s"const ${id.format} is ${typeEx.format} = ${value.format}"
  }

  @JSExportTopLevel("ConstantRef")
  case class ConstantRef(
    loc: At = At.empty,
    pathId: PathIdentifier = PathIdentifier.empty
  ) extends Reference[Constant] {
    override def format: String = s"constant ${pathId.format}"
  }

  ////////////////////////////////////////////////////////////////////////////////////////// VALUES

  /** A54: the union of value expressions usable as a statement operand. Today a value is a
    * pseudo-code [[LiteralString]], a [[Constructor]] of a message/record, a [[ValueRef]] to a
    * named value in scope, or a [[GetValue]] that reads a UI input or entity state. Designed to be
    * extended: A28 adds a `BooleanExpression` arm. All arms are [[RiddlValue]]s so `.format` and
    * `.loc` are available on the union directly.
    */
  type Value =
    LiteralString | PromptValue | Constructor | ValueRef | GetValue | BooleanExpression | Call |
      Ask | SelfValue | Initiate

  /** A54: a single argument supplied to a [[Constructor]]. Positional when `name` is `None`; named
    * (`id = value`) when `name` is `Some`. Validation requires positional arguments to precede
    * named ones and matches names/arity/types against the target aggregate's fields.
    *
    * @param loc
    *   The location of the argument in the source
    * @param name
    *   The field name for a named argument, or `None` for a positional argument
    * @param value
    *   The [[Value]] supplied for this argument
    */
  // NOTE: `loc` is NOT defaulted here (unlike the fully-defaultable refs / ValueRef): @JSExportTopLevel
  // requires any defaulted parameter to be trailing, and `value`/`name` have no empty default. So
  // `loc` stays required, matching every other exported statement/node with required trailing fields.
  @JSExportTopLevel("ConstructorArg")
  case class ConstructorArg(
    loc: At,
    name: Option[Identifier],
    value: Value
  ) extends RiddlValue:
    override def kind: String = "Constructor Argument"
    def format: String = name match
      case Some(id) => s"${id.format} = ${value.format}"
      case None     => value.format
  end ConstructorArg

  /** A54: constructs a message or record value by supplying arguments for its aggregate fields.
    *
    * @param loc
    *   The location of the constructor in the source
    * @param ref
    *   The reference to the message ([[MessageRef]]) or record ([[RecordRef]]) type to construct
    * @param args
    *   The arguments supplied to the constructor
    */
  // `loc` required (not defaulted): see the ConstructorArg note — @JSExportTopLevel forbids a
  // non-trailing default and `ref`/`args` have no empty default.
  @JSExportTopLevel("Constructor")
  case class Constructor(
    loc: At,
    ref: MessageRef | RecordRef,
    args: Seq[ConstructorArg]
  ) extends RiddlValue:
    override def kind: String = "Constructor"
    def format: String = s"${ref.format}(${args.map(_.format).mkString(", ")})"
  end Constructor

  /** A24: a call of a pure [[Function]] to obtain its result value. "Functions only" is a *target*
    * restriction: the callee is a [[FunctionRef]], so only a `Function` can be called (never an
    * entity, context, or other definition). A call is effect-free — functions are pure (A26) — so
    * it composes anywhere a [[Value]] is valid: handler bodies and function bodies alike (`let x =
    * call function F(a, b)`, `set f to call function F(...)`, `return call function F(...)`, and
    * comparison/logical operands). Its type (for downstream checks) is the called function's
    * `output`. Arguments reuse [[ConstructorArg]] (positional | named) and bind to the fields of
    * the function's `input` aggregate; empty `()` is allowed for a no-input function.
    *
    * @param loc
    *   The location of the call in the source
    * @param function
    *   The reference to the [[Function]] being called
    * @param args
    *   The arguments supplied to the call
    */
  // `loc` required (not defaulted): see the ConstructorArg note — @JSExportTopLevel forbids a
  // non-trailing default and `function`/`args` have no empty default.
  @JSExportTopLevel("Call")
  case class Call(
    loc: At,
    function: FunctionRef,
    args: Seq[ConstructorArg]
  ) extends RiddlValue:
    override def kind: String = "Call"
    def format: String = s"call ${function.format}(${args.map(_.format).mkString(", ")})"
  end Call

  /** `ask query Foo of entity Bar` -- a request whose ANSWER is a value.
    *
    * RIDDL could already send a message (`tell`) and declare what handling one produces
    * (`yields`/`replies`). What it could not say is that two messages are two halves of ONE
    * interaction: `yield` names no destination, `tell` says nothing about a reply, and the word
    * `correlation` appeared nowhere in the language. A generator therefore could not tell
    * fire-and-forget from a request whose answer the caller awaits.
    *
    * `ask` declares that CORRELATION and nothing more. It deliberately implies no mechanism -- not
    * a Future, not a temp actor, not a correlation-id field, not a blocking call. All four are
    * lowerings a generator should be free to choose between, on the same principle that settled
    * `message_envelope`: RIDDL specifies meaning and leaves representation to generators.
    *
    * QUERIES ONLY (Reid, 2026-08-08). Asking a command, event, result or record is an Error: a
    * query is the message kind that exists to be answered, and its declared `replies result X` is
    * what gives the answer a type. That is why this could not be built before the `yield`/`reply`
    * split -- there was no per-query declaration to look the type up from.
    *
    * It is a [[Value]] rather than a Statement so `let answer = ask query Foo of entity Bar` works
    * through the EXISTING [[LetStatement]], whose `expression` is already a `Value`. [[Call]] set
    * the precedent for a Value that names an effect.
    *
    * @param loc
    *   The location of the ask in the source
    * @param query
    *   The query being asked. A [[QueryRef]] specifically, so the kind restriction is structural.
    * @param processor
    *   The processor being asked
    */
  @JSExportTopLevel("Ask")
  case class Ask(
    loc: At,
    query: QueryRef,
    processor: ProcessorRef[Processor[?]]
  ) extends RiddlValue:
    override def kind: String = "Ask"
    def format: String = s"ask ${query.format} of ${processor.format}"
  end Ask

  /** `initiate <processor>(args)` -- bring an instance into being and yield its identity.
    *
    * Creation still completes only when `on init` finishes, so this does NOT introduce a second
    * way for an instance to exist (CM line 999): it supplies the invocation that was missing.
    * The value is the newly minted `Id(P)`, which is system-generated and opaque -- a BUSINESS
    * key belongs in `on init`'s parameters and lives in state.
    *
    * @param loc
    *   The location of the `initiate` in the source
    * @param processor
    *   The processor to bring an instance of into being
    * @param args
    *   The arguments supplied to the target's `on init` parameters (empty when `on init` takes
    *   none, in which case the parentheses are omitted -- see `StatementParser.initiateValue`)
    */
  @JSExportTopLevel("Initiate")
  case class Initiate(
    loc: At,
    processor: ProcessorRef[Processor[?]],
    args: Seq[ConstructorArg]
  ) extends RiddlValue:
    override def kind: String = "Initiate"
    def format: String =
      val argList = if args.isEmpty then "" else args.map(_.format).mkString("(", ", ", ")")
      s"initiate ${processor.format}$argList"
  end Initiate

  /** `self` -- the currently executing processor instance, and `self.<field>` on it.
    *
    * Its TYPE is a synthesized [[Aggregation]] rather than a bespoke node. That is deliberate
    * and load-bearing: because the type is an ordinary record, `let me = self` followed by
    * `me.id` resolves through the SAME `ValueRef` path walk every other value uses, so no
    * resolution rule anywhere needs to know `self` exists.
    *
    * The type cannot be user-nameable -- `self.id` is `Id(Order)` in an Order handler and
    * `Id(Shipping)` in a Shipping one -- so `let me: T = self` has no `T` to write, and `self`
    * itself is not assignable into a message field. Pass `self.id`.
    */
  @JSExportTopLevel("SelfValue")
  case class SelfValue(loc: At, field: Option[Identifier] = None) extends RiddlValue:
    override def kind: String = "Self"
    def format: String = s"self${field.map("." + _.format).getOrElse("")}"
  end SelfValue

  object SelfValue:
    /** The CLOSED set of fields. Adding one is a language change, not a detail: see the
      * admission principle in the design spec -- `self` carries what cannot be known
      * statically, which is why `version` is here and `isClustered` is not.
      */
    val fieldNames: Seq[String] = Seq("id", "version")

    /** The synthesized record type of `self` within the processor `path` names.
      *
      * It takes the PATH, not the [[Processor]]: the only thing `self`'s shape depends on is which
      * processor `self.id` identifies, and that is the fully-qualified path. The Processor itself
      * was a parameter here until the final review of the instance-identity branch, and was never
      * read -- a parameter a function ignores is a claim about its inputs that is not true.
      */
    def aggregation(path: PathIdentifier): Aggregation =
      Aggregation(
        At.empty,
        Contents(
          Field(At.empty, Identifier(At.empty, "id"), UniqueId(At.empty, path)),
          Field(At.empty, Identifier(At.empty, "version"), String_(At.empty))
        )
      )
  end SelfValue

  /** A54: a reference to a named value in scope. Resolved (at validation time) from one of four
    * sources: a `let`-bound local, a field of the handled on-clause message, a field of the
    * enclosing entity's state, or — only within a function `return` — a field of the function's
    * `requires` input.
    *
    * @param loc
    *   The location of the reference in the source
    * @param path
    *   The path identifier naming the value
    */
  @JSExportTopLevel("ValueRef")
  case class ValueRef(
    loc: At = At.empty,
    path: PathIdentifier = PathIdentifier.empty
  ) extends RiddlValue:
    override def kind: String = "Value Reference"
    def format: String = path.format
  end ValueRef

  /** A45/A45b: reads a value from a UI [[Input]] (`get from input <ref>`) or an entity [[State]]
    * (`get from state <ref>`). A general value expression usable anywhere a [[Value]] is expected.
    *
    * @param loc
    *   The location of the get expression in the source
    * @param source
    *   The [[InputRef]] or [[StateRef]] to read from
    */
  // `loc` required (not defaulted): see the ConstructorArg note — @JSExportTopLevel forbids a
  // non-trailing default and `source` has no empty default.
  @JSExportTopLevel("GetValue")
  case class GetValue(
    loc: At,
    source: InputRef | StateRef
  ) extends RiddlValue:
    override def kind: String = "Get Value"
    def format: String = s"get from ${source.format}"
  end GetValue

  /** A54: an AI-computed value expressed by a natural-language prompt (`prompt("…")`). Distinct
    * from the deprecated `prompt` STATEMENT (`prompt "…"`, no parens) by the parenthesized form. A
    * bare [[LiteralString]] in a value position is a literal constant; a `PromptValue` asks the
    * backend to invoke AI codegen. No resolution needed — the prompt is literal text.
    *
    * @param loc
    *   The location of the prompt value in the source
    * @param prompt
    *   The prompt text to provide to an AI code generator
    */
  // `loc` required (not defaulted): see the ConstructorArg note — @JSExportTopLevel forbids a
  // non-trailing default and `prompt` has no empty default.
  @JSExportTopLevel("PromptValue")
  case class PromptValue(
    loc: At,
    prompt: LiteralString
  ) extends RiddlValue:
    override def kind: String = "Prompt Value"
    def format: String = s"prompt(${prompt.format})"
  end PromptValue

  /** A28: the relational operator of a [[ComparisonExpression]]. `symbol` is the surface syntax
    * (`==`, `!=`, `<`, `>`, `<=`, `>=`) used by both the parser and `format`.
    */
  enum ComparisonOperator(val symbol: String):
    case EQ extends ComparisonOperator("==")
    case NE extends ComparisonOperator("!=")
    case LT extends ComparisonOperator("<")
    case GT extends ComparisonOperator(">")
    case LE extends ComparisonOperator("<=")
    case GE extends ComparisonOperator(">=")
  end ComparisonOperator

  /** A28: the boolean connective of a [[LogicalExpression]]. `symbol` is the surface keyword
    * (`and`, `or`). Left-associative binary; the parser folds a `rep` left.
    */
  enum LogicalOperator(val symbol: String):
    case And extends LogicalOperator("and")
    case Or extends LogicalOperator("or")
  end LogicalOperator

  /** A28: the operand of a [[ComparisonExpression]]. Comparisons are TYPE-SAFE and therefore
    * compare two TYPED references only — a [[ValueRef]] (a `let`-local, a field of the handled
    * message / entity state / function input, or a bare path naming a [[Constant]]), a [[GetValue]]
    * (a UI input or entity-state read), or a [[ConstantRef]] (`constant <path>`). RIDDL
    * deliberately has NO magic-constant comparison operands: literals, constructors and prompt
    * values are not comparands — to compare against a constant, declare a `constant` and reference
    * it. The parser enforces this (a non-ref comparison operand is a PARSE error); validation
    * enforces type compatibility.
    */
  type Comparand = ValueRef | GetValue | ConstantRef

  /** A28: the boolean-expression sub-language. An arm of the [[Value]] union so `let`/`set`/`put`/
    * `return` accept booleans for free. All cases are [[RiddlValue]]s so `.format`/`.loc` work on
    * the union directly. Logical/`not` operands are typed as [[Value]] (not `BooleanExpression`)
    * because the layered precedence parser returns a bare `Value` atom — e.g. a [[ValueRef]] to a
    * boolean field — at any operand position; validation (not the type system) enforces that
    * logical/`not` operands are boolean. Comparison operands, by contrast, are narrowed to
    * [[Comparand]] (ref-only) so magic-constant comparisons cannot be constructed at all.
    */
  sealed trait BooleanExpression extends RiddlValue

  /** An invariant named in a condition: `invariant X`, or `invariant X with <expr>`.
    *
    * A17 was already satisfied by the BARE spelling — `when not NonNegative then` resolves the name
    * to the Invariant through the ordinary `ValueRef` route and has worked all along. What did NOT
    * work was the keyword-qualified spelling an author naturally writes after learning `require
    * invariant X`, which mis-parsed: `invariant` was consumed as a bare value name and the parser
    * then asked for a comparison operator, pointing PAST the real problem. This node makes the two
    * statements spell a reference the same way.
    *
    * It extends [[BooleanExpression]] rather than merely being a [[Value]] because an invariant IS
    * a boolean by construction — that is what lets `when invariant X then` stand alone, since
    * `booleanExprOnly` admits only real boolean expressions and a bare atom backtracks out of it.
    *
    * `argument` is the same `with <expr>` the `require` statement takes. It is OPTIONAL here even
    * for an invariant declaring `requires <type>` (author's ruling, 2026-08-04): a condition asks
    * whether the rule holds, and is never rejected for omitting the value — unlike `require
    * invariant X`, which APPLIES the rule and so must be handed what the rule reads.
    */
  case class InvariantCondition(
    loc: At,
    ref: InvariantRef,
    argument: Option[Value] = None
  ) extends BooleanExpression:
    override def kind: String = "Invariant Condition"
    def format: String = ref.format + argument.map(a => s" with ${a.format}").getOrElse("")
  end InvariantCondition

  /** A28: a boolean constant (`true` / `false`). Matched only within the boolean-expression rules
    * so `true`/`false` remain legal identifiers elsewhere.
    */
  // `loc` required (not defaulted): @JSExportTopLevel forbids a non-trailing default and `value` has
  // no empty default — matching the sibling value nodes (Constructor/GetValue/PromptValue).
  @JSExportTopLevel("BooleanLiteral")
  case class BooleanLiteral(loc: At, value: Boolean) extends BooleanExpression:
    override def kind: String = "Boolean Literal"
    def format: String = if value then "true" else "false"
  end BooleanLiteral

  /** A28: a relational comparison of two values (`left <op> right`). Non-associative — exactly one
    * operator and two operands.
    */
  @JSExportTopLevel("ComparisonExpression")
  case class ComparisonExpression(
    loc: At,
    op: ComparisonOperator,
    left: Comparand,
    right: Comparand
  ) extends BooleanExpression:
    override def kind: String = "Comparison Expression"
    def format: String = s"${left.format} ${op.symbol} ${right.format}"
  end ComparisonExpression

  /** A28: a binary logical connective (`left and right`, `left or right`). Left-associative; the
    * parser folds a `rep` left. A logical sub-expression operand is parenthesized in `format` so
    * the emitted text re-parses to the same tree regardless of precedence.
    */
  @JSExportTopLevel("LogicalExpression")
  case class LogicalExpression(
    loc: At,
    op: LogicalOperator,
    left: Value,
    right: Value
  ) extends BooleanExpression:
    override def kind: String = "Logical Expression"
    private def paren(v: Value): String = v match
      case _: LogicalExpression => s"(${v.format})"
      case _                    => v.format
    def format: String = s"${paren(left)} ${op.symbol} ${paren(right)}"
  end LogicalExpression

  /** A28: logical negation (`not expr`). A logical sub-expression operand is parenthesized in
    * `format` so `not (a and b)` re-parses as `Not(And(a, b))`, not `And(Not(a), b)`.
    */
  @JSExportTopLevel("NotExpression")
  case class NotExpression(loc: At, expr: Value) extends BooleanExpression:
    override def kind: String = "Not Expression"
    def format: String = expr match
      case _: LogicalExpression => s"not (${expr.format})"
      case _                    => s"not ${expr.format}"
  end NotExpression

  /** A54: accessors for a widened message/record operand (a bare ref, or a [[Constructor]] whose
    * ref names the constructed message/record). Used by send/tell/yield (message) and morph
    * (record).
    */
  extension (m: MessageRef | RecordRef | Constructor)
    def operandPathId: PathIdentifier = m match
      case ref: (MessageRef | RecordRef) => ref.pathId
      case c: Constructor                => c.ref.pathId
    def operandMessageKind: AggregateUseCase = m match
      case ref: (MessageRef | RecordRef) => ref.messageKind
      case c: Constructor                => c.ref.messageKind
  end extension

  /** A56: the path of a `tell`/`send` operand, which may additionally be a [[ValueRef]] naming an
    * on-clause binding.
    *
    * For a binding the path is the LOCAL name (`p`), which is exactly the key
    * `ResolutionPass.resolveValueRef` registers in the refMap against the handled message's Type —
    * so `refMap.definitionOf[Type](…)` works unchanged for all three arms.
    *
    * Only the path generalizes. There is deliberately no `operandMessageKind` counterpart here: a
    * ref carries its kind syntactically but a binding's kind is only known once resolved, so asking
    * for it without a resolver would force a wrong answer. Use
    * `ValidationPass.operandMessageKind`, which returns an Option.
    */
  extension (m: MessageRef | Constructor | ValueRef)
    def deliverableOperandPathId: PathIdentifier = m match
      case ref: MessageRef => ref.pathId
      case c: Constructor  => c.ref.pathId
      case vr: ValueRef    => vr.path
  end extension

  ////////////////////////////////////////////////////////////////////////////////////// STATEMENTS

  /** Base trait of all Statements that can occur in [[OnClause]]s */
  sealed trait Statement extends RiddlValue:

    /** A12/A36: can this statement, BY ITSELF, be a runtime failure point? A plain computed
      * predicate (no constructor field ⇒ zero reflection cost: no prettify/BAST/JSON, no
      * `FORMAT_REVISION` bump). The shared source of truth for the "can-fail" census: `send`,
      * `tell`, `yield` and `put` interact with the outside world and may fail; every other
      * statement cannot fail by itself. NOTE this is a DIFFERENT axis from A23's
      * `isEffectStatement` — `set` is an effect (mutates state) but cannot fail. Value-level
      * failure points (`call`/`get`, which are [[Value]]s, not [[Statement]]s) are counted
      * separately by scanning a statement's value expressions; they are deliberately NOT folded
      * into this predicate.
      */
    def canFail: Boolean = false
  end Statement

  /** A statement whose behavior is specified as a text string allowing a prompt for AI-based
    * simulation to be specified.
    *
    * @param loc
    *   The location where the action occurs in the source
    * @param what
    *   The prompt text to provide to an AI simulator
    */
  @JSExportTopLevel("PromptStatement")
  case class PromptStatement(
    loc: At,
    what: LiteralString
  ) extends Statement {
    override def kind: String = "Prompt Statement"
    def format: String = what.format
  }

  /** A statement that is intended to generate a runtime error in the application or otherwise
    * indicate an error condition
    *
    * @param loc
    *   The location where the action occurs in the source
    * @param message
    *   The error message to report
    */
  @JSExportTopLevel("ErrorStatement")
  case class ErrorStatement(
    loc: At,
    message: LiteralString
  ) extends Statement {
    override def kind: String = "Error Statement"
    def format: String = s"error ${message.format}"
  }

  /** A statement that requires a boolean condition to be true for execution to continue. If the
    * condition is false, an error is generated. The condition can be either a literal string
    * expression or a reference to a named invariant.
    *
    * @param loc
    *   The location where the statement occurs in the source
    * @param condition
    *   Either a boolean expression as a string or a reference to a named invariant
    */
  @JSExportTopLevel("RequireStatement")
  case class RequireStatement(
    loc: At,
    condition: LiteralString | InvariantRef | BooleanExpression,
    /** The value handed to an invariant that declares `requires <type>` — the `with <expr>` part.
      *
      * Only meaningful with an [[InvariantRef]] condition, and only for an invariant whose
      * `requires` is a [[TypeRef]]: that is the one form the ambient scope cannot supply, so the
      * clause gathers the value (where sending IS legal) and hands it in.
      */
    argument: Option[Value] = None
  ) extends Statement {
    override def kind: String = "Require Statement"
    private def arg: String = argument.map(a => s" with ${a.format}").getOrElse("")
    def format: String = condition match {
      case ls: LiteralString     => s"require ${ls.format}$arg"
      case ir: InvariantRef      => s"require ${ir.format}$arg"
      case be: BooleanExpression => s"require ${be.format}$arg" // A28
    }
  }

  /** A statement that sets a value of a field
    *
    * @param loc
    *   THe locaiton of the statement
    * @param field
    *   The field that is the target of the value change
    * @param value
    *   A description of the value to set as a [[LiteralString]]
    */
  @JSExportTopLevel("SetStatement")
  case class SetStatement(
    loc: At,
    field: FieldRef | StateRef,
    // A54: the value to set is now a full value expression (literal, constructor, ref, get, prompt).
    value: Value
  ) extends Statement {
    override def kind: String = "Set Statement"
    def format: String = s"set ${field.format} to ${value.format}"
  }

  /** An action that sends a message to an [[Inlet]] or [[Outlet]].
    *
    * @param loc
    *   The location in the source of the send action
    * @param msg
    *   The constructed message to be sent
    * @param portlet
    *   The inlet or outlet to which the message is sent
    */
  @JSExportTopLevel("SendStatement")
  case class SendStatement(
    loc: At,
    // A54: the message operand is a bare ref or a constructor that builds the message value.
    // A56: it may also be a [[ValueRef]] naming an on-clause binding. See [[TellStatement]].
    msg: MessageRef | Constructor | ValueRef,
    portlet: PortletRef[Portlet]
  ) extends Statement {
    override def kind: String = "Send Statement"
    override def canFail: Boolean = true // A12: sending to a portlet may fail
    def format: String = s"send ${msg.format} to ${portlet.format}"
  }

  /** An statement that morphs the state of an entity to a new structure
    *
    * @param loc
    *   The location of the morph action in the source
    * @param entity
    *   The entity to be affected
    * @param state
    *   The reference to the new state structure
    */
  @JSExportTopLevel("MorphStatement")
  case class MorphStatement(
    loc: At,
    entity: EntityRef,
    state: StateRef,
    // A9b: the morph carries the RECORD that types the target state (its data), not a message.
    // A54: a bare RecordRef names existing data; a Constructor R("v1",…) builds it inline.
    // Task 2 of the message-value design: it may also be a [[ValueRef]] naming a value already in
    // hand -- a state-record field, `let`-local, function result or `ask` result. Without it a
    // generator has nothing to lower and emits a hole; this and `send`/`tell` together were 98.2%
    // of riddlg's `AI FILL` markers on reactive-bbq.
    value: RecordRef | Constructor | ValueRef
  ) extends Statement {
    override def kind: String = "Morph Statement"
    def format: String = s"morph ${entity.format} to ${state.format} with ${value.format}"
  }

  /** A statement that changes the behavior of an entity by making it use a new handler for its
    * messages; named for the "become" operation in Akka that does the same for an user.
    *
    * @param loc
    *   The location in the source of the become action
    * @param entity
    *   The entity whose behavior is to change
    * @param handler
    *   The reference to the new handler for the entity
    */
  @JSExportTopLevel("BecomeStatement")
  case class BecomeStatement(
    loc: At,
    entity: EntityRef,
    handler: HandlerRef
  ) extends Statement {
    override def kind: String = "Become Statement"
    def format: String = s"become ${entity.format} to ${handler.format}"
  }

  /** An action that tells a message to an entity. This is very analogous to the tell operator in
    * Akka. Unlike using an Portlet, this implies a direct relationship between the telling entity
    * and the told entity. This action is considered useful in "high cohesion" scenarios. Use
    * [[SendStatement]] to reduce the coupling between entities because the relationship is managed
    * by a [[Context]] 's [[Connector]] instead.
    *
    * @param loc
    *   The location of the tell action
    * @param msg
    *   A constructed message value to send to the entity, probably a command
    * @param processorRef
    *   The processor to which the message is directed
    * @param by
    *   The disambiguator, needed only when the message carries more than one field typed
    *   `Id(target)`. Trailing and defaulted, which is safe here because `TellStatement` has no
    *   other defaulted parameters.
    */
  @JSExportTopLevel("TellStatement")
  case class TellStatement(
    loc: At,
    // A54: the message operand is a bare ref or a constructor that builds the message value.
    // A56: it may also be a [[ValueRef]] naming an on-clause binding -- `on p: command Ping is {
    // tell p to entity F }`. The binding is DECLARED by the enclosing clause, so both the Type and
    // the message kind are recovered from `omc.msg`; see `operandMessageKind`.
    msg: MessageRef | Constructor | ValueRef,
    processorRef: ProcessorRef[Processor[?]],
    by: Option[Identifier] = None
  ) extends Statement {
    override def kind: String = "Tell Statement"
    override def canFail: Boolean = true // A12: telling a processor may fail
    def format: String = s"tell ${msg.format} to ${processorRef.format}${by.map(b => s" by ${b.format}").getOrElse("")}"
  }

  /** A statement that sends a result message back to the sender of the current message. Used in
    * query handlers to return results without needing to know the sender's identity.
    *
    * @param loc
    *   The location of the reply statement
    * @param msg
    *   The result message to send back
    */
  @JSExportTopLevel("YieldStatement")
  case class YieldStatement(
    loc: At,
    // A54: the message operand is a bare ref or a constructor that builds the message value.
    // Task 2 of the message-value design: also a [[ValueRef]]. `yield` was excluded from A56 on the
    // grounds that widening it "would interact with yield conformance (A19)" -- but that comparison
    // is by RESOLVED TYPE, which a ValueRef supplies exactly as a MessageRef does, so conformance
    // is a check to keep working, not a reason to stay narrow.
    msg: MessageRef | Constructor | ValueRef
  ) extends Statement {
    override def kind: String = "Yield Statement"
    override def canFail: Boolean = true // A12: yielding a result to the sender may fail
    def format: String = s"yield ${msg.format}"
  }

  /** Answer a QUERY with its declared result.
    *
    * The counterpart to [[YieldStatement]], and deliberately a separate node rather than a synonym
    * for it. RIDDL has two message pairings, and until 2.0 the syntax did not distinguish them:
    *
    * {{{
    *   command Pay yields  event  Paid   ->  yield event Paid
    *   query   Ask replies result Answer ->  reply result Answer
    * }}}
    *
    * `yield` served BOTH halves while `reply` was a deprecated alias pointing at it
    * (`type ReplyStatement = YieldStatement`), so nothing in a handler body told a reader which
    * half of the language they were in. Reid un-deprecated `reply` and restricted `yield` to
    * events (2026-08-08).
    *
    * They are genuinely different operations, not two spellings: emitting an event as a
    * consequence of a command is not the same act as answering a question, and a generator lowers
    * them differently -- which is why this is a distinct case class and not a kind test on
    * [[YieldStatement]]. It is also what makes `ask` expressible: the value an `ask` produces is
    * the one a `reply` provides.
    *
    * Pairing is enforced in ValidationPass, not the parser: `reply event X` and `yield result X`
    * are Errors that can name BOTH the keyword and the message kind, where a parse failure could
    * only point at the keyword.
    */
  @JSExportTopLevel("ReplyStatement")
  case class ReplyStatement(
    loc: At,
    // Same operand shape as YieldStatement, Task 2 widening included.
    msg: MessageRef | Constructor | ValueRef
  ) extends Statement {
    override def kind: String = "Reply Statement"
    override def canFail: Boolean = true // answering the asker may fail, as yielding may
    def format: String = s"reply ${msg.format}"
  }

  /** A conditional statement for branching logic
    *
    * @param loc
    *   The location of the statement in the model
    * @param condition
    *   The boolean expression to evaluate - either a literal string description or an identifier
    *   referencing a let binding
    * @param thenStatements
    *   The statements to execute if the condition is true
    * @param elseStatements
    *   The statements to execute if the condition is false (optional)
    */
  @JSExportTopLevel("WhenStatement")
  case class WhenStatement(
    loc: At,
    condition: LiteralString | Identifier | ValueRef | BooleanExpression | PromptValue,
    thenStatements: Contents[Statements],
    elseStatements: Contents[Statements] = Contents.empty[Statements](0),
    negated: Boolean = false
  ) extends Statement {
    override def kind: String = "When Statement"
    def format: String = {
      val condStr = condition match {
        case ls: LiteralString     => ls.format
        case id: Identifier        => if negated then s"!${id.format}" else id.format
        case vr: ValueRef          => if negated then s"!${vr.format}" else vr.format // A17
        case be: BooleanExpression => be.format // A28: negation is expressed via `not` in the expr
        // A54: an AI-evaluated condition. This arm was MISSING, and the match therefore threw a
        // MatchError on `when prompt("…")` -- the `condition` union has five members and this had
        // four. It went unseen because `PrettifyVisitor` does NOT route through here: it has its
        // own copy of this dispatch in `RiddlFileEmitter.emitStatement`, and THAT copy has the arm.
        // So the reflectivity round trip, which is what normally proves `format` total, could
        // never reach the hole. It became reachable when `checkUnusedInitiateId` started rendering
        // a clause body to decide whether an `initiate` id is used.
        case pv: PromptValue => pv.format
      }
      val thenStr =
        if thenStatements.isEmpty then "" else thenStatements.toSeq.map(_.format).mkString("\n  ")
      if elseStatements.isEmpty then s"when $condStr then\n$thenStr\n  end"
      else {
        val elseStr =
          if elseStatements.isEmpty then "" else elseStatements.toSeq.map(_.format).mkString("\n  ")
        s"when $condStr then\n$thenStr\nelse\n$elseStr\n  end"
      }
    }
  }

  /** A29: the subject of a [[MatchStatement]] — the value being matched. Either a runtime value
    * reference ([[ValueRef]]), a `get from input/state` read ([[GetValue]]), or a legacy opaque
    * pseudo-code label ([[LiteralString]], kept for backward compatibility). Deliberately NOT a
    * [[ConstantRef]]: matching on a constant subject is pointless (0/1 case can ever match).
    */
  type MatchSubject = ValueRef | GetValue | LiteralString

  /** A29: a structured pattern within a [[MatchCase]]. Three forms: a **type-case**
    * ([[TypePattern]]) that matches when the subject IS a given type / alternant of an `one of {…}`
    * alternation / member of an `any of {…}` enumeration / message subtype; a **value comparison**
    * ([[ComparisonPattern]]) `<op> <comparand>` with the subject as the implicit left operand; and
    * a legacy opaque pseudo-code label ([[LiteralPattern]]). All arms are [[RiddlValue]]s.
    */
  sealed trait MatchPattern extends RiddlValue

  /** A29: a type-case pattern — `case <TypeRef>` (a bare path). Matches when the subject is that
    * type / that alternant / that enumerator / that message subtype.
    */
  @JSExportTopLevel("TypePattern")
  case class TypePattern(loc: At, typeRef: TypeRef) extends MatchPattern:
    override def kind: String = "Type Pattern"
    // Emit only the bare path (a type-case is written `case Shipped`, not `case type Shipped`); the
    // parser re-reads a bare path into a TypeRef with the default `type` keyword, so this round-trips.
    def format: String = typeRef.pathId.format
  end TypePattern

  /** A29: a value-comparison pattern — `case <op> <comparand>` (e.g. `case == Approved`, `case >
    * MaxCount`). Semantics: "subject <op> comparand" — the subject is the implicit left operand.
    * The explicit operator disambiguates from a bare type-case (`case Approved` is a TYPE-case).
    */
  @JSExportTopLevel("ComparisonPattern")
  case class ComparisonPattern(loc: At, op: ComparisonOperator, comparand: Comparand)
      extends MatchPattern:
    override def kind: String = "Comparison Pattern"
    def format: String = s"${op.symbol} ${comparand.format}"
  end ComparisonPattern

  /** A29: a legacy opaque pseudo-code pattern — `case "some label"`. Never resolved or typed; kept
    * for backward compatibility with pre-A29 string `match`/`case` models.
    */
  @JSExportTopLevel("LiteralPattern")
  case class LiteralPattern(loc: At, literal: LiteralString) extends MatchPattern:
    override def kind: String = "Literal Pattern"
    def format: String = literal.format
  end LiteralPattern

  /** A case clause within a match statement (A29: structured [[MatchPattern]] plus an optional
    * `when` guard — `case <pattern> [when <guard>] { … }`). The guard mirrors A17's
    * [[WhenStatement]] condition: a structured [[BooleanExpression]] OR a bare boolean-typed
    * [[ValueRef]] (`case X when active { … }`), validated to be Boolean-typed.
    */
  @JSExportTopLevel("MatchCase")
  case class MatchCase(
    loc: At,
    pattern: MatchPattern,
    guard: Option[BooleanExpression | ValueRef],
    statements: Contents[Statements]
  ) extends RiddlValue {
    override def kind: String = "Match Case"
    def format: String =
      val guardStr = guard.map(g => s" when ${g.format}").getOrElse("")
      s"case ${pattern.format}$guardStr {\n${statements.toSeq.map(_.format).mkString("\n  ")}\n}"
  }

  /** A pattern matching statement for value-based branching
    *
    * @param loc
    *   The location of the statement in the model
    * @param expression
    *   The [[MatchSubject]] to match against
    * @param cases
    *   The case clauses (pattern -> statements)
    * @param default
    *   The default statements if no case matches (optional)
    */
  @JSExportTopLevel("MatchStatement")
  case class MatchStatement(
    loc: At,
    expression: MatchSubject,
    cases: Seq[MatchCase],
    default: Contents[Statements]
  ) extends Statement {
    override def kind: String = "Match Statement"
    def format: String = {
      val casesStr = cases.map(_.format).mkString("\n")
      val defaultStr =
        if default.isEmpty then ""
        else s"\ndefault {\n${default.toSeq.map(_.format).mkString("\n  ")}\n}"
      s"match ${expression.format} {\n$casesStr$defaultStr\n}"
    }
  }

  /** A local immutable value binding statement
    *
    * @param loc
    *   The location of the statement in the model
    * @param identifier
    *   The name of the local variable
    * @param expression
    *   The expression to bind to the variable
    */
  @JSExportTopLevel("LetStatement")
  case class LetStatement(
    loc: At,
    identifier: Identifier,
    typeRef: Option[TypeRef],
    // A54: the bound expression is now a full value expression (literal, constructor, ref, get, prompt).
    expression: Value
  ) extends Statement {
    override def kind: String = "Let Statement"
    def format: String =
      val typeClause = typeRef.map(t => s": ${t.format}").getOrElse("")
      s"let ${identifier.format}$typeClause = ${expression.format}"
  }

  /** A code statement that contains arbitrary code in a specified language
    *
    * @param loc
    *   The location of the statement in the model
    * @param language
    *   The programming language of the code
    * @param body
    *   The code body
    */
  @JSExportTopLevel("CodeStatement")
  case class CodeStatement(
    loc: At,
    language: LiteralString,
    body: String
  ) extends Statement {
    def format: String = s"```${language.s}\n$body```"
    override def kind: String = "Code Statement"
  }

  /** A25: a `foreach` statement — the safe, bounded loop. Iterates a collection-typed value,
    * binding each element to a local `element` identifier that is visible to the nested body
    * statements.
    *
    * A [[Mapping]] is iterated in the DESTRUCTURING form, `foreach k, v in m`, binding the key and
    * the value separately. The alternative was to bind one name to a synthesized `{ key, value }`
    * record; two names were chosen because they need no record type, no addition to the predefined
    * `Riddl` module, and no generics — which RIDDL does not have, so a single named `Entry` could
    * not be typed against an arbitrary mapping's `from`/`to`.
    *
    * Arity is STRICT in both directions, checked in ValidationPass (never in the parser, where an
    * `error()` would preempt the pass chain): exactly two names for a mapping, exactly one for
    * every other collection. Permitting one name over a mapping is what previously bound the
    * element to `Anything` and let `e.whatever` pass unchecked — the hole this closes.
    *
    * @param loc
    *   The location of the statement in the model
    * @param element
    *   The local identifier bound to each element of the collection during iteration. For a
    *   mapping this is the KEY.
    * @param valueElement
    *   The local identifier bound to the VALUE of each mapping entry, present only for the
    *   destructuring form. `None` for every non-mapping collection.
    * @param collection
    *   The collection being traversed. Disambiguated at parse time by the `field` keyword: a
    *   [[FieldRef]] (`foreach o in field X.Y { … }`) names a collection-typed field of the
    *   enclosing entity's state, the handled message, or a function's `requires` input; a bare
    *   [[Identifier]] (`foreach o in myLocal { … }`) names a `let`-bound local whose declared type
    *   is a collection.
    * @param doStatements
    *   The statements to execute for each element of the collection
    */
  // `valueElement` is declared BEFORE `doStatements` and WITHOUT a default: @JSExportTopLevel
  // requires defaulted parameters to be TRAILING, the same rule that shaped A55's `binding` and
  // A57's `envelopeType`.
  @JSExportTopLevel("ForeachStatement")
  case class ForeachStatement(
    loc: At,
    element: Identifier,
    valueElement: Option[Identifier],
    collection: FieldRef | Identifier,
    doStatements: Contents[Statements]
  ) extends Statement {
    override def kind: String = "Foreach Statement"
    def format: String =
      val collectionStr = collection match
        case fr: FieldRef   => fr.format
        case id: Identifier => id.format
      val elements = valueElement.fold(element.format)(v => s"${element.format}, ${v.format}")
      s"foreach $elements in $collectionStr { … }"
  }

  /** A45: a statement that publishes a [[Value]] to a UI [[Output]] (`put <value> to output
    * <ref>`). Allowed only in a Context (application) handler. The value's type is checked against
    * the resolved `Output.putOut`.
    *
    * @param loc
    *   The location of the statement in the model
    * @param value
    *   The value to publish
    * @param output
    *   The output to publish the value to
    */
  @JSExportTopLevel("PutStatement")
  case class PutStatement(
    loc: At,
    value: Value,
    output: OutputRef
  ) extends Statement {
    override def kind: String = "Put Statement"
    override def canFail: Boolean = true // A12: publishing to a UI output may fail
    def format: String = s"put ${value.format} to ${output.format}"
  }

  /** A57: a statement that returns a [[Value]] from a [[Function]] (`return <value>`). Allowed only
    * in a function body. The value's type is checked against the enclosing `Function.output`.
    *
    * @param loc
    *   The location of the statement in the model
    * @param value
    *   The value to return
    */
  @JSExportTopLevel("ReturnStatement")
  case class ReturnStatement(
    loc: At,
    value: Value
  ) extends Statement {
    override def kind: String = "Return Statement"
    def format: String = s"return ${value.format}"
  }

  /** `terminate <processor>(id, args)` -- end an instance by invoking its `on term`.
    *
    * A STATEMENT, not a value: termination produces nothing. It can fail (it may race a
    * passivation), so it joins the can-fail census alongside send/tell/call/yield/put/get.
    *
    * @param loc
    *   The location of the `terminate` statement in the source
    * @param processor
    *   The processor whose instance is being terminated
    * @param args
    *   The arguments supplied to the target's `on term` parameters. Unlike [[Initiate]]'s, the
    *   parentheses are NOT optional: `on term`'s leading `Id(...)` parameter is mandatory, so a
    *   no-argument `terminate` could never pass validation and the bare spelling was dead syntax
    *   (removed in the final review of the instance-identity branch).
    */
  @JSExportTopLevel("TerminateStatement")
  case class TerminateStatement(
    loc: At,
    processor: ProcessorRef[Processor[?]],
    args: Seq[ConstructorArg]
  ) extends Statement {
    override def kind: String = "Terminate Statement"
    override def canFail: Boolean = true
    // Byte-identical in shape to `Initiate.format`, and deliberately so: the bare `terminate P`
    // form returned on 2026-08-14 when `on term`'s leading-Id requirement was dropped (`self.id`
    // supplies the instance, so the parameter was redundant). The requirement was the ONLY reason
    // a no-argument `terminate` was unreachable, and with it gone the parens-optional asymmetry
    // with `initiate` had nothing left holding it up.
    def format: String =
      val argList = if args.isEmpty then "" else args.map(_.format).mkString("(", ", ", ")")
      s"terminate ${processor.format}$argList"
  }

  ///////////////////////////////////////////////////////////////////////////////////////// ADAPTOR

  /** A trait that is the base trait of Adaptor directions */
  sealed trait AdaptorDirection extends RiddlValue:
    def loc: At
  end AdaptorDirection

  /** Represents an [[AdaptorDirection]] that is inbound (towards the bounded referent the
    * [[Adaptor]] was defined in)
    *
    * @param loc
    *   Location in the source of the adaptor direction
    */
  @JSExportTopLevel("InboundAdaptor")
  case class InboundAdaptor(loc: At) extends AdaptorDirection:
    def format: String = "from"
  end InboundAdaptor

  /** Represents an [[AdaptorDirection]] that is outbouand (towards a bounded referent that is not
    * the one that defined the [[Adaptor]]
    */
  @JSExportTopLevel("OutboundAdaptor")
  case class OutboundAdaptor(loc: At) extends AdaptorDirection:
    def format: String = "to"
  end OutboundAdaptor

  /** Definition of an Adaptor. Adaptors are defined in Contexts to convert messages from another
    * bounded referent. Adaptors translate incoming messages into corresponding messages using the
    * ubiquitous language of the defining bounded referent. There should be one Adapter for each
    * external Context
    *
    * @param loc
    *   Location in the parsing input
    * @param id
    *   Name of the adaptor
    * @param direction
    *   An indication of whether this is an inbound or outbound adaptor.
    * @param referent
    *   A reference to the bounded referent from which messages are adapted
    * @param contents
    *   The definitional contents of this Adaptor
    * @param metadata
    *   The descriptive values for this Adaptor
    */
  @JSExportTopLevel("Adaptor")
  case class Adaptor(
    loc: At,
    id: Identifier,
    direction: AdaptorDirection,
    referent: ContextRef,
    contents: Contents[AdaptorContents] = Contents.empty[AdaptorContents](),
    ascribedShape: Option[StreamletShape] = None,
    metadata: Contents[MetaData] = Contents.empty[MetaData]()
  ) extends Processor[AdaptorContents]:
    def format: String = Keyword.adaptor + " " + id.format
  end Adaptor

  @JSExportTopLevel("AdaptorRef")
  case class AdaptorRef(loc: At, pathId: PathIdentifier) extends ProcessorRef[Adaptor] {
    override def format: String = Keyword.adaptor + " " + pathId.format
  }

  //////////////////////////////////////////////////////////////////////////////////////// FUNCTION

  /** The `requires` clause of a [[Function]] or [[Saga]].
    *
    * A9: it names a [[TypeRef]] (preferred) or, deprecated, an inline [[Aggregation]].
    *
    * This is CONTENT, not a field. It began as `Function.input` / `Saga.input`, which meant the
    * grammar had to spell the body as `[func_input] [func_output] {definitions}` — a fixed prefix.
    * Once a comment became a legal definition (867ab0333), a comment written ABOVE `requires`
    * consumed the definitions slot and `requires` was then rejected, so the working rule became
    * "`requires`/`returns` must be the very first tokens of the body" — exactly where a reader most
    * wants a comment explaining them. Making the clause ordinary content dissolves the prefix and
    * lets comments sit anywhere.
    *
    * [[Function.input]] and [[Saga.input]] remain as derived accessors, so every existing reader
    * (ValidationPass, BASTWriter, PrettifyVisitor) is unaffected.
    */
  case class Requires(loc: At, what: TypeRef | Aggregation) extends RiddlValue:
    def format: String = "requires " + (what match
      case tr: TypeRef      => tr.format
      case agg: Aggregation => agg.format
    )
  end Requires

  /** The `returns` clause of a [[Function]] or [[Saga]]. See [[Requires]] for why it is content
    * rather than a field.
    */
  case class Returns(loc: At, what: TypeRef | Aggregation) extends RiddlValue:
    def format: String = "returns " + (what match
      case tr: TypeRef      => tr.format
      case agg: Aggregation => agg.format
    )
  end Returns

  /** A function definition which can be part of a bounded referent or an entity.
    *
    * @param loc
    *   The location of the function definition
    * @param id
    *   The identifier that names the function
    * @param contents
    *   The set of types, functions, statements, authors, includes and terms that define this
    *   Function, including its [[Requires]] and [[Returns]] clauses
    * @param metadata
    *   The set of descriptive values for this function
    */
  @JSExportTopLevel("Function")
  case class Function(
    loc: At,
    id: Identifier,
    contents: Contents[FunctionContents] = Contents.empty[FunctionContents](),
    metadata: Contents[MetaData] = Contents.empty[MetaData]()
  ) extends VitalDefinition[FunctionContents]
      with WithTypes[FunctionContents]
      with WithFunctions[FunctionContents]
      with WithStatements[FunctionContents] {
    override def format: String = Keyword.function + " " + id.format
    final override def kind: String = "Function"

    /** A9: the `requires` clause, now stored as [[Requires]] content. Derived so that every reader
      * predating the move keeps working unchanged — the type is exactly what the constructor field
      * used to hold.
      *
      * `filterThroughWrappers`, like the other content accessors: a `requires` written in an
      * included fragment is still this function's `requires`. The parser guarantees at most one
      * (`checkRequiresReturnsCardinality`), so `headOption` is the whole answer, not a truncation.
      */
    def input: Option[TypeRef | Aggregation] =
      contents.filterThroughWrappers[Requires].headOption.map(_.what)

    /** A9: the `returns` clause, now stored as [[Returns]] content. */
    def output: Option[TypeRef | Aggregation] =
      contents.filterThroughWrappers[Returns].headOption.map(_.what)

    override def isEmpty: Boolean = statements.isEmpty && input.isEmpty && output.isEmpty
  }

  /** A reference to a function.
    *
    * @param loc
    *   The location of the function reference.
    * @param pathId
    *   The path identifier of the referenced function.
    */
  @JSExportTopLevel("FunctionRef")
  case class FunctionRef(loc: At, pathId: PathIdentifier) extends Reference[Function] {
    override def format: String = Keyword.function + " " + pathId.format
  }

  /** An invariant expression that can be used in the definition of an entity. Invariants provide
    * conditional expressions that must be true at all times in the lifecycle of an entity.
    *
    * @param loc
    *   The location of the invariant definition
    * @param id
    *   The name of the invariant
    * @param condition
    *   The condition that ought to be true
    * @param requires
    *   The data the invariant reads, and thereby where it applies. See [[Invariant]].
    * @param metadata
    *   The list of meta data for the invariant
    */
  @JSExportTopLevel("Invariant")
  case class Invariant(
    loc: At,
    id: Identifier,
    // A28: a condition is an opaque pseudo-code LiteralString, a structured BooleanExpression, or
    // (2026-08-04) a block of pure statements ending in a boolean.
    condition: Option[LiteralString | BooleanExpression | InvariantBlock] =
      Option.empty[LiteralString | BooleanExpression | InvariantBlock],
    /** What this invariant reads, which is also WHERE it applies.
      *
      * `None` -- scope comes from the declaration site: declared in an Entity it applies to every
      * clause of that entity and may read only fields present in EVERY state record (the
      * intersection rule); declared inside a State it applies to that state's handlers.
      *
      * `Some(StateRef)` -- declared at entity level but scoped to that one state; applies while
      * the entity is in it.
      *
      * `Some(TypeRef)` -- reads only the value handed to it, so it is NEVER implicit; it must be
      * invoked by `require invariant X with <expr>`, where the CLAUSE does any gathering. This is
      * the only form available to a stateless processor, and it is why an invariant needs no
      * `send`: an invariant never acquires, it only receives.
      */
    requires: Option[StateRef | TypeRef] = Option.empty[StateRef | TypeRef],
    metadata: Contents[MetaData] = Contents.empty[MetaData]()
  ) extends Leaf {
    override def isEmpty: Boolean = condition.isEmpty

    /** Whether this invariant is applied implicitly at the head of every clause in its scope, as
      * opposed to only where a `require invariant` names it. A declared `TypeRef` is the one form
      * that cannot be implicit -- nothing in ambient scope can supply its value.
      */
    def isImplicit: Boolean = requires match
      case Some(_: TypeRef) => false
      case _                => true

    def format: String = Keyword.invariant + " " + id.format +
      requires.map(r => " requires " + r.format).getOrElse("") +
      condition.map(" is " + _.format).getOrElse("")
  }

  /** A block-form invariant condition: pure statements followed by the boolean that IS the
    * predicate.
    *
    * The statements are exactly those a pure [[Function]] may contain (A26) -- no state writes, no
    * `send`/`tell`, no `morph`/`become`/`yield`/`reply` -- so with the no-loops rule the block is
    * structurally terminating. That matters more than it looks: an invariant runs as a precondition
    * before every effect, so it must be synchronous, total, deterministic and terminating. A `send`
    * would satisfy "does not mutate" while breaking all four.
    *
    * The value is what a string condition cannot express: `let` bindings and calls to pure
    * functions, checkable rather than left to an AI to interpret the same way twice.
    */
  case class InvariantBlock(
    loc: At,
    statements: Contents[Statements] = Contents.empty[Statements](),
    predicate: BooleanExpression
  ) extends RiddlValue:
    def format: String =
      "{ " + (statements.toSeq.map(_.format) :+ predicate.format).mkString(" ") + " }"
  end InvariantBlock

  /////////////////////////////////////////////////////////////////////////////////////// ON CLAUSE

  /** A sealed trait for the kinds of OnClause that can occur within a Handler definition.
    */
  sealed trait OnClause extends Branch[Statements] with WithStatements[Statements]

  /** Common supertype of the two on-clauses that carry a message reference: [[OnMessageClause]]
    * (command/query/result/record) and [[OnEventClause]] (event). Event handling was split into its
    * own node so the parser can forbid `require`/`error` in event bodies, but for resolution,
    * message-flow, dependency and diagram purposes both are "a clause that reacts to a message" —
    * those passes match on this trait to treat them uniformly by `msg`/`from`.
    */
  sealed trait OnMessageLikeClause extends OnClause {
    def msg: MessageRef

    /** A55: the optional local name bound to the handled message — `on foo: command Foo { … }`. The
      * `:` is ordinary type ascription (as in `let x: T = …` or a field declaration), so the
      * binding reads "foo has type command Foo". When present, the name denotes the whole message
      * within the clause body (`foo`) and prefixes field access (`foo.someField`).
      */
    def binding: Option[Identifier]
    def from: Option[(Option[Identifier], Reference[Definition])]
  }

  /** Defines the actions to be taken when a message does not match any of the OnMessageClauses.
    * OnOtherClause corresponds to the "other" case of an [[Handler]].
    *
    * A57: `on other as x [: <envelope>]` optionally binds the residual message's ENVELOPE. Unlike
    * an [[OnMessageLikeClause]] binding, `x` does not denote a message — the clause names none — it
    * denotes the metadata the message travelled in, whose type comes from `option
    * message_envelope` in scope. The ascription is an OPTIONAL restatement of that option, checked
    * against it, so the type can be pulled to the use site where a reader benefits without being
    * repeated everywhere. Validation owns both rules: an ascription that contradicts the option,
    * and either form used with no envelope in scope.
    *
    * `binding` and `envelopeType` carry NO defaults and precede `contents`, because
    * `@JSExportTopLevel` requires a case class's defaulted parameters to be TRAILING.
    *
    * @param loc
    *   THe location of the "on other" clause
    * @param binding
    *   A57: the optional local name bound to the message's envelope
    * @param envelopeType
    *   A57: the optional explicit envelope type; must agree with `option message_envelope`
    * @param contents
    *   A set of examples that define the behavior when a message doesn't match
    */
  @JSExportTopLevel("OnOtherClause")
  case class OnOtherClause(
    loc: At,
    binding: Option[Identifier],
    envelopeType: Option[TypeRef],
    contents: Contents[Statements] = Contents.empty[Statements](),
    metadata: Contents[MetaData] = Contents.empty[MetaData]()
  ) extends OnClause {
    def id: Identifier = Identifier(loc, s"other")

    override def kind: String = "On Other"

    /** A57: the binding is rendered by `Declaration.ascription`, which `openDef` and
      * `Definition.format` both read — one implementation, so the two surfaces cannot drift.
      */
    override def format: String = Declaration.ascription(this)
  }

  /** Defines the actions to be taken when the component this OnClause occurs in is initialized.
    * `on init` is the CONSTRUCTOR: there is no instance yet, so unlike [[OnTerminationClause]] its
    * parameters are ordinary -- none of them need be an [[UniqueId]] of the enclosing processor,
    * because the identity is minted BY initiating, not supplied to it.
    *
    * @param loc
    *   THe location of the "on other" clause
    * @param parameters
    *   Declared BEFORE `contents`/`metadata` and WITHOUT a default: `@JSExportTopLevel` requires
    *   defaulted parameters to be trailing, and those two are defaulted. Same rule as A55's
    *   `binding` and A57's `envelopeType`.
    * @param contents
    *   A set of statements that define the behavior when a message doesn't match
    */
  @JSExportTopLevel("OnInitializationClause")
  case class OnInitializationClause(
    loc: At,
    parameters: Seq[MethodArgument],
    contents: Contents[Statements] = Contents.empty[Statements](),
    metadata: Contents[MetaData] = Contents.empty[MetaData]()
  ) extends OnClause {
    def id: Identifier = Identifier(loc, s"init")

    override def kind: String = "On Init"

    override def format: String = ""
  }

  /** Defines the actions to be taken when a particular message is received by an entity.
    * [[OnMessageClause]]s are used in the definition of a [[Handler]] with one for each kind of
    * message that handler deals with.
    *
    * @param loc
    *   The location of the "on" clause
    * @param msg
    *   A reference to the message type that is handled
    * @param from
    *   Optional message generating
    * @param binding
    *   A55: the optional local name bound to the handled message (`on foo: command Foo`). Declared
    *   before `contents`/`metadata` and WITHOUT a default because `@JSExportTopLevel` requires all
    *   defaulted parameters to be trailing.
    * @param contents
    *   A set of statements that define the behavior when the [[msg]] is received.
    */
  @JSExportTopLevel("OnMessageClause")
  case class OnMessageClause(
    loc: At,
    msg: MessageRef,
    from: Option[(Option[Identifier], Reference[Definition])],
    binding: Option[Identifier],
    contents: Contents[Statements] = Contents.empty[Statements](),
    metadata: Contents[MetaData] = Contents.empty[MetaData]()
  ) extends OnMessageLikeClause {
    def id: Identifier = Identifier(msg.loc, msg.format)
    def format: String = ""
  }

  /** Defines the actions to be taken when the component this OnClause occurs in is terminated.
    * `on term` is the DESTRUCTOR, and is invoked from OUTSIDE the instance, so the caller must say
    * which one: its first parameter is required (validation-time, not grammar) to be an
    * [[UniqueId]] of the enclosing processor. Unlike [[OnInitializationClause]] there is no
    * question of what the identity IS -- it already exists, and termination needs to be told it.
    *
    * @param loc
    *   THe location of the "on other" clause
    * @param parameters
    *   Declared BEFORE `contents`/`metadata` and WITHOUT a default: `@JSExportTopLevel` requires
    *   defaulted parameters to be trailing, and those two are defaulted. Same rule as A55's
    *   `binding` and A57's `envelopeType`.
    * @param contents
    *   A set of statements that define the behavior when a message doesn't match
    */
  @JSExportTopLevel("OnTerminationClause")
  case class OnTerminationClause(
    loc: At,
    parameters: Seq[MethodArgument],
    contents: Contents[Statements] = Contents.empty[Statements](),
    metadata: Contents[MetaData] = Contents.empty[MetaData]()
  ) extends OnClause {
    def id: Identifier = Identifier(loc, s"term")

    override def kind: String = "On Term"

    override def format: String = ""
  }

  /** Defines the actions taken when a specific event is received. Distinct from [[OnMessageClause]]
    * (which handles command/query/result) because events must ALWAYS be accepted: an event clause
    * may not use `require` or `error` statements — the only ways to circumvent normal flow control
    * — and that restriction is enforced at parse time. Used in every kind of handler.
    *
    * @param msg
    *   A reference to the event type that is handled
    * @param binding
    *   A55: the optional local name bound to the handled event (`on evt: event Started`). Declared
    *   before `contents`/`metadata` and WITHOUT a default because `@JSExportTopLevel` requires all
    *   defaulted parameters to be trailing.
    */
  @JSExportTopLevel("OnEventClause")
  case class OnEventClause(
    loc: At,
    msg: MessageRef,
    from: Option[(Option[Identifier], Reference[Definition])],
    binding: Option[Identifier],
    contents: Contents[Statements] = Contents.empty[Statements](),
    metadata: Contents[MetaData] = Contents.empty[MetaData]()
  ) extends OnMessageLikeClause {
    def id: Identifier = Identifier(msg.loc, msg.format)

    override def kind: String = "On Event"

    def format: String = ""
  }

  /** Defines the actions taken each time an entity is activated (rehydrated into memory). Distinct
    * from [[OnInitializationClause]], which happens once ever at creation. Entity handlers only.
    * Activation must be transparent to the rest of the system, so outbound messaging
    * (`send`/`tell`/`reply`/`morph`/`become`) is not permitted (enforced at parse time).
    */
  @JSExportTopLevel("OnActivationClause")
  case class OnActivationClause(
    loc: At,
    contents: Contents[Statements] = Contents.empty[Statements](),
    metadata: Contents[MetaData] = Contents.empty[MetaData]()
  ) extends OnClause {
    def id: Identifier = Identifier(loc, s"activate")

    override def kind: String = "On Activate"

    override def format: String = ""
  }

  /** Defines the actions taken each time an entity is passivated (evicted from memory). Distinct
    * from [[OnTerminationClause]], which happens once ever at destruction. Entity handlers only.
    * Same side-effect-free restriction as [[OnActivationClause]].
    */
  @JSExportTopLevel("OnPassivationClause")
  case class OnPassivationClause(
    loc: At,
    contents: Contents[Statements] = Contents.empty[Statements](),
    metadata: Contents[MetaData] = Contents.empty[MetaData]()
  ) extends OnClause {
    def id: Identifier = Identifier(loc, s"passivate")

    override def kind: String = "On Passivate"

    override def format: String = ""
  }

  ///////////////////////////////////////////////////////////////////////////////////////// HANDLER

  /** A named handler of messages (commands, events, queries) that bundles together a set of
    * [[OnMessageClause]] definitions and by doing so defines the behavior of an entity. Note that
    * entities may define multiple handlers and switch between them to change how it responds to
    * messages over time or in response to changing conditions
    *
    * @param loc
    *   The location of the handler definition
    * @param id
    *   The name of the handler.
    * @param contents
    *   The set of [[OnMessageClause]] definitions and comments that define how the entity responds
    *   to received messages.
    */
  @JSExportTopLevel("Handler")
  case class Handler(
    loc: At,
    id: Identifier,
    contents: Contents[HandlerContents] = Contents.empty[HandlerContents](),
    metadata: Contents[MetaData] = Contents.empty[MetaData](),
    // Marks this as the initial/live handler (after a morph, the target state's first handler, or
    // the marked one). Set by the parser: explicit `initial` keyword, or defaulted onto the first
    // handler of a state (or of the entity when it has a single state) when none is marked.
    isInitial: Boolean = false
  ) extends Branch[HandlerContents] {
    override def isEmpty: Boolean = clauses.isEmpty

    def clauses: Seq[OnClause] = contents.filterThroughWrappers[OnClause]

    def format: String = s"${Declaration.prefix(this)}${Keyword.handler} ${id.format}"
  }

  /** A reference to a Handler
    *
    * @param loc
    *   The location of the handler reference
    * @param pathId
    *   The path identifier of the referenced handler
    */
  @JSExportTopLevel("HandlerRef")
  case class HandlerRef(loc: At, pathId: PathIdentifier) extends Reference[Handler] {
    def format: String = Keyword.handler + " " + pathId.format
  }

  /////////////////////////////////////////////////////////////////////////////////////////// STATE

  /** Represents a state of an entity. A State defines the shape of the entity's state when it is
    * active. The MorphAction can cause the active state of an entity to change. Consequently the
    * state of an entity can change its value (mutable) and they shape of that value.
    *
    * @param loc
    *   The location of the state definition
    * @param id
    *   The name of the state definition
    * @param typ
    *   A reference to a type definition that provides the range of values that the state may
    *   assume.
    * @param brief
    *   A brief description (one sentence) for use in documentation
    * @param description
    *   An optional description of the state.
    */
  @JSExportTopLevel("State")
  case class State(
    loc: At,
    id: Identifier,
    // A9b: a state is record-shaped data, so its type is a RecordRef (`state S of record R`).
    typ: RecordRef,
    contents: Contents[StateContents] = Contents.empty[StateContents](),
    metadata: Contents[MetaData] = Contents.empty[MetaData](),
    // Marks this as the entity's starting state. Set by the parser: from an explicit `initial`
    // keyword, or defaulted onto the first state when none is marked (refactor-safe under reorder).
    isInitial: Boolean = false
  ) extends Branch[StateContents]
      with WithHandlers[StateContents]
      with WithInvariants[StateContents]:
    def format: String = Declaration.prefix(this) + Keyword.state + " " + id.format
  end State

  /** A reference to an entity's state definition
    *
    * @param loc
    *   The location of the state reference
    * @param pathId
    *   The path identifier of the referenced state definition
    */
  @JSExportTopLevel("StateRef")
  case class StateRef(loc: At, pathId: PathIdentifier) extends Reference[State]:
    def format: String = Keyword.state + " " + pathId.format
  end StateRef

  ////////////////////////////////////////////////////////////////////////////////////////// ENTITY

  /** A semantic declaration written as a keyword BEFORE `entity`.
    *
    * These were options (`with { option event-sourced }`) until 2.0. They are not metadata: they
    * decide what the model MEANS -- whether state is rebuilt from a log, whether it survives a
    * restart -- and the event-sourcing rules make some of them decide whether the model is even
    * legal. A hard error keyed off something the Computational Model calls an instruction "to be
    * honored if possible" is a category error, so they were promoted into the grammar, where they
    * are read at the declaration site instead of in a trailing block.
    *
    * Three INDEPENDENT groups; within a group the keywords are mutually exclusive (validated, not
    * encoded in the type, so that a model with two can be reported rather than fail to parse).
    */
  enum EntityIntention:
    case Aggregate, Consistent, Available, EventSourced, Persistent, Transient

    def keyword: String = this match
      case Aggregate    => "aggregate"
      case Consistent   => "consistent"
      case Available    => "available"
      case EventSourced => "event-sourced"
      case Persistent   => "persistent"
      case Transient    => "transient"

    /** Keywords sharing a group are mutually exclusive. `event-sourced` is in the persistence group
      * because it IMPLIES persistent -- saying both is redundant, not additive.
      */
    def group: String = this match
      case Aggregate                             => "role"
      case Consistent | Available                => "consistency"
      case EventSourced | Persistent | Transient => "persistence"
  end EntityIntention

  object EntityIntention:

    /** The canonical order intentions are emitted in: role, then consistency, then persistence. Any
      * order is accepted on input; PrettifyPass emits this one.
      */
    val canonicalOrder: Seq[EntityIntention] =
      Seq(Aggregate, Consistent, Available, EventSourced, Persistent, Transient)

    /** All keywords, longest first, so a prefix parser never matches a shorter word that is the
      * start of a longer one.
      */
    val keywords: Seq[String] = canonicalOrder.map(_.keyword).sortBy(-_.length)

    def fromKeyword(kw: String): Option[EntityIntention] =
      canonicalOrder.find(_.keyword == kw)

    /** Sort into [[canonicalOrder]] and drop duplicates. The parser stores intentions this way so
      * that the order they were written in can never make two otherwise-identical entities compare
      * unequal -- `Definition.equals` compares this field.
      */
    def canonical(intentions: Seq[EntityIntention]): Seq[EntityIntention] =
      canonicalOrder.filter(intentions.contains)
  end EntityIntention

  /** How a definition DECLARES itself -- the parts of the declaration that carry meaning rather
    * than identity.
    *
    * RIDDL 2.0 put semantics into prefixes and suffixes: an entity's intentions, a context's
    * intention, `initial` on a handler or state, `yields` on a message type, `as <shape>` on a
    * processor. These are not decoration -- `event-sourced` is the difference between a model that
    * must satisfy the event-sourcing rules and one that need not.
    *
    * This is the ONE implementation. `format` renders it for consumers and the prettifier's
    * `openDef`/`openState` emit it for round-tripping, so the two cannot drift apart. They did:
    * `format` used to drop every prefix and render a Streamlet with the shape keyword 2.0
    * deprecated, while the prettifier normalized that same definition to `processor X as <shape>`.
    */
  object Declaration {

    /** Keywords written BEFORE the definition keyword. */
    def prefix(definition: Definition): String = definition match
      case h: Handler if h.isInitial => s"${Keyword.initial} "
      case s: State if s.isInitial   => s"${Keyword.initial} "
      case c: Context                => c.intention.map(i => s"${i.keyword} ").getOrElse("")
      // Canonical order regardless of how they were written -- the parser sorts them at parse
      // time, so the written order is gone before the AST exists. A deprecated
      // `option event-sourced` was consumed into an intention, so it renders as the keyword.
      case e: Entity =>
        EntityIntention.canonical(e.intentions).map(i => s"${i.keyword} ").mkString
      case c: Connector =>
        ConnectorIntention.canonical(c.intentions).map(i => s"${i.keyword} ").mkString
      case _ => ""
    end prefix

    /** What sits between the identifier and `is`. */
    def ascription(definition: Definition): String = definition match
      // Absent means the shape was DERIVED from arity rather than written, and nothing is
      // emitted -- so a derived-shape processor shows no shape at all, in both surfaces.
      case p: Processor[?] => p.ascribedShape.map(s => s" as ${s.keyword}").getOrElse("")
      case t: Type =>
        t.typEx match
          case a: AggregateUseCaseTypeExpression =>
            a.yields.map(y => s" ${a.responseKeyword} ${y.format}").getOrElse("")
          case _ => ""
      // A55: an on-clause's `from [<name>:] <origin>`.
      case omc: OnMessageLikeClause =>
        omc.from
          .map { case (optId, ref) =>
            s" ${Keyword.from} " + optId.map(id => s"${id.format}: ").getOrElse("") + ref.format
          }
          .getOrElse("")
      // A57: `on other as <name> [: <envelope>]`. It lives HERE, not in the clause's own `format`,
      // because this is the one implementation both surfaces read: `openDef` emits it for
      // round-tripping and `format` renders it for consumers. Putting it anywhere else is how the
      // two drift, and the prettifier silently dropped this binding until it moved here.
      case ooc: OnOtherClause =>
        ooc.binding
          .map { id =>
            s" as ${id.format}" + ooc.envelopeType.map(t => s": ${t.pathId.format}").getOrElse("")
          }
          .getOrElse("")
      // Task 3: `on init`/`on term` parameter lists. Rendered between the id and `is`, exactly
      // where the parser reads them (`on init(a: T, b: U) is { … }`), and shared with `openDef`
      // for the same reason every other ascription case is -- one implementation, so the parser
      // and the emitter cannot drift.
      case oic: OnInitializationClause =>
        if oic.parameters.isEmpty then ""
        else s"(${oic.parameters.map(_.format).mkString(", ")})"
      case otc: OnTerminationClause =>
        if otc.parameters.isEmpty then ""
        else s"(${otc.parameters.map(_.format).mkString(", ")})"
      case _ => ""
    end ascription

    /** The message-type keyword a Type declares itself with. */
    def typeKeyword(t: Type): String = t.typEx match
      case AggregateUseCaseTypeExpression(_, useCase, _, _) =>
        useCase match
          case AggregateUseCase.CommandCase => Keyword.command
          case AggregateUseCase.EventCase   => Keyword.event
          case AggregateUseCase.QueryCase   => Keyword.query
          case AggregateUseCase.ResultCase  => Keyword.result
          case AggregateUseCase.RecordCase  => Keyword.record
          case AggregateUseCase.TypeCase    => Keyword.type_
          case AggregateUseCase.GraphCase   => Keyword.graph
          case AggregateUseCase.TableCase   => Keyword.table
      case _ => Keyword.type_
    end typeKeyword
  }


  /** Definition of an Entity
    *
    * @param loc
    *   The location in the input
    * @param id
    *   The name of the entity
    * @param contents
    *   The definitional content of this entity: handlers, states, functions, invariants, etc.
    * @param intentions
    *   Semantic keywords written before `entity`, in [[EntityIntention.canonicalOrder]]
    */
  @JSExportTopLevel("Entity")
  case class Entity(
    loc: At,
    id: Identifier,
    contents: Contents[EntityContents] = Contents.empty[EntityContents](),
    ascribedShape: Option[StreamletShape] = None,
    intentions: Seq[EntityIntention] = Seq.empty,
    metadata: Contents[MetaData] = Contents.empty[MetaData]()
  ) extends Processor[EntityContents]
      with WithStates[EntityContents]:
    override def format: String =
      Declaration.prefix(this) + Keyword.entity + " " + id.format

    def hasIntention(intention: EntityIntention): Boolean = intentions.contains(intention)

    /** The entity's state is rebuilt by replaying its events, so the event-sourcing rules apply. */
    def isEventSourced: Boolean = hasIntention(EntityIntention.EventSourced)
  end Entity

  /** A reference to an entity
    *
    * @param loc
    *   The location of the entity reference
    * @param pathId
    *   The path identifier of the referenced entity.
    */
  @JSExportTopLevel("EntityRef")
  case class EntityRef(loc: At, pathId: PathIdentifier) extends ProcessorRef[Entity]:
    def format: String = Keyword.entity + " " + pathId.format
  end EntityRef

  ////////////////////////////////////////////////////////////////////////////////////// REPOSITORY

  enum RepositorySchemaKind:
    case Other, Flat, Relational, TimeSeries, Graphical, Hierarchical, Star, Document, Columnar,
      Vector

  /** The repository schema defined as an identifier of the schema, a general kind of intended
    * schema, and the representation of the schema as data node types (vertices, tables, vectors,
    * etc.), a list of named connections between pairs of the data nodes (foreign keys,
    * parent/child, arbitrary graph nodes, etc.), and indices on specific fields of the data nodes.
    * @param loc
    *   The location at which the schema occurs
    * @param id
    *   The name of this schema
    * @param schemaKind
    *   One of the RepositorySchemaKinds for a general sense of the repository intention
    * @param data
    *   A list of the named primary data nodes (tables, vectors, vertices)
    * @param links
    *   A list of named relations between primary data nodes
    * @param indices
    *   A list of fields in the ((data)) or ((links) that are considered indexed for faster
    *   retrieval
    */
  @JSExportTopLevel("Schema")
  case class Schema(
    loc: At,
    id: Identifier,
    schemaKind: RepositorySchemaKind = RepositorySchemaKind.Other,
    data: Map[Identifier, TypeRef] = Map.empty[Identifier, TypeRef],
    links: Map[Identifier, (FieldRef, FieldRef)] = Map.empty[Identifier, (FieldRef, FieldRef)],
    indices: Seq[FieldRef] = Seq.empty[FieldRef],
    metadata: Contents[MetaData] = Contents.empty[MetaData]()
  ) extends Leaf:
    def format: String = Keyword.schema + " " + id.format + s" is $schemaKind"
  end Schema

  /** A RIDDL repository is an abstraction for anything that can retain information(e.g. messages
    * for retrieval at a later time. This might be a relational database, NoSQL database, data lake,
    * API, or something not yet invented. There is no specific technology implied other than the
    * retention and retrieval of information. You should think of repositories more like a
    * message-oriented version of the Java Repository Pattern than any particular kind ofdatabase.
    *
    * @see
    *   https://java-design-patterns.com/patterns/repository/#explanation
    * @param loc
    *   Location in the source of the Repository
    * @param id
    *   The unique identifier for this Repository
    * @param contents
    *   The definitional content of this Repository: types, handlers, inlets, outlets, etc.
    */
  @JSExportTopLevel("Repository")
  case class Repository(
    loc: At,
    id: Identifier,
    contents: Contents[RepositoryContents] = Contents.empty[RepositoryContents](),
    ascribedShape: Option[StreamletShape] = None,
    metadata: Contents[MetaData] = Contents.empty[MetaData]()
  ) extends Processor[RepositoryContents]:
    def format: String = Keyword.repository + " " + id.format
  end Repository

  /** A reference to a repository definition
    *
    * @param loc
    *   The location of the state reference
    * @param pathId
    *   The path identifier of the referenced projector definition
    */
  @JSExportTopLevel("RepositoryRef")
  case class RepositoryRef(loc: At, pathId: PathIdentifier)
      extends Reference[Repository]
      with ProcessorRef[Projector] {
    override def format: String = s"repository ${pathId.format}"
  }

  /////////////////////////////////////////////////////////////////////////////////////// PROJECTOR

  /** Projectors get their name from Euclidean Geometry but are probably more analogous to a
    * relational database view. The concept is very simple in RIDDL: projectors gather data from
    * entities and other sources, transform that data into a specific record type, and support
    * querying that data arbitrarily.
    *
    * @see
    *   https://en.wikipedia.org/wiki/View_(SQL)).
    * @see
    *   https://en.wikipedia.org/wiki/Projector_(mathematics)
    * @param loc
    *   Location in the source of the Projector
    * @param id
    *   The unique identifier for this Projector
    * @param contents
    *   The content of this Projectors' definition
    */
  /** A70: a named, keyed accumulation of several events into one record the [[Repository]] stores.
    *
    * A projection frequently must join facts arriving from different entities at different times —
    * an order placed here, a payment taken there, a shipment somewhere else — and a [[Projector]]
    * otherwise has nowhere to hold the partial join while it waits.
    *
    * Written as:
    * {{{
    *   correlation Fulfillment by customerId, orderId yields record Sales.Fulfillment is {
    *     handler Collect is { on event Sales.OrderPlaced is { set field orderedAt to occurredAt } }
    *   } times out after "30 days" { tell command Ops.ReportStalled to entity Ops.Monitor }
    * }}}
    *
    * The semantics are specified in `RIDDL-Computational-Model.md` §6.2 and §6.5–§6.8, which is the
    * authority for any lowering decision; they are deliberately not restated here.
    *
    * @param keys
    *   The correlation key, ordered AS WRITTEN. It is deliberately NOT canonicalized: §6.5 makes
    *   identity the full tuple, and component order can matter to a generator's composite index or
    *   partition key. Contrast [[EntityIntention.canonical]], which canonicalizes for the opposite
    *   reason — there, write order must never make two identical entities compare unequal, whereas
    *   here a different order IS a different declaration.
    * @param yields
    *   The target record, named as declared. Partiality never appears in the model: that the
    *   accumulator holds a partly-filled version is a realization concern (§6.5).
    * @param timeout
    *   The mandatory bound on the accumulation, as a [[LiteralString]] so neither ISO-8601 nor
    *   Scala `Duration` syntax enters the grammar. It is duration-VALIDATED in `ValidationPass`;
    *   dropping that check would let `times out after "banana"` compile.
    * @param timeoutStatements
    *   What to do when the bound expires with the correlation incomplete. Required and non-empty.
    *   Unlike a fold, this block MAY have effects — it exists to have one (§6.7).
    */
  @JSExportTopLevel("Correlation")
  case class Correlation(
    loc: At,
    id: Identifier,
    // These three precede `contents` because they have no defaults and @JSExportTopLevel requires
    // defaulted parameters to be TRAILING -- the same rule A55's `binding` and A57's `envelopeType`
    // follow. Source order is different (`timeout` is written after the body) and that is fine;
    // declaration order here is a Scala.js constraint, not a statement about the syntax.
    keys: Seq[Identifier],
    // A COMMAND, not a record (Reid, 2026-08-12). A projector's only output is a change to a
    // repository, and a repository is changed by handling a command -- so the thing a correlation
    // yields must be nameable by a repository handler. A `record` never was: `messageRef` is the
    // four real messages only (A9b), so `yields record R` named a type no `on` clause could
    // mention, which is why the first design had to INFER acceptance from a command that "holds"
    // the record. Naming the command directly deletes the inference.
    yields: CommandRef,
    timeout: LiteralString,
    contents: Contents[CorrelationContents] = Contents.empty[CorrelationContents](),
    timeoutStatements: Contents[Statements] = Contents.empty[Statements](),
    metadata: Contents[MetaData] = Contents.empty[MetaData]()
  ) extends Branch[CorrelationContents]
      with WithHandlers[CorrelationContents]:
    def format: String = Keyword.correlation + " " + id.format
    override def isEmpty: Boolean = super.isEmpty && timeoutStatements.isEmpty
  end Correlation

  @JSExportTopLevel("Projector")
  case class Projector(
    loc: At,
    id: Identifier,
    contents: Contents[ProjectorContents] = Contents.empty[ProjectorContents](),
    ascribedShape: Option[StreamletShape] = None,
    metadata: Contents[MetaData] = Contents.empty[MetaData]()
  ) extends Processor[ProjectorContents]:
    def repositories: Seq[RepositoryRef] = contents.filterThroughWrappers[RepositoryRef]

    /** A70: the [[Correlation]]s this projector declares. Descends the provenance wrappers, as
      * every other `contents` accessor does — a client asking what a projector correlates has no
      * stake in whether the correlation was written inline or arrived by `include`.
      */
    def correlations: Seq[Correlation] = contents.filterThroughWrappers[Correlation]
    def format: String = Keyword.projector + " " + id.format
  end Projector

  /** A reference to an referent's projector definition
    *
    * @param loc
    *   The location of the state reference
    * @param pathId
    *   The path identifier of the referenced projector definition
    */
  @JSExportTopLevel("ProjectorRef")
  case class ProjectorRef(loc: At, pathId: PathIdentifier) extends ProcessorRef[Projector] {
    override def format: String = Keyword.projector + " " + pathId.format
  }

  ///////////////////////////////////////////////////////////////////////////////////////// CONTEXT

  /** A bounded referent definition. Bounded contexts provide a definitional boundary on the
    * language used to describe some aspect of a system. They imply a tightly integrated ecosystem
    * of one or more microservices that share a common purpose. Context can be used to house
    * entities, read side projectors, sagas, adaptations to other contexts, apis, and etc.
    *
    * @param loc
    *   The location of the bounded referent definition
    * @param id
    *   The name of the referent
    * @param contents
    *   The definitional content for this Context
    */
  /** The intent of a [[Context]] — what kind of subsystem it is. Drives code generation and
    * architectural validation (A37). Optional; a context without an intention is generic. Declared
    * as an optional keyword prefix before `context` (e.g. `application context Orders is { … }`).
    */
  enum Intention:
    case Application, External, Gateway, Service
    def keyword: String = this match
      case Application => "application"
      case External    => "external"
      case Gateway     => "gateway"
      case Service     => "service"
  end Intention

  object Intention:
    /** Parse an intention keyword; None if it is not one of the four. */
    def fromKeyword(kw: String): Option[Intention] = kw match
      case "application" => Some(Intention.Application)
      case "external"    => Some(Intention.External)
      case "gateway"     => Some(Intention.Gateway)
      case "service"     => Some(Intention.Service)
      case _             => None
  end Intention

  @JSExportTopLevel("Context")
  case class Context(
    loc: At,
    id: Identifier,
    contents: Contents[ContextContents] = Contents.empty[ContextContents](),
    ascribedShape: Option[StreamletShape] = None,
    intention: Option[Intention] = None,
    metadata: Contents[MetaData] = Contents.empty[MetaData]()
  ) extends Processor[ContextContents]
      with WithProjectors[ContextContents]
      with WithRepositories[ContextContents]
      with WithEntities[ContextContents]
      with WithStreamlets[ContextContents]
      with WithConnectors[ContextContents]
      with WithAdaptors[ContextContents]
      with WithSagas[ContextContents]
      with WithGroups[ContextContents] {
    def format: String = Declaration.prefix(this) + Keyword.context + " " + id.format

    /** True when this context is declared with the `application` intention.
      *
      * Reads [[intention]], which is `None` for a plain `context`. UI consumers were each writing
      * `intention.contains(Intention.Application)` by hand; the predicate is here so the
      * representation can change without breaking them.
      */
    def isApplication: Boolean = intention.contains(Intention.Application)

    /** True when declared with the `external` intention. See [[isApplication]]. */
    def isExternal: Boolean = intention.contains(Intention.External)

    /** True when declared with the `gateway` intention. See [[isApplication]]. */
    def isGateway: Boolean = intention.contains(Intention.Gateway)

    /** True when declared with the `service` intention. See [[isApplication]]. */
    def isService: Boolean = intention.contains(Intention.Service)
  }

  @JSExportTopLevel("Context$")
  object Context {
    lazy val empty: Context = Context(At.empty, Identifier.empty)
  }

  /** A reference to a bounded referent
    *
    * @param loc
    *   The location of the reference
    * @param pathId
    *   The path identifier for the referenced referent
    */
  @JSExportTopLevel("ContextRef")
  case class ContextRef(loc: At, pathId: PathIdentifier) extends ProcessorRef[Context] {
    override def format: String = s"context ${pathId.format}"
  }

  /////////////////////////////////////////////////////////////////////////////////////// STREAMLET

  /** A sealed trait for Inlets and Outlets */
  sealed trait Portlet extends Leaf

  /** A streamlet that supports input of data of a particular type.
    *
    * @param loc
    *   The location of the Inlet definition
    * @param id
    *   The name of the inlet
    * @param type_
    *   The type of the data that is received from the inlet
    * @param metadata
    *   The metadata for the Inlet
    */
  @JSExportTopLevel("Inlet")
  case class Inlet(
    loc: At,
    id: Identifier,
    type_ : TypeRef,
    metadata: Contents[MetaData] = Contents.empty[MetaData]()
  ) extends Portlet {
    def format: String = s"inlet ${id.format} is ${type_.format}"
  }

  /** A streamlet that supports output of data of a particular type.
    *
    * @param loc
    *   The location of the outlet definition
    * @param id
    *   The name of the outlet
    * @param type_
    *   The type expression for the kind of data put out
    * @param metadata
    *   The metadata for the Outlet
    */
  @JSExportTopLevel("Outlet")
  case class Outlet(
    loc: At,
    id: Identifier,
    type_ : TypeRef,
    metadata: Contents[MetaData] = Contents.empty[MetaData]()
  ) extends Portlet {
    def format: String = s"outlet ${id.format} is ${type_.format}"
  }

  /** A semantic declaration written as a keyword BEFORE `connector`.
    *
    * Same reasoning as [[EntityIntention]], and the same category error being fixed: `persistent`
    * was an option, but the Computational Model calls options advisory ("honored if possible")
    * and a DELIVERY GUARANTEE is not advisory. §25.7 is explicit -- delivery is at-least-once on
    * durable realizations, "weaker only as a knowing deployment downgrade, never a silent one" --
    * and a keyword at the declaration site is exactly what makes the downgrade un-silent.
    *
    * Two INDEPENDENT groups; within a group the keywords are mutually exclusive (validated, not
    * encoded in the type, so a model with two can be REPORTED rather than fail to parse).
    *
    * **`at-least-once` is the default and is still writable** (author, 2026-08-13): absence means
    * at-least-once, and stating it is redundant but legitimate where a reader benefits from seeing
    * the guarantee affirmatively. It draws no warning.
    *
    * **`exactly-once` joined the delivery group on 2026-08-14** (Reid, asked directly). It failed
    * no admission test -- a generator may not quietly decline to provide it any more than it may
    * decline at-most-once -- and leaving it as the one delivery OPTION while its two siblings
    * became intentions was its own inconsistency, which is what blocked deprecating the option
    * spellings at all. Whether a given transport can honour it is a lowering concern, exactly as
    * durability is; the model states the requirement and a generator that cannot meet it must say
    * so rather than silently weaken it.
    *
    * ORDERING is deliberately NOT here. §25.7 makes `unordered` "permission, not mandate" with a
    * best-effort obligation -- which is the definition of advisory, so it stays an option. The test
    * for admission to this enum is whether a generator may decline to honour it.
    */
  enum ConnectorIntention:
    case Persistent, AtLeastOnce, AtMostOnce, ExactlyOnce

    def keyword: String = this match
      case Persistent  => "persistent"
      case AtLeastOnce => "at-least-once"
      case AtMostOnce  => "at-most-once"
      case ExactlyOnce => "exactly-once"

    def group: String = this match
      case Persistent                              => "durability"
      case AtLeastOnce | AtMostOnce | ExactlyOnce  => "delivery"
  end ConnectorIntention

  object ConnectorIntention:

    /** The canonical order intentions are emitted in: durability, then delivery. Any order is
      * accepted on input; PrettifyPass emits this one.
      */
    val canonicalOrder: Seq[ConnectorIntention] =
      Seq(Persistent, AtLeastOnce, AtMostOnce, ExactlyOnce)

    /** All keywords, longest first, so a prefix parser never matches a shorter word that is the
      * start of a longer one.
      */
    val keywords: Seq[String] = canonicalOrder.map(_.keyword).sortBy(-_.length)

    def fromKeyword(kw: String): Option[ConnectorIntention] =
      canonicalOrder.find(_.keyword == kw)

    /** Sort into [[canonicalOrder]] and drop duplicates. The parser stores intentions this way so
      * write order can never make two otherwise-identical connectors compare unequal --
      * `Definition.equals` compares this field.
      */
    def canonical(intentions: Seq[ConnectorIntention]): Seq[ConnectorIntention] =
      canonicalOrder.filter(intentions.contains)
  end ConnectorIntention

  /** A connector between an [[com.ossuminc.riddl.language.AST.Outlet]] and an
    * [[com.ossuminc.riddl.language.AST.Inlet]] that flows a particular
    * [[com.ossuminc.riddl.language.AST.Type]].
    * @param loc
    *   The location at which the connector is defined
    * @param id
    *   The unique identifier of the connector
    * @param from
    *   The origin Outlet of the connector
    * @param to
    *   The destination Inlet of the connector
    * @param metadata
    *   The meta data for this connector
    */
  @JSExportTopLevel("Connector")


  case class Connector(
    loc: At,
    id: Identifier,
    from: OutletRef,
    to: InletRef,
    // DEFAULTED, like Entity.intentions. The compatibility policy requires a new parameter to
    // have one, and @JSExportTopLevel is satisfied because every param after it is defaulted too --
    // the rule is that defaulted params be TRAILING, not that there be none.
    intentions: Seq[ConnectorIntention] = Seq.empty,
    metadata: Contents[MetaData] = Contents.empty[MetaData]()
  ) extends Leaf {
    override def format: String = Keyword.connector + " " + id.format

    override def isEmpty: Boolean = super.isEmpty && from.isEmpty && to.isEmpty

    /** Durability, from the intention OR the deprecated `option persistent`. Both spellings must be
      * asked for together during the deprecation -- asking one is the bug that cost 1120 false
      * warnings on `external` (2026-08-12).
      */
    def isPersistent: Boolean =
      intentions.contains(ConnectorIntention.Persistent) || hasOption("persistent")
  }

  sealed trait StreamletShape extends RiddlValue {
    def keyword: String
  }

  @JSExportTopLevel("Void")
  case class Void(loc: At) extends StreamletShape {
    def format: String = "void"

    def keyword: String = "void"
  }

  @JSExportTopLevel("Source")
  case class Source(loc: At) extends StreamletShape {
    def format: String = "source"

    def keyword: String = "source"
  }

  @JSExportTopLevel("Sink")
  case class Sink(loc: At) extends StreamletShape {
    def format: String = "sink"

    def keyword: String = "sink"
  }

  @JSExportTopLevel("Flow")
  case class Flow(loc: At) extends StreamletShape {
    def format: String = "flow"

    def keyword: String = "flow"
  }

  @JSExportTopLevel("Merge")
  case class Merge(loc: At) extends StreamletShape {
    def format: String = "merge"

    def keyword: String = "merge"
  }

  @JSExportTopLevel("Split")
  case class Split(loc: At) extends StreamletShape {
    def format: String = "split"

    def keyword: String = "split"
  }

  @JSExportTopLevel("Router")
  case class Router(loc: At) extends StreamletShape {
    def format: String = "router"

    def keyword: String = "router"
  }

  object StreamletShape {

    /** Canonicalize a shape keyword (including synonyms) into a [[StreamletShape]].
      * @param kw
      *   The keyword as written by the author (e.g. "flow", "cascade", "fanout").
      * @param loc
      *   The source location to attach to the resulting shape.
      * @return
      *   Some(shape) for a recognized keyword or synonym, None otherwise.
      */
    def fromKeyword(kw: String, loc: At): Option[StreamletShape] = kw match
      case "source"                         => Some(Source(loc))
      case "sink"                           => Some(Sink(loc))
      case "flow" | "cascade"               => Some(Flow(loc))
      case "merge" | "fanin"                => Some(Merge(loc))
      case "split" | "broadcast" | "fanout" => Some(Split(loc))
      case "router"                         => Some(Router(loc))
      case "void"                           => Some(Void(loc))
      case _                                => None
  }

  /** Definition of a Streamlet. A computing element for processing data from [[Inlet]]s to
    * [[Outlet]]s. A processor's processing is specified by free text statements in [[Handler]]s.
    * Streamlets come in various shapes: Source, Sink, Flow, Merge, Split, and Router depending on
    * how many inlets and outlets they have
    *
    * @param loc
    *   The location of the Processor definition
    * @param id
    *   The name of the processor
    * @param ascribedShape
    *   The shape explicitly ascribed by the author, if any; otherwise the shape is derived from
    *   arity via `effectiveShape`.
    * @param contents
    *   The definitional content for this Context
    */
  @JSExportTopLevel("Streamlet")
  case class Streamlet(
    loc: At,
    id: Identifier,
    ascribedShape: Option[StreamletShape] = None,
    contents: Contents[StreamletContents] = Contents.empty[StreamletContents](),
    metadata: Contents[MetaData] = Contents.empty[MetaData]()
  ) extends Processor[StreamletContents] {
    // WithInlets/WithOutlets are now inherited from Processor.
    final override def kind: String = effectiveShape.getClass.getSimpleName
    // The canonical `processor` keyword, matching the prettifier. `effectiveShape.keyword`
    // emitted source/sink/flow -- the spellings 2.0 deprecated -- and showed a shape that may
    // have been DERIVED from arity rather than declared.
    def format: String =
      Keyword.processor + " " + id.format + Declaration.ascription(this)
  }

  /** A reference to an referent's projector definition
    *
    * @param loc
    *   The location of the state reference
    * @param pathId
    *   The path identifier of the referenced projector definition
    */
  @JSExportTopLevel("StreamletRef")
  case class StreamletRef(loc: At, keyword: String, pathId: PathIdentifier)
      extends ProcessorRef[Streamlet] {
    override def format: String = s"$keyword ${pathId.format}"
  }

  /** Sealed base trait of references to [[Inlet]]s or [[Outlet]]s
    *
    * @tparam T
    *   The type of definition to which the references refers.
    */
  sealed trait PortletRef[+T <: Portlet] extends Reference[T]

  /** A reference to an [[Inlet]]
    *
    * @param loc
    *   The location of the inlet reference
    * @param pathId
    *   The path identifier of the referenced [[Inlet]]
    */
  @JSExportTopLevel("InletRef")
  case class InletRef(loc: At, pathId: PathIdentifier) extends PortletRef[Inlet] {
    override def format: String = s"inlet ${pathId.format}"
  }
  @JSExportTopLevel("InletRef$")
  object InletRef { def empty: InletRef = InletRef(At.empty, PathIdentifier.empty) }

  /** A reference to an [[Outlet]]
    *
    * @param loc
    *   The location of the outlet reference
    * @param pathId
    *   The path identifier of the referenced [[Outlet]]
    */
  @JSExportTopLevel("OutletRef")
  case class OutletRef(loc: At, pathId: PathIdentifier) extends PortletRef[Outlet] {
    override def format: String = s"outlet ${pathId.format}"
  }
  @JSExportTopLevel("OutletRef$")
  object OutletRef { def empty: OutletRef = OutletRef(At.empty, PathIdentifier.empty) }

  ///////////////////////////////////////////////////////////////////////////////////////////// SAGA

  /** The definition of one step in a saga with its undo step and example.
    *
    * @param loc
    *   The location of the saga action definition
    * @param id
    *   The name of the SagaAction
    * @param doStatements
    *   The command to be done.
    * @param undoStatements
    *   The command that undoes [[doStatements]]
    * @param metadata
    *   The metadata for the SagaStep
    */
  @JSExportTopLevel("SagaStep")
  case class SagaStep(
    loc: At,
    id: Identifier,
    doStatements: Contents[Statements] = Contents.empty[Statements](),
    undoStatements: Contents[Statements] = Contents.empty[Statements](),
    metadata: Contents[MetaData] = Contents.empty[MetaData]()
  ) extends Leaf {
    def format: String = s"step ${id.format}"
  }

  /** The definition of a Saga based on inputs, outputs, and the set of [[SagaStep]]s involved in
    * the saga. Sagas define a computing action based on a variety of related commands that must all
    * succeed atomically or have their effects undone.
    *
    * @param loc
    *   The location of the Saga definition
    * @param id
    *   The name of the saga
    * @param contents
    *   The definitional content for this Saga, including its [[Requires]] and [[Returns]] clauses
    * @param metadata
    *   The metadata for the Saga
    */
  @JSExportTopLevel("Saga")
  case class Saga(
    loc: At,
    id: Identifier,
    contents: Contents[SagaContents] = Contents.empty[SagaContents](),
    metadata: Contents[MetaData] = Contents.empty[MetaData]()
  ) extends VitalDefinition[SagaContents]
      with WithSagaSteps[SagaContents] {
    override def format: String = Keyword.saga + " " + id.format

    /** A9: the `requires` clause, now stored as [[Requires]] content. Derived so that every reader
      * predating the move keeps working unchanged. See [[Function.input]] for why this descends
      * through the provenance wrappers — a Saga body may contain an `include`, so this one can
      * actually differ from the literal filter.
      */
    def input: Option[TypeRef | Aggregation] =
      contents.filterThroughWrappers[Requires].headOption.map(_.what)

    /** A9: the `returns` clause, now stored as [[Returns]] content. */
    def output: Option[TypeRef | Aggregation] =
      contents.filterThroughWrappers[Returns].headOption.map(_.what)

    override def isEmpty: Boolean = super.isEmpty && input.isEmpty && output.isEmpty
  }

  @JSExportTopLevel("SagaRef")
  case class SagaRef(loc: At, pathId: PathIdentifier) extends Reference[Saga] {
    def format: String = s"saga ${pathId.format}"
  }

  //////////////////////////////////////////////////////////////////////////////////////////// EPIC

  /** A reference to an User using a path identifier
    *
    * @param loc
    *   THe location of the User in the source code
    * @param pathId
    *   The path identifier that locates the User
    */
  @JSExportTopLevel("UserRef")
  case class UserRef(loc: At, pathId: PathIdentifier) extends Reference[User] {
    def format: String = s"user ${pathId.format}"
  }

  sealed trait Interaction extends RiddlValue with WithMetaData

  sealed trait GenericInteraction extends Interaction {
    def relationship: LiteralString
  }

  /** One abstract step in an Interaction between things. The set of case classes associated with
    * this sealed trait provide more type specificity to these three fields.
    */
  sealed trait TwoReferenceInteraction extends GenericInteraction {
    def from: Reference[Definition]

    def to: Reference[Definition]
  }

  sealed trait InteractionContainer
      extends Interaction
      with Container[InteractionContainerContents]
      with WithMetaData:

    /** Format the node to a string */
    override def format: String = s"Interaction"
  end InteractionContainer

  /** An interaction expression that specifies that each contained expression should be executed in
    * parallel
    *
    * @param loc
    *   Location of the parallel group
    * @param contents
    *   The expressions to execute in parallel
    * @param metadata
    *   The metadata for this ParallelInteractions
    */
  @JSExportTopLevel("ParallelInteractions")
  case class ParallelInteractions(
    loc: At,
    contents: Contents[InteractionContainerContents] =
      Contents.empty[InteractionContainerContents](),
    metadata: Contents[MetaData] = Contents.empty[MetaData]()
  ) extends InteractionContainer {
    override def kind: String = "Parallel Interaction"
  }

  /** An interaction expression that specifies that each contained expression should be executed in
    * strict sequential order
    *
    * @param loc
    *   Location of the sequence
    * @param contents
    *   The interactions to execute in sequence
    * @param metadata
    *   The metadata for this SequentialInteractions
    */
  @JSExportTopLevel("SequentialInteractions")
  case class SequentialInteractions(
    loc: At,
    contents: Contents[InteractionContainerContents] =
      Contents.empty[InteractionContainerContents](),
    metadata: Contents[MetaData] = Contents.empty[MetaData]()
  ) extends InteractionContainer {
    override def kind: String = "Sequential Interaction"
  }

  /** An interaction expression that specifies that its contents are optional
    *
    * @param loc
    *   The location of the optional group
    * @param contents
    *   The optional expressions
    * @param metadata
    *   The metadata for this OptionalInteractions
    */
  @JSExportTopLevel("OptionalInteractions")
  case class OptionalInteractions(
    loc: At,
    contents: Contents[InteractionContainerContents] =
      Contents.empty[InteractionContainerContents](),
    metadata: Contents[MetaData] = Contents.empty[MetaData]()
  ) extends InteractionContainer {
    override def kind: String = "Optional Interaction"
  }

  /** An [[GenericInteraction]] that is vaguely written as a textual description
    *
    * @param loc
    *   The location of the interaction definition
    * @param from
    *   A [[LiteralString]] for the originating end of the relationship
    * @param relationship
    *   The relationship between the from and to
    * @param to
    *   A [[LiteralString]] for the receiving end of the relationship
    * @param metadata
    */
  @JSExportTopLevel("VagueInteraction")
  case class VagueInteraction(
    loc: At,
    from: LiteralString,
    relationship: LiteralString,
    to: LiteralString,
    metadata: Contents[MetaData] = Contents.empty[MetaData]()
  ) extends GenericInteraction {
    override def kind: String = "Vague Interaction"
    def format: String = s"${from.format} ${relationship.s} ${to.format}"
  }

  /** A [[GenericInteraction]] that involves sending a message between the interacting components
    *
    * @param loc
    *   The location of the interaction definition
    * @param from
    *   The definition that originates the interaction
    * @param message
    *   The message that is sent to the `to` component
    * @param to
    *   A [[Reference]] to the [[Processor]] that receives the sent `message`
    * @param metadata
    *   The metadata for this SendMessageInteraction
    */
  @JSExportTopLevel("SendMessageInteraction")
  case class SendMessageInteraction(
    loc: At,
    from: Reference[Definition],
    message: MessageRef,
    to: ProcessorRef[?],
    metadata: Contents[MetaData] = Contents.empty[MetaData]()
  ) extends GenericInteraction {
    def relationship: LiteralString = {
      LiteralString(message.loc, s"sends ${message.format} to")
    }

    override def kind: String = "Send Message Interaction"
    def format: String = s"${from.format} ${relationship.s} ${to.format}"
  }

  /** An arbitrary interaction step. The abstract nature of the relationship is
    *
    * @param loc
    *   The location of the step
    * @param from
    *   A reference to the source of the interaction
    * @param relationship
    *   A literal spring that specifies the arbitrary relationship
    * @param to
    *   A reference to the destination of the interaction
    * @param metadata
    *   The metadata for this ArbitraryInteraction
    */
  @JSExportTopLevel("ArbitraryInteraction")
  case class ArbitraryInteraction(
    loc: At,
    from: Reference[Definition],
    relationship: LiteralString,
    to: Reference[Definition],
    metadata: Contents[MetaData] = Contents.empty[MetaData]()
  ) extends TwoReferenceInteraction {
    override def kind: String = "Arbitrary Interaction"

    def format: String = s"${from.format} ${relationship.s} ${to.format}"

  }

  /** An [[TwoReferenceInteraction]] between a [[Definition]] and itself
    *
    * @param loc
    *   The location at which the interaction occurs
    * @param from
    *   A reference to a [[Definition]] from which the relationship extends and to which it returns.
    * @param relationship
    *   A textual description of the relationship
    * @param metadata
    *   The metadata for this SelfInteraction
    */
  @JSExportTopLevel("SelfInteraction")
  case class SelfInteraction(
    loc: At,
    from: Reference[Definition],
    relationship: LiteralString,
    metadata: Contents[MetaData] = Contents.empty[MetaData]()
  ) extends TwoReferenceInteraction {
    override def kind: String = "Self Interaction"
    override def to: Reference[Definition] = from
    def format: String = s"${from.format} ${relationship.s} ${to.format}"
  }

  /** An interaction where an User receives output
    *
    * @param loc
    *   The location of the interaction in the source
    * @param from
    *   The User that is being focused
    * @param to
    *   The Group that is the target of the focus
    * @param metadata
    *   The metadata for this FocsOnGroupInteraction
    */
  @JSExportTopLevel("FocusOnGroupInteraction")
  case class FocusOnGroupInteraction(
    loc: At,
    from: UserRef,
    to: GroupRef,
    metadata: Contents[MetaData] = Contents.empty[MetaData]()
  ) extends TwoReferenceInteraction {
    override def kind: String = "Focus On Group"
    override def relationship: LiteralString =
      LiteralString(loc + (6 + from.pathId.format.length), "focuses on")
    def format: String = s"${from.format} ${relationship.s} ${to.format}"
  }

  /** An interaction between a ser and a URL
    * @param loc
    *   THe location of the interaction in the model
    * @param from
    *   The user from which the interaction emanates
    * @param url
    *   The URL towards which the user is directed
    * @param metadata
    *   The metadata for this DirectUserToURLInteraction
    */
  @JSExportTopLevel("DirectUserToURLInteraction")
  case class DirectUserToURLInteraction(
    loc: At,
    from: UserRef,
    url: com.ossuminc.riddl.utils.URL,
    metadata: Contents[MetaData] = Contents.empty[MetaData]()
  ) extends GenericInteraction {
    def relationship: LiteralString =
      LiteralString(loc + (6 + from.pathId.format.length), "directed to ")
    override def kind: String = "Direct User To URL"
    def format: String = s"${from.format} ${relationship.s} ${url.toExternalForm}"
  }

  /** An interaction where an User receives output
    * @param loc
    *   The locaiton of the interaction in the source
    * @param from
    *   The output received
    * @param relationship
    *   THe name of the relationship
    * @param to
    *   The user that receives the output
    * @param metadata
    *   The metadata for this ShowoutputInteraction
    */
  @JSExportTopLevel("ShowOutputInteraction")
  case class ShowOutputInteraction(
    loc: At,
    from: OutputRef,
    relationship: LiteralString,
    to: UserRef,
    metadata: Contents[MetaData] = Contents.empty[MetaData]()
  ) extends TwoReferenceInteraction {
    override def kind: String = "Show Output Interaction"
    def format: String = s"${from.format} ${relationship.s} ${to.format}"
  }

  /** A interaction where a User selects an command generating item
    *
    * @param loc
    *   The location of the interaction in the source
    * @param from
    *   The user providing the input
    * @param to
    *   The input definition that receives the input
    * @param metadata
    *   The metadata for this SelectInputInteraction
    */
  @JSExportTopLevel("SelectInputInteraction")
  case class SelectInputInteraction(
    loc: At,
    from: UserRef,
    to: InputRef,
    metadata: Contents[MetaData] = Contents.empty[MetaData]()
  ) extends TwoReferenceInteraction {
    override def kind: String = "Select Input Interaction"
    def format: String = s"${from.format} selects ${to.format}"
    def relationship: LiteralString = LiteralString(loc, "selects")
  }

  /** A interaction where and User provides input
    *
    * @param loc
    *   The location of the interaction in the source
    * @param from
    *   The user providing the input
    * @param to
    *   The input definition that receives the input
    * @param metadata
    *   The metadata for this TakeInputInteraction
    */
  @JSExportTopLevel("TakeInputInteraction")
  case class TakeInputInteraction(
    loc: At,
    from: UserRef,
    to: InputRef,
    metadata: Contents[MetaData] = Contents.empty[MetaData]()
  ) extends TwoReferenceInteraction {
    override def kind: String = "Take Input Interaction"
    def format: String = s"${from.format} ${relationship.s} ${to.format}"
    def relationship: LiteralString = LiteralString(loc, "Provides data to")
  }

  /** An interaction where a system element refuses a User's request (A38). The refusing element is
    * the `from` side (a processor/context/entity or any interaction reference), the refused party
    * is the `to` [[UserRef]], and `reason` is a literal string explaining why the request was
    * refused.
    *
    * @param loc
    *   The location of the interaction in the source
    * @param from
    *   A reference to the system element that refuses the request
    * @param to
    *   The user whose request is refused
    * @param reason
    *   A literal string describing why the request is refused
    * @param metadata
    *   The metadata for this RefusalInteraction
    */
  @JSExportTopLevel("RefusalInteraction")
  case class RefusalInteraction(
    loc: At,
    from: Reference[Definition],
    to: UserRef,
    reason: LiteralString,
    metadata: Contents[MetaData] = Contents.empty[MetaData]()
  ) extends TwoReferenceInteraction {
    override def kind: String = "Refusal Interaction"
    def relationship: LiteralString = LiteralString(loc, "refuses")
    def format: String = s"${from.format} refuses ${to.format} ${reason.format}"
  }

  /** The definition of a Jacobsen Use Case RIDDL defines these epics by allowing a linkage between
    * the user and RIDDL applications or bounded contexts.
    * @param loc
    *   Where in the source this use case occurs
    * @param id
    *   The unique identifier for this use case
    * @param contents
    *   The interactions between users and system components that define the use case.
    */
  @JSExportTopLevel("UseCase")
  case class UseCase(
    loc: At,
    id: Identifier,
    userStory: UserStory,
    contents: Contents[UseCaseContents] = Contents.empty[UseCaseContents](),
    metadata: Contents[MetaData] = Contents.empty[MetaData]()
  ) extends Branch[UseCaseContents] {
    override def kind: String = "UseCase"
    override def format: String = s"case ${id.format}"
  }

  /** An agile user story definition in the usual "As a {role} I want {capability} so that
    * {benefit}" style.
    *
    * @param loc
    *   Location of the user story
    * @param user
    *   The user, or instigator, of the story.
    * @param capability
    *   The capability the user wishes to utilize
    * @param benefit
    *   The benefit of that utilization
    */
  @JSExportTopLevel("UserStory")
  case class UserStory(
    loc: At,
    user: UserRef,
    capability: LiteralString,
    benefit: LiteralString
  ) extends RiddlValue {
    def format: String = {
      user.format + " wants to \"" + capability.s + "\" so that \"" + benefit.s + "\""
    }
    override def isEmpty: Boolean =
      loc.isEmpty && user.isEmpty && capability.isEmpty && benefit.isEmpty
  }

  /** An element of a Use Case that links it to an external resource
    * @param loc
    *   The location at which the ShownBy occurs
    * @param urls
    *   The list of URLs by which the Use Case is shown
    */
  @JSExportTopLevel("ShownBy")
  case class ShownBy(
    loc: At = At.empty,
    urls: Seq[URL] = Seq.empty
  ) extends RiddlValue:
    def format: String = "shown by "
  end ShownBy

  /** The definition of an Epic that bundles multiple Jacobsen Use Cases into an overall story about
    * user interactions with the system. This define functionality from the perspective of users
    * (men or machines) interactions with the system that is part of their role.
    *
    * @param loc
    *   The location of the Epic definition
    * @param id
    *   The name of the Epic
    * @param userStory
    *   The [[UserStory]] (per agile and xP) that provides the overall big picture of this Epic
    * @param contents
    *   The definitional content for this Context
    */
  @JSExportTopLevel("Epic")
  case class Epic(
    loc: At,
    id: Identifier,
    userStory: UserStory,
    contents: Contents[EpicContents] = Contents.empty[EpicContents](),
    metadata: Contents[MetaData] = Contents.empty[MetaData]()
  ) extends VitalDefinition[EpicContents]
      with WithUseCases[EpicContents]
      with WithShownBy[EpicContents] {

    override def isEmpty: Boolean = userStory.isEmpty && contents.isEmpty

    override def format: String = s"$kind ${id.format}"
  }

  /** A reference to a Story definintion.
    * @param loc
    *   Location of the StoryRef
    * @param pathId
    *   The path id of the referenced Story
    */
  @JSExportTopLevel("EpicRef")
  case class EpicRef(loc: At, pathId: PathIdentifier) extends Reference[Epic] {
    def format: String = s"epic ${pathId.format}"
  }

  /////////////////////////////////////////////////////////////////////////////////////////// GROUP

  /** A group of GroupDefinition that can be treated as a whole. For example, a form, a button
    * group, etc.
    * @param loc
    *   The location of the group
    * @param alias
    *   The buzzword used to define this group
    * @param id
    *   The unique identifier of the group
    * @param contents
    *   The list of GroupDefinition
    */
  @JSExportTopLevel("Group")
  case class Group(
    loc: At,
    alias: String,
    id: Identifier,
    contents: Contents[OccursInGroup] = Contents.empty[OccursInGroup](),
    metadata: Contents[MetaData] = Contents.empty[MetaData]()
  ) extends Branch[OccursInGroup]
      with WithShownBy[OccursInGroup]
      with WithInputs[OccursInGroup]
      with WithOutputs[OccursInGroup]:
    override def identify: String = s"$alias ${id.value}"

    /** Format the node to a string */
    override def format: String = s"group ${id.value}"
  end Group

  /** A Reference to a Group
    *
    * @param loc
    *   The At locator of the group reference
    * @param keyword
    *   The keyword used to introduce the Group
    * @param pathId
    *   The path to the referenced group
    */
  @JSExportTopLevel("GroupRef")
  case class GroupRef(loc: At, keyword: String, pathId: PathIdentifier) extends Reference[Group]:
    def format: String = s"$keyword ${pathId.format}"
  end GroupRef

  /** A Group contained within a group
    *
    * @param loc
    *   Location of the contained group
    * @param id
    *   The name of the group contained
    * @param group
    *   The contained group as a reference to that group
    */
  @JSExportTopLevel("ContainedGroup")
  case class ContainedGroup(
    loc: At,
    id: Identifier,
    group: GroupRef,
    metadata: Contents[MetaData] = Contents.empty[MetaData]()
  ) extends Leaf:
    def format: String = s"contains ${id.format} as ${group.format}"
  end ContainedGroup

//////////////////////////////////////////////////////////////////////////////////////////// OUTPUT

  /** A UI Element that presents some information to the user
    *
    * @param loc
    *   Location of the view in the source
    * @param id
    *   unique identifier oof the view
    * @param putOut
    *   A result reference for the data too be presented
    * @param contents
    *   Any contained outputs
    */
  @JSExportTopLevel("Output")
  case class Output(
    loc: At,
    nounAlias: String,
    id: Identifier,
    verbAlias: String,
    putOut: TypeRef | ConstantRef | LiteralString,
    contents: Contents[OccursInOutput] = Contents.empty[OccursInOutput](),
    metadata: Contents[MetaData] = Contents.empty[MetaData]()
  ) extends Branch[OccursInOutput]
      with WithOutputs[OccursInOutput]:
    override def kind: String = if nounAlias.nonEmpty then nounAlias else super.kind
    override def identify: String = s"$verbAlias ${id.value}"

    /** Format the node to a string */
    override def format: String = s"$kind ${id.value} $verbAlias ${putOut.format}"
  end Output

  /** A reference to an Output using a path identifier
    *
    * @param loc
    *   The location of the ViewRef in the source code
    * @param pathId
    *   The path identifier that refers to the View
    */
  @JSExportTopLevel("OutputRef")
  case class OutputRef(loc: At, keyword: String, pathId: PathIdentifier) extends Reference[Output]:
    def format: String = s"$keyword ${pathId.format}"
  end OutputRef

  //////////////////////////////////////////////////////////////////////////////////////////// INPUT

  /** An Input is a UI Element to allow the user to provide some data to the application. It is
    * analogous to a form in HTML
    *
    * @param loc
    *   Location of the Give
    * @param id
    *   Name of the give
    * @param takeIn
    *   a Type reference of the type given by the user
    */
  @JSExportTopLevel("Input")
  case class Input(
    loc: At,
    nounAlias: String,
    id: Identifier,
    verbAlias: String,
    takeIn: TypeRef,
    contents: Contents[OccursInInput] = Contents.empty[OccursInInput](),
    metadata: Contents[MetaData] = Contents.empty[MetaData]()
  ) extends Branch[OccursInInput]
      with WithInputs[OccursInInput]:
    override def kind: String = if nounAlias.nonEmpty then nounAlias else super.kind
    override def identify: String = s"$verbAlias ${id.value}"

    /** Format the node to a string */
    override def format: String = {
      s"$kind $verbAlias ${takeIn.format}"
    }
  end Input

  /** A reference to an Input using a path identifier
    *
    * @param loc
    *   THe location of the GiveRef in the source code
    * @param pathId
    *   The path identifier that refers to the Give
    */
  @JSExportTopLevel("InputRef")
  case class InputRef(loc: At, keyword: String, pathId: PathIdentifier) extends Reference[Input]:
    def format: String = s"$keyword ${pathId.format}"
  end InputRef

  ////////////////////////////////////////////////////////////////////////////////////////// DOMAIN

  /** The definition of a domain. Domains are the highest building block in RIDDL and may be nested
    * inside each other to form a hierarchy of domains. Generally, domains follow hierarchical
    * organization structure but other taxonomies and ontologies may be modelled with domains too.
    *
    * @param loc
    *   The location of the domain definition
    * @param id
    *   The name of the domain
    * @param contents
    *   The definitional content for this Context
    */
  @JSExportTopLevel("Domain")
  case class Domain(
    loc: At,
    id: Identifier,
    contents: Contents[DomainContents] = Contents.empty[DomainContents](),
    metadata: Contents[MetaData] = Contents.empty[MetaData]()
  ) extends VitalDefinition[DomainContents]
      with WithTypes[DomainContents]
      with WithAuthors[DomainContents]
      with WithContexts[DomainContents]
      with WithUsers[DomainContents]
      with WithEpics[DomainContents]
      with WithSagas[DomainContents]
      with WithRepositories[DomainContents]
      with WithConnectors[DomainContents]
      with WithVersion[DomainContents]
      with WithCopyright[DomainContents]
      with WithDomains[DomainContents] {
    override def format: String = Keyword.domain + " " + id.format
  }

  /** A reference to a domain definition
    *
    * @param loc
    *   The location at which the domain definition occurs
    * @param pathId
    *   The path identifier for the referenced domain.
    */
  @JSExportTopLevel("DomainRef")
  case class DomainRef(loc: At, pathId: PathIdentifier) extends Reference[Domain] {
    override def format: String = s"domain ${pathId.format}"
  }

/////////////////////////////////////////////////////////////////////////////////////////////////////////// TOKENS
  enum Token(at: At):
    val loc: At = at
    case Punctuation(at: At) extends Token(at)
    case QuotedString(at: At) extends Token(at)
    case Readability(at: At) extends Token(at)
    case Predefined(at: At) extends Token(at)
    case Keyword(at: At) extends Token(at)
    case Comment(at: At) extends Token(at)
    case LiteralCode(at: At) extends Token(at)
    case MarkdownLine(at: At) extends Token(at)
    case Identifier(at: At) extends Token(at)
    case Numeric(at: At) extends Token(at)
    case Other(at: At) extends Token(at)
  end Token

  /////////////////////////////////////////////////////////////////////////////////////////////////////////// FUNCTIONS

  /** Find the authors for some definition
    *
    * @param definition
    *   The definition whose [[AST.Author]]s we are seeking
    * @param parents
    *   The parents of the definition whose [[AST.Author]]s we are seeking
    * @return
    *   The list of [[AST.Author]]s of definition
    */
  @JSExport
  def findAuthors(
    definition: Definition,
    parents: Contents[RiddlValue]
  ): Seq[AuthorRef] =
    val result = definition.authorRefs
    if result.isEmpty then parents.filter[Definition].flatMap(_.authorRefs)
    else result
    end if
  end findAuthors

  /** The [[Copyright]] a single definition declares for its own scope, if any (A47).
    *
    * Only the copyright-bearing scopes ([[Root]], [[Module]], [[Domain]] and every [[Processor]])
    * can declare one; everything else yields [[None]].
    */
  @JSExport
  def copyrightOf(definition: Definition): Option[Copyright] =
    definition match
      case wc: WithCopyright[?] => wc.copyright
      case _                    => None
  end copyrightOf

  /** The [[Copyright]] that APPLIES to a definition: NEAREST DECLARING SCOPE WINS (A47).
    *
    * Unlike [[composedVersion]], which accumulates a coordinate out of EVERY versioned ancestor, a
    * copyright does not compose. The applicable notice is the one declared by the definition
    * itself, or failing that by the nearest ancestor that declares one — the [[findAuthors]]
    * precedent. That is the whole point of allowing it at inner scopes: an `external context`
    * bearing a third party's notice must OVERRIDE its enclosing domain's for everything inside it,
    * not be appended to it.
    *
    * `parents` arrives in RIDDL's usual LEAF→ROOT order, which is already nearest-first, so this
    * walk does NOT reverse it — the opposite of [[composedVersion]].
    *
    * @param definition
    *   The definition whose applicable copyright is sought
    * @param parents
    *   The parents of that definition, in RIDDL's usual LEAF→ROOT order
    * @return
    *   The nearest declared [[Copyright]], or [[None]] when nothing in the chain declares one
    */
  @JSExport
  def findCopyright(
    definition: Definition,
    parents: Contents[RiddlValue]
  ): Option[Copyright] =
    copyrightOf(definition).orElse {
      parents.filter[Definition].iterator.map(copyrightOf).collectFirst { case Some(c) => c }
    }
  end findCopyright

  /** The version component a single definition declares for its own scope, if any (A53).
    *
    * Only the version-bearing scopes ([[Root]], [[Module]], [[Domain]] and every [[Processor]] —
    * A47) can declare one; everything else yields [[None]].
    */
  @JSExport
  def versionOf(definition: Definition): Option[String] =
    definition match
      case wv: WithVersion[?] => wv.version.map(_.component)
      case _                  => None
  end versionOf

  /** The separator joining the components of a composed version coordinate (A53). */
  final val VersionSeparator: String = "."

  /** Compose the precise version of a definition from its versioned ancestors, root→leaf (A53).
    *
    * The result is a '''hierarchical coordinate''', NOT a semantic version — see [[AST.Version]]
    * for the caveats. Components are strings because a scope may name its version rather than
    * number it (`version Garibaldi`). Only ancestors (and the definition itself) that actually BEAR
    * a [[Version]] contribute a component, so `domain Garibaldi / context 4 / entity 3` composes to
    * `Seq("Garibaldi", "4", "3")` while the same model with an unversioned context composes to
    * `Seq("Garibaldi", "3")` ("missing-level rule").
    *
    * A definition that establishes no version scope of its own — a [[Type]], a message, a
    * [[Handler]] — simply contributes nothing, so it reports the composed version of its container.
    *
    * @param definition
    *   The definition whose composed version is sought
    * @param parents
    *   The parents of that definition, in RIDDL's usual LEAF→ROOT order
    * @return
    *   The composed version coordinate, root→leaf; empty when nothing in the chain is versioned
    */
  @JSExport
  def composedVersion(
    definition: Definition,
    parents: Contents[RiddlValue]
  ): Seq[String] =
    val chain: Seq[Definition] = parents.filter[Definition].reverse :+ definition
    chain.flatMap(versionOf)
  end composedVersion

  /** The dotted rendering of [[composedVersion]] — components joined with `.` (A53).
    *
    * @return
    *   e.g. `"Garibaldi.4.3"`, or the empty string when nothing in the chain is versioned
    */
  @JSExport
  def composedVersionString(
    definition: Definition,
    parents: Contents[RiddlValue]
  ): String =
    composedVersion(definition, parents).mkString(VersionSeparator)
  end composedVersionString

  /** Get all the top level domain definitions even if they are in include statements
    * @param root
    *   The model's [[AST.Root]] node.
    * @return
    *   A Seq of [[AST.Domain]]s including those in [[AST.Include]]s
    */
  @JSExport
  def getTopLevelDomains(root: Root): Seq[Domain] = {
    root.domains
  }

  /** Get all the first level nested domains of a domain even if they are in include statements
    * @param domain
    *   The parent [[AST.Domain]] whose subdomains will be returned
    * @return
    *   The subdomains of the provided domain including those in [[AST.Include]]s
    */
  @JSExport
  def getDomains(domain: Domain): Seq[Domain] = {
    domain.domains
  }

  def getAllDomains(root: Root): Seq[Domain] = {
    for {
      domain <- getTopLevelDomains(root)
      domains <- getDomains(domain)
    } yield { domains }
  }

  /** Get the bounded contexts defined in a domain even if they are in includes of that domain
    * @param domain
    *   The domain whose contexts should be returned
    * @return
    *   A Seq of Context expressed including those in [[AST.Include]]s
    */
  @JSExport
  def getContexts(domain: Domain): Seq[Context] = {
    domain.contexts
  }

  /** get all the epics defined in a domain even if they are in includes of that domain
    *
    * @param domain
    *   The domain to examine for epics
    * @return
    *   A [[scala.Seq]] of [[AST.Epic]] expressed including those in [[AST.Include]]s
    */
  @JSExport
  def getEpics(domain: Domain): Seq[Epic] = {
    domain.epics
  }

  /** get all the entities defined in a referent even if they are in includes of that domain
    *
    * @param context
    *   The domain to examine for entities
    * @return
    *   A Seq of [[AST.Entity]] expressed including those in [[AST.Include]]s
    */
  @JSExport
  def getEntities(context: Context): Seq[Entity] = {
    context.entities
  }

  /** Get all the authors defined in a domain even if they are in includes of that domain
    *
    * @param domain
    *   The domain to examine for authors
    * @return
    *   A Seq of [[AST.Author]] from the domain and nested domains
    */
  @JSExport
  def getAuthors(domain: Domain): Seq[Author] = {
    domain.authors ++ domain.domains.flatMap(getAuthors)
  }

  /** Get all the authors defined in the root node even if they are in includes
    *
    * @param root
    *   The root to examine for authors
    * @return
    *   A Seq of [[AST.Author]] from all domains
    */
  @JSExport
  def getAuthors(root: Root): Seq[Author] = {
    root.domains.flatMap(getAuthors)
  }

  /** Get all the [[User]]s defined in a [[Domain]] node even if they are in includes
    *
    * @param domain
    *   The domain to examine for users
    * @return
    *   A Seq of [[AST.User]] from the domain and nested domains
    */
  @JSExport
  def getUsers(domain: Domain): Seq[User] = {
    domain.users ++ domain.domains.flatMap(getUsers)
  }

  /** Get the [[AST.User]] definitions found at the [[AST.Root]] level or in its [[AST.Include]]s
    * @param root
    *   The [[AST.Root]] node to examine
    * @return
    *   A Seq of [[AST.User]] from all domains and root-level includes
    */
  @JSExport
  def getUsers(root: Root): Seq[User] = {
    // The second term is NOT redundant with include-transparent accessors, unlike the manual
    // walks the sibling getters used to carry: it collects Users written at ROOT level inside an
    // include, which `root.domains` cannot reach because they belong to no domain. The two sets
    // are disjoint, so this does not double count.
    root.domains.flatMap(getUsers) ++ root.includes.flatMap(_.contents.filter[User])
  }

  extension (optLit: Option[LiteralString])
    /** An extension to an [[scala.Option[LiteralString]]] that makes extracting the content of the
      * [[LiteralString]] easier.
      * @return
      *   The content of the formatted LiteralString or "N/A" if it is not available
      */
    @JSExport
    def format: String = optLit.map(_.format).getOrElse("N/A")

  /** A utility function for getting the kind of a type expression.
    *
    * @param te
    *   The type expression to examine
    *
    * @return
    *   A string indicating the kind corresponding to te
    */
  @JSExport
  def errorDescription(te: TypeExpression): String =
    te match
      case AliasedTypeExpression(_, keyword, pid) => s"$keyword ${pid.format}"
      case Optional(_, typeExp)                   => errorDescription(typeExp) + "?"
      case ZeroOrMore(_, typeExp)                 => errorDescription(typeExp) + "*"
      case OneOrMore(_, typeExp)                  => errorDescription(typeExp) + "+"
      case e: Enumeration                         => s"Enumeration of ${e.enumerators.size} values"
      case a: Alternation                         => s"Alternation of ${a.of.size} types"
      case a: Aggregation                         => s"Aggregation of ${a.fields.size} fields"
      case Mapping(_, from, to) =>
        s"Map from ${errorDescription(from)} to ${errorDescription(to)}"
      case EntityReferenceTypeExpression(_, entity) =>
        s"Reference to entity ${entity.format}"
      case p: Pattern              => p.format
      case Decimal(_, whl, frac)   => s"Decimal($whl,$frac)"
      // Delegated rather than re-spelled: this line held a SECOND copy of the label, which is how
      // it kept saying `Range(2,4)` after `RangeType.kind` was lowered to the spelling that
      // actually parses. One source of truth means it cannot drift again.
      case rt: RangeType           => rt.format
      // Same reasoning as RangeType above: delegate to format() so the keyword (added
      // 2026-08-13) can't drift out of sync with a hand-built second copy of the label.
      case uid: UniqueId           => uid.format
      case m @ AggregateUseCaseTypeExpression(_, messageKind, _, _) =>
        s"${messageKind.useCase} of ${m.fields.size} fields and ${m.methods.size} methods"
      case pt: PredefinedType => pt.kind
    end match
  end errorDescription

  /** What each container may hold, derived from the containment unions themselves.
    *
    * The rules live in exactly one place already — the `OccursInX` / `XContents` aliases above,
    * which are what the parser's return types are checked against. `utils.UnionMembers.contains` expands
    * one of those unions into a membership test at COMPILE time, so every entry below is a
    * restatement of nothing: add a member to `ContextContents` and `context.canContain` gains it
    * with no edit here.
    *
    * **The match deliberately has no default case.** `Branch` is sealed and this build runs with
    * `-Werror`, so adding a container without deciding what it may hold is a BUILD FAILURE rather
    * than a predicate that quietly answers `false`. That is the property that makes centralising
    * this worth anything — a hand-written list would have been shorter and would have reintroduced
    * exactly the drift it was meant to remove.
    *
    * Generic containers are absent on purpose. `Include[CT]`, `SimpleContainer[CV]` and the
    * accessor traits take their content type as a PARAMETER, so they have no union of their own to
    * consult and the question is ill-posed for them — an `Include` may hold whatever its parent
    * may, which is why [[Branch.canContain]] treats it as transparent and asks the parent instead.
    */
  private[language] object Containment:
    import com.ossuminc.riddl.utils.UnionMembers.{Contains, contains}

    private lazy val rootIn = contains[RootContents]
    private lazy val moduleIn = contains[ModuleContents]
    private lazy val domainIn = contains[DomainContents]
    private lazy val contextIn = contains[ContextContents]
    private lazy val entityIn = contains[EntityContents]
    private lazy val adaptorIn = contains[AdaptorContents]
    private lazy val repositoryIn = contains[RepositoryContents]
    private lazy val projectorIn = contains[ProjectorContents]
    private lazy val streamletIn = contains[StreamletContents]
    private lazy val functionIn = contains[FunctionContents]
    private lazy val sagaIn = contains[SagaContents]
    private lazy val epicIn = contains[EpicContents]
    private lazy val useCaseIn = contains[UseCaseContents]
    private lazy val typeIn = contains[TypeContents]
    private lazy val handlerIn = contains[HandlerContents]
    private lazy val stateIn = contains[StateContents]
    private lazy val correlationIn = contains[CorrelationContents]
    private lazy val groupIn = contains[OccursInGroup]
    private lazy val outputIn = contains[OccursInOutput]
    private lazy val inputIn = contains[OccursInInput]
    private lazy val onClauseIn = contains[Statements]

    def of(branch: Branch[?]): Contains = branch match
      case _: Root       => rootIn
      case _: Module     => moduleIn
      case _: Domain     => domainIn
      case _: Context    => contextIn
      case _: Entity     => entityIn
      case _: Adaptor    => adaptorIn
      case _: Repository => repositoryIn
      case _: Projector  => projectorIn
      case _: Streamlet  => streamletIn
      case _: Function   => functionIn
      case _: Saga       => sagaIn
      case _: Epic       => epicIn
      case _: UseCase    => useCaseIn
      case _: Type       => typeIn
      case _: Handler     => handlerIn
      case _: State       => stateIn
      case _: Correlation => correlationIn
      case _: Group      => groupIn
      case _: Output     => outputIn
      case _: Input      => inputIn
      case _: OnClause   => onClauseIn
    end of
  end Containment
end AST
