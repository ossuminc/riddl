/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language

import com.ossuminc.riddl.language.AST.Contents.unapply
import com.ossuminc.riddl.utils.{Await, PlatformContext, URL}
import com.ossuminc.riddl.language.Messages.Messages
import com.ossuminc.riddl.language.parsing.{Keyword, RiddlParserInput}

import scala.collection.{mutable, immutable}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.reflect.{ClassTag, classTag}
import scala.annotation.{tailrec, targetName, unused}
import scala.io.{BufferedSource, Codec}
import scala.scalajs.js.annotation.*
import wvlet.airframe.ulid.ULID

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

  /** A representation of the editable contents of a definition
    * @tparam CV
    *   The upper bound of the values that can be contained (RiddlValue)
    */
  opaque type Contents[CV <: RiddlValue] = mutable.ArrayBuffer[CV]

  object Contents:
    def dempty[T <: RiddlValue]: Contents[T] = new mutable.ArrayBuffer[T](2)
    def empty[T <: RiddlValue](
      initialSize: Int = mutable.ArrayBuffer.DefaultInitialSize
    ): Contents[T] =
      new mutable.ArrayBuffer[T](initialSize)
    def apply[T <: RiddlValue](items: T*): Contents[T] = mutable.ArrayBuffer[T](items: _*)
    def unapply[T <: RiddlValue](contents: Contents[T]) =
      mutable.ArrayBuffer.unapplySeq[T](contents)
  end Contents

  extension [CV <: RiddlValue](sequence: Seq[CV])
    def toContents: Contents[CV] = Contents[CV](sequence: _*)
    def find(name: String): Option[CV] =
      sequence.find(d =>
        d.isInstanceOf[WithIdentifier] && d.asInstanceOf[WithIdentifier].id.value == name
      )

  /** The extension of a mutable ArrayBuffer of [[RiddlValue]] for ease of manipulating that content
    */
  extension [CV <: RiddlValue, CV2 <: RiddlValue](container: Contents[CV])

    inline def length: Int = container.length
    inline def size: Int = container.length
    inline def apply(n: Int): CV = container.apply(n)
    inline def head: CV = container.apply(0)
    inline def indexOf[B >: CV](elem: B): Int = container.indexOf[B](elem, 0)
    inline def splitAt(n: Int): (Contents[CV], Contents[CV]) = container.splitAt(n)
    inline def indices: Range = Range(0, container.length)
    inline def foreach[T](f: CV => T): Unit = container.foreach(f)
    inline def forall(p: CV => Boolean): Boolean = container.forall(p)
    inline def update(index: Int, elem: CV): Unit = container.update(index, elem)
    inline def foldLeft[B](z: B)(op: (B, CV) => B): B = container.foldLeft[B](z)(op)
    inline def isEmpty: Boolean = container.isEmpty
    inline def nonEmpty: Boolean = !isEmpty
    inline def map[B <: RiddlValue](f: CV => B): Contents[B] = container.map[B](f)
    inline def flatMap[B <: RiddlValue](f: CV => IterableOnce[B]): Contents[B] =
      container.flatMap[B](f)

    inline def startsWith[B >: CV](that: IterableOnce[B], offset: Int = 0): Boolean =
      container.startsWith[B](that)

    def toSet[B >: CV <: RiddlValue]: immutable.Set[B] = immutable.Set.from(container)
    def toSeq: immutable.Seq[CV] = container.toSeq
    def toIterator: Iterator[CV] = container.toIterator

    inline def dropRight(howMany: Int): Contents[CV] = container.dropRight(howMany)
    inline def drop(howMany: Int): Contents[CV] = container.drop(howMany)
    inline def append(elem: CV): Unit = container.append(elem)
    inline def prepend(elem: CV): Unit = container.prepend(elem)
    inline def ++(suffix: IterableOnce[CV]): Contents[CV] =
      container.concat[CV](suffix).asInstanceOf[Contents[CV]]

    // @`inline` final def ++ [B >: A](xs: => IterableOnce[B]): Iterator[B] = concat(xs)
    /** Merge to Contents of varying upper bound constraints into a single combined container */
    def merge(other: Contents[CV2]): Contents[CV & CV2] =
      val result = Contents.empty[CV & CV2](container.size + other.size)
      result ++= container.asInstanceOf[Contents[CV & CV2]]
      result ++= other.asInstanceOf[Contents[CV & CV2]]
      result
    end merge

    /** Extract the elements of the [[Contents]] that have identifiers (are definitions,
      * essentially)
      */
    private def identified: Contents[CV] = container.filter(_.isIdentified)

    /** Extract the elements of the [[Contents]] that are the type of the type parameter T
      *
      * @tparam T
      *   THe kind of [[RiddlValue]] sought in the [[Contents]]
      *
      * @return
      *   The Seq of type `T` found in the [[Contents]]
      */
    def filter[T <: RiddlValue: ClassTag]: Seq[T] =
      val theClass = classTag[T].runtimeClass
      container.filter(x => theClass.isAssignableFrom(x.getClass)).map(_.asInstanceOf[T]).toSeq
    end filter

    /** Returns the elements of the [[Contents]] that are [[VitalDefinition]]s */
    def vitals: Seq[VitalDefinition[?]] = container.filter[VitalDefinition[?]]

    /** Returns the elememts of the [[Contents]] that are [[Processor]]s */
    def processors: Seq[Processor[?]] = container.filter[Processor[?]]

    /** Find the first element of the [[Contents]] that has the provided `name` */
    def find(name: String): Option[CV] =
      identified.find(d =>
        d.isInstanceOf[WithIdentifier] && d.asInstanceOf[WithIdentifier].id.value == name
      )

    /** Find the first element of the [[Contents]] that */
    def identifiedValues: Seq[WithIdentifier] =
      container
        .filter(d => d.isInstanceOf[WithIdentifier])
        .map(_.asInstanceOf[WithIdentifier])
        .toSeq

    /** Returns the [[Include]] elements of [[Contents]] */
    def includes: Seq[Include[?]] = container.filter[Include[?]].map(_.asInstanceOf[Include[?]])

    /** find the elements of the [[Contents]] that are [[Definition]]s */
    def definitions: Definitions = container.filter[Definition].map(_.asInstanceOf[Definition])

    /** find the elemetns of the [[Contents]] that are [[Branch]]s */
    def parents: Seq[Branch[CV]] = container.filter[Branch[CV]]

  end extension

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

    override def isEmpty: Boolean = contents.isEmpty

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
    override def format: String = value
    override def isEmpty: Boolean = value.isEmpty
  end Identifier

  /** Companion object for the Identifier class to provide the empty value */
  object Identifier:
    /** Definition of the empty [[Identifier]] */
    val empty: Identifier = Identifier(At.empty, "")
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
    override def format: String = value.mkString(".")
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
    *   The URL for the file content that is the description.
    */
  case class URLDescription(loc: At, url: URL)(using urlLoader: PlatformContext)
      extends Description:
    lazy val lines: Seq[LiteralString] = {
      val future = urlLoader.load(url).map(_.split("\n").toSeq.map(LiteralString(loc, _)))
      Await.result(future, 10)
    }
    override def format: String = url.toExternalForm
  end URLDescription

  /** */
  case class FileAttachment(
    loc: At,
    id: Identifier,
    mimeType: String,
    inFile: LiteralString
  ) extends Meta
      with WithIdentifier:
    def format: String = identify
  end FileAttachment

  /** */
  case class StringAttachment(
    loc: At,
    id: Identifier,
    mimeType: String,
    value: LiteralString
  ) extends Meta
      with WithIdentifier:
    def format: String = identify
  end StringAttachment

  case class ULIDAttachment(
    loc: At,
    ulid: ULID
  ) extends Meta
      with WithIdentifier:
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
      s"${classTag[T].runtimeClass.getSimpleName} ${
          if id.nonEmpty then {
            id.map(_.format + ": ")
          } else ""
        }'${pathId.format}'"
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
        s"$kind '${id.format}'"
      }
    end identify

    /** Same as [[identify]] but also adds the value's location via [[loc]] */
    def identifyWithLoc: String = s"$identify at ${loc.format}"
  end WithIdentifier

  sealed trait WithMetaData extends RiddlValue:
    def metadata: Contents[MetaData]

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

    def options: Seq[OptionValue] = metadata.filter[OptionValue]

    def hasOption(name: String): Boolean = options.exists(_.name == name)

    /** Get the value of `name`'d option, if there is one. */
    def getOptionValue(name: String): Option[OptionValue] = options.find(_.name == name)

    /** A lazily constructed mutable [[Seq]] of [[AuthorRef]] */
    def authorRefs: Seq[AuthorRef] = metadata.filter[AuthorRef]

    lazy val ulid: ULID =
      metadata.find("ULID") match
        case Some(ulid: ULIDAttachment) => ulid.ulid
        case _ =>
          val result = ULID.newULID
          metadata += ULIDAttachment(At.empty, result)
          result
      end match
    end ulid

  end WithMetaData

  /** A trait that includes the `comments` field to extract the comments from the contents */
  sealed trait WithComments[CV <: RiddlValue] extends Container[CV]:

    /** A lazily constructed [[Seq]] of [[Comment]] filtered from the contents */
    def comments: Seq[Comment] = contents.filter[Comment]
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
    def types: Seq[Type] = contents.filter[Type]
    override def hasTypes: Boolean = types.nonEmpty
  end WithTypes

  /** Base trait to use in any definition that can define a constant */
  sealed trait WithConstants[CV <: RiddlValue] extends Container[CV]:

    /** A lazily constructed [[Seq]] of [[Constant]] filtered from the contents */
    def constants: Seq[Constant] = contents.filter[Constant]
  end WithConstants

  /** Base trait to use in any [[Definition]] that can define an invariant */
  sealed trait WithInvariants[CV <: RiddlValue] extends Container[CV]:

    /** A lazily constructed [[Seq]] of [[Invariant]] filtered from the contents */
    def invariants: Seq[Invariant] = contents.filter[Invariant]
  end WithInvariants

  /** Base trait to use in any [[Definition]] that can define a [[Function]] */
  sealed trait WithFunctions[CV <: RiddlValue] extends Container[CV]:

    /** A lazily constructed [[Seq]] of [[Function]] filtered from the contents */
    def functions: Seq[Function] = contents.filter[Function].toSeq
  end WithFunctions

  /** Base trait to use in any [[Processor]] because they define [[Handler]]s */
  sealed trait WithHandlers[CV <: RiddlValue] extends Container[CV]:

    /** A lazily constructed [[Seq]] of [[Handler]] filtered from the contents */
    def handlers: Seq[Handler] = contents.filter[Handler]
  end WithHandlers

  /** Base trait to use in any [[Definition]] that can define an [[Inlet]] */
  sealed trait WithInlets[CV <: RiddlValue] extends Container[CV]:

    /** A lazily constructed [[Seq]] of [[Inlet]] filtered from the contents */
    def inlets: Seq[Inlet] = contents.filter[Inlet]
  end WithInlets

  /** Base trait to use in any [[Definition]] that can define an [[Outlet]] */
  sealed trait WithOutlets[CV <: RiddlValue] extends Container[CV]:

    /** A lazily constructed [[Seq]] of [[Outlet]] filtered from the contents */
    def outlets: Seq[Outlet] = contents.filter[Outlet]
  end WithOutlets

  /** Base trait to use in any [[Definition]] that can define a [[State]] */
  sealed trait WithStates[CV <: RiddlValue] extends Container[?]:

    /** A lazily constructed [[Seq]] of [[State]] filtered from the contents */
    def states: Seq[State] = contents.filter[State]
  end WithStates

  /** Base trait to use in any [[Definition]] that can define a [[Group]] */
  sealed trait WithGroups[CV <: RiddlValue] extends Container[CV]:

    /** A lazily constructed [[Seq]] of [[Group]] filtered from the contents */
    def groups: Seq[Group] = contents.filter[Group]
  end WithGroups

  /** Base trait to use in any [[Definition]] that can define a [[Output]] */
  sealed trait WithOutputs[CV <: RiddlValue] extends Container[CV]:

    /** A lazily constructed [[Seq]] of [[Output]] filtered from the contents */
    def outputs: Seq[Output] = contents.filter[Output]
  end WithOutputs

  /** Base trait to use in any [[Definition]] that can define a [[Output]] */
  sealed trait WithInputs[CV <: RiddlValue] extends Container[CV]:

    /** A lazily constructed [[Seq]] of [[Output]] filtered from the contents */
    def inputs: Seq[Input] = contents.filter[Input]
  end WithInputs

  /** Base trait to use to define the [[AST.Statement]]s that form the body of a [[Function]] or
    * [[OnClause]]
    */
  sealed trait WithStatements[CV <: RiddlValue] extends Container[CV]:

    /** A lazily constructed [[Seq]] of [[Statement]] filtered from the contents */
    def statements: Seq[Statement] = contents.filter[Statement]
  end WithStatements

  /** Base trait to use in a [[Domain]] to define the bounded [[Context]] it contains */
  sealed trait WithContexts[CV <: RiddlValue] extends Container[CV]:

    /** A lazily constructed [[Seq]] of [[Context]] filtered from the contents */
    def contexts: Seq[Context] = contents.filter[Context]
  end WithContexts

  /** Base trait to use in any [[Definition]] that can define [[Author]]s */
  sealed trait WithAuthors[CV <: RiddlValue] extends Container[CV]:

    /** A lazily constructed [[Seq]] of [[Author]] filtered from the contents */
    def authors: Seq[Author] = contents.filter[Author]
    override def hasAuthors: Boolean = authors.nonEmpty
  end WithAuthors

  /** Base trait to use in any [[Definition]] that can define [[User]]s */
  sealed trait WithUsers[CV <: RiddlValue] extends Container[CV]:

    /** A lazily constructed [[Seq]] of [[User]] filtered from the contents */
    def users: Seq[User] = contents.filter[User]
  end WithUsers

  /** Base trait to use in any [[Definition]] that can define [[Epic]]s */
  sealed trait WithEpics[CV <: RiddlValue] extends Container[CV]:

    /** A lazily constructed [[Seq]] of [[Epic]] filtered from the contents */
    def epics: Seq[Epic] = contents.filter[Epic]
  end WithEpics

  /** Base trait to use in any [[Definition]] that can define [[Domain]]s */
  sealed trait WithDomains[CV <: RiddlValue] extends Container[CV]:

    /** A lazily constructed [[Seq]] of [[Domain]] filtered from the contents */
    def domains: Seq[Domain] = contents.filter[Domain]
  end WithDomains

  /** Base trait to use in any [[Definition]] that can define [[Projector]]s */
  sealed trait WithProjectors[CV <: RiddlValue] extends Container[CV]:

    /** A lazily constructed [[Seq]] of [[Projector]] filtered from the contents */
    def projectors: Seq[Projector] = contents.filter[Projector]
  end WithProjectors

  /** Base trait to use in any [[Definition]] that can define [[Repository]]s */
  sealed trait WithRepositories[CV <: RiddlValue] extends Container[CV]:

    /** A lazily constructed [[Seq]] of [[Repository]] filtered from the contents */
    def repositories: Seq[Repository] = contents.filter[Repository]
  end WithRepositories

  /** Base trait to use in any [[Definition]] that can define [[Entity]]s */
  sealed trait WithEntities[CV <: RiddlValue] extends Container[CV]:

    /** A lazily constructed [[Seq]] of [[Entity]] filtered from the contents */
    def entities: Seq[Entity] = contents.filter[Entity]
  end WithEntities

  /** Base trait to use in any [[Definition]] that can define [[Streamlet]]s */
  sealed trait WithStreamlets[CV <: RiddlValue] extends Container[CV]:

    /** A lazily constructed [[Seq]] of [[Streamlet]] filtered from the contents */
    def streamlets: Seq[Streamlet] = contents.filter[Streamlet]
  end WithStreamlets

  /** Base trait to use in any [[Definition]] that can define [[Connector]]s */
  sealed trait WithConnectors[CV <: RiddlValue] extends Container[CV]:

    /** A lazily constructed [[Seq]] of [[Connector]] filtered from the contents */
    def connectors: Seq[Connector] = contents.filter[Connector]
  end WithConnectors

  /** Base trait to use in any [[Definition]] that can define [[Adaptor]]s */
  sealed trait WithAdaptors[CV <: RiddlValue] extends Container[CV]:

    /** A lazily constructed [[Seq]] of [[Adaptor]] filtered from the contents */
    def adaptors: Seq[Adaptor] = contents.filter[Adaptor]
  end WithAdaptors

  /** Base trait to use in any [[Definition]] that can define [[Saga]]s */
  sealed trait WithSagas[CV <: RiddlValue] extends Container[CV]:

    /** A lazily constructed [[Seq]] of [[Saga]] filtered from the contents */
    def sagas: Seq[Saga] = contents.filter[Saga]
  end WithSagas

  /** Base trait to use in any [[Definition]] that can define [[SagaStep]]s */
  sealed trait WithSagaSteps[CV <: RiddlValue] extends Container[CV]:

    /** A lazily constructed [[Seq]] of [[SagaStep]] filtered from the contents */
    def sagaSteps: Seq[SagaStep] = contents.filter[SagaStep]
  end WithSagaSteps

  /** Base trait to use in any [[Definition]] that can define [[UseCase]]s */
  sealed trait WithUseCases[CV <: RiddlValue] extends Container[CV]:

    /** A lazily constructed [[Seq]] of [[UseCase]] filtered from the contents */
    def cases: Seq[UseCase] = contents.filter[UseCase]
  end WithUseCases

  /** Base trait to use in any [[Definition]] that can define [[ShownBy]]s */
  sealed trait WithShownBy[CV <: RiddlValue] extends Container[CV]:

    /** A lazily constructed [[Seq]] of [[ShownBy]] filtered from the contents */
    def shownBy: Seq[ShownBy] = contents.filter[ShownBy]
  end WithShownBy

  /** Base trait to use anywhere that can contain [[Module]]s */
  sealed trait WithModules[CV <: RiddlValue] extends Container[CV]:
    /** A lazily constructed [[Contents]] of [[Module]] */
    def modules: Seq[Module] = contents.filter[Module]
  end WithModules

  ///////////////////////////////////////////////////////////////////////////// ABSTRACT DEFINITIONS
  ///// This section defines various abstract things needed by the rest of the definitions

  /** The list of definitions to which a reference cannot be made */
  type NonReferencableDefinitions = Enumerator | Root | SagaStep | Term | Invariant

  /** THe list of RiddlValues that are not Definitions for excluding them in match statements */
  type NonDefinitionValues = LiteralString | Identifier | PathIdentifier | Description |
    Interaction | Include[?] | TypeExpression | Comment | Reference[?] | OptionValue |
    StreamletShape | AdaptorDirection | UserStory | MethodArgument | Schema | ShownBy |
    SimpleContainer[?] | BriefDescription | BlockDescription | URLDescription | FileAttachment |
    StringAttachment | ULIDAttachment | Meta | Statement

  /** Type of definitions that occur in a [[Root]] without [[Include]] */
  private type OccursInModule = Domain | Author | Comment

  /** Type of definitions that can occur in a [[Module]] */
  type ModuleContents = OccursInModule | Include[OccursInModule]

  /** The root is a module that can have other modules */
  type RootContents = ModuleContents | Module

  /** Things that can occur in the "With" section of a leaf definition */
  type MetaData =
    BriefDescription | Description | Term | AuthorRef | FileAttachment | StringAttachment |
      ULIDAttachment | Comment | OptionValue

  /** Type of definitions that occurs within all Vital Definitions */
  type OccursInVitalDefinition = Type | Comment

  /** Type of definitions that occur within all Processor types */
  type OccursInProcessor = OccursInVitalDefinition | Constant | Invariant | Function | Handler |
    Streamlet | Connector | Relationship

  /** Type of definitions that occur in a [[Domain]] without [[Include]] */
  type OccursInDomain = OccursInVitalDefinition | Author | Context | Domain | User | Epic | Saga

  /** Type of definitions that occur in a [[Domain]] with [[Include]] */
  type DomainContents = OccursInDomain | Include[OccursInDomain]

  /** Type of definitions that occur in a [[Context]] without [[Include]] */
  type OccursInContext = OccursInProcessor | Entity | Adaptor | Group | Saga | Projector |
    Repository

  /** Type of definitions that occur in a [[Context]] with [[Include]] */
  type ContextContents = OccursInContext | Include[OccursInContext]

  /** Type of definitions that occur in a [[Group]] */
  type OccursInGroup = Group | ContainedGroup | Input | Output

  /** Type of definitions that occur in an [[Input]] */
  type OccursInInput = Input | TypeRef

  /** Type of definitions that occur in an [[Output]] */
  type OccursInOutput = Output | TypeRef

  type GroupRelated = Group | Input | Output

  /** Type of definitions that occur in an [[Entity]] without [[Include]] */
  private type OccursInEntity = OccursInProcessor | State

  /** Type of definitions that occur in an [[Entity]] with [[Include]] */
  type EntityContents = OccursInEntity | Include[OccursInEntity]

  /** Type of definitions that occur in a [[Handler]] */
  type HandlerContents = OnClause | Comment

  /** Type of definitions that occur in an [[Adaptor]] without [[Include]] */
  private type OccursInAdaptor = OccursInProcessor

  /** Type of definitions that occur in an [[Adaptor]] with [[Include]] */
  type AdaptorContents = OccursInAdaptor | Include[OccursInAdaptor]

  /** Type of definitions that occur in a [[Saga]] without [[Include]] */
  private type OccursInSaga = OccursInVitalDefinition | SagaStep

  /** Type of definitions that occur in a [[Saga]] with [[Include]] */
  type SagaContents = OccursInSaga | Include[OccursInSaga]

  /** Type of definitions that occur in a [[Streamlet]] without [[Include]] */
  private type OccursInStreamlet = OccursInProcessor | Inlet | Outlet | Connector

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
  private type OccursInProjector = OccursInProcessor | RepositoryRef

  /** Type of definitions that occur in a [[Projector]] with [[Include]] */
  type ProjectorContents = OccursInProjector | Include[OccursInProjector]

  /** Type of definitions that occur in a [[Repository]] without [[Include]] */
  private type OccursInRepository = OccursInProcessor | Schema

  /** Type of definitions that occur in a [[Repository]] with [[Include]] */
  type RepositoryContents = OccursInRepository | Include[OccursInRepository]

  /** Type of definitions that occur in a [[Function]] */
  private type OccursInFunction = OccursInVitalDefinition | Statement | Function

  /** Type of definitions that occur in a [[Function]], with Include */
  type FunctionContents = OccursInFunction | Include[OccursInFunction]

  /** Type of definitions that occur in a [[Type]] */
  type TypeContents = Field | Method | Enumerator

  type AggregateContents = Field | Method | Comment

  /** Type of definitions that occur in a block of [[Statement]] */
  type Statements = Statement | Comment

  type NebulaContents = Adaptor | Author | Connector | Constant | Context | Domain | Entity | Epic |
    Function | Invariant | Module | Projector | Relationship | Repository | Saga | Streamlet |
    Type | User

  ///////////////////////////////////////////////////////////////////////////////////// DEFINITIONS
  //////// The Abstract classes for defining Definitions by using the foregoing traits

  /** Base trait for all Definitions. Their mere distinction at this level of abstraction is to
    * simply have an identifier and can have attachments
    *
    * @see
    *   [[Branch]] and [[Leaf]]
    */
  sealed trait Definition extends WithIdentifier:
    /** Yes anything deriving from here is a definition */
    override def isDefinition: Boolean = true
    override def isParent: Boolean = false
    override def hasDefinitions: Boolean = false
  end Definition

  object Definition:
    /** The canonical value for "empty" Definition which can usually be interpeted as "Not Found" */
    lazy val empty: Definition = new Definition {
      def id: Identifier = Identifier.empty
      def format: String = ""
      def loc: At = At.empty
      override def isEmpty: Boolean = true
    }
  end Definition

  /** The Base trait for a definition that contains some unrestricted kind of content, RiddlValue */
  sealed trait Branch[CV <: RiddlValue] extends Definition with Container[CV]:
    override def isParent: Boolean = true
    override def hasDefinitions: Boolean = !contents.isEmpty
    opaque type ContentType <: RiddlValue = CV
  end Branch

  /** A leaf node in the hierarchy of definitions. Leaves have no content, unlike [[Branch]]. They
    * do permit a single [[BriefDescription]] value and single [[Description]] value. There are no
    * contents.
    */
  sealed trait Leaf extends Definition with WithMetaData

  type Definitions = Seq[Definition] // TODO: Make this opaque some day

  object Definitions:
    def empty: Definitions = Seq.empty[Definition]
  end Definitions

  /** A simple sequence of Parents from the closest all the way up to the Root */
  type Parents = Seq[Branch[?]]

  object Parents:
    def empty[CV <: RiddlValue]: Parents = Seq.empty[Branch[?]]
    def apply(contents: Branch[?]*) = Seq(contents: _*)
  end Parents

  /** A mutable stack of Branch[?] for keeping track of the parent hierarchy */
  type ParentStack = mutable.Stack[Branch[?]] // TODO: Make this opaque some day

  /** Extension methods for the ParentStack type */
  extension (ps: ParentStack)
    /** Convert the mutable ParentStack into an immutable Parents Seq */
    def toParents: Parents = ps.toSeq
  end extension

  /** A Companion to the ParentStack class */
  object ParentStack:
    /** @return  an empty ParentStack */
    def empty[CV <: RiddlValue]: ParentStack = mutable.Stack.empty[Branch[?]]
    def apply(items: Branch[?]*): ParentStack = mutable.Stack(items: _*)
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
      with WithComments[CT]
      with WithMetaData:
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
      with WithStreamlets[CT]:
    final override def isProcessor: Boolean = true
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
      with WithIncludes[RootContents]:

    override def isRootContainer: Boolean = true

    override def id: Identifier = Identifier(loc, "Root")

    override def identify: String = "Root"

    override def identifyWithLoc: String = "Root"

    def format: String = "Root"
  end Root

  object Root:

    /** The value to use for an empty [[Root]] instance */
    val empty: Root = apply(At.empty, mutable.ArrayBuffer.empty[RootContents])
  end Root
  ////////////////////////////////////////////////////////////////////////////////////////// NEBULA

  /** The nubula of arbitrary definitions. This allows any named definition in its contents without
    * regard to intended structure of those things. This can be used as a general "scratchpad".
    *
    * @param contents
    *   The nebula of unrelated single definitions
    */
  case class Nebula(
    loc: At,
    contents: Contents[NebulaContents] = Contents.empty[NebulaContents]()
  ) extends Branch[NebulaContents]:
    override def isRootContainer: Boolean = false

    override def id: Identifier = Identifier(loc, "Nebula")

    override def identify: String = "Nebula"

    override def identifyWithLoc: String = "Nebula"

    def format: String = "Nebula"
  end Nebula

  object Nebula:

    /** The value to use for an empty [[Nebula]] instance */
    val empty: Nebula = Nebula(At.empty, Contents.empty[NebulaContents]())
  end Nebula

  ////////////////////////////////////////////////////////////////////////////////////////// MODULE

  /** A Module represents a */
  case class Module(
    loc: At,
    id: Identifier,
    contents: Contents[ModuleContents] = Contents.empty[ModuleContents](),
    metadata: Contents[MetaData] = Contents.empty[MetaData]()
  ) extends VitalDefinition[ModuleContents]
      with WithModules[ModuleContents]
      with WithDomains[ModuleContents]:
    def format: String = s"${Keyword.module} ${id.format}"
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
    * @param brief
    *   A brief description of the user
    * @param description
    *   A longer description of the user and its role
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
    * @param brief
    *   The short definition of the term
    * @param description
    *   The longer definition of the term.
    */
  case class Term(
    loc: At,
    id: Identifier,
    definition: Seq[LiteralString],
    metadata: Contents[MetaData] = Contents.empty[MetaData]()
  ) extends Leaf:
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
    * @param brief
    *   A optional short description of the author
    * @param description
    *   An optional long description of the author
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
  case class AuthorRef(loc: At, pathId: PathIdentifier) extends Reference[Author]:
    override def format: String = Keyword.author + " " + pathId.format
  end AuthorRef

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
        (other.getClass == classOf[Abstract]) ||
        (this.getClass == classOf[Abstract])
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
        case AggregateUseCaseTypeExpression(_, usecase, _) if usecase == useCase => true
        case _                                                                   => false
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
  ) extends Definition
      with WithMetaData:
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
    override def format: String = "{ " + enumerators
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
      s"one of { ${of.map(_.format).mkString(", ")} }"
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
    * @param brief
    *   A brief description (one sentence) for use in documentation
    * @param description
    *   An optional description of the field.
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
    * @param brief
    *   A brief description (one sentence) for use in documentation
    * @param description
    *   An optional description of the field.
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
    def fields: Seq[Field] = contents.filter[Field]

    /** Thelist of aggregated [[Method]] */
    def methods: Seq[Method] = contents.filter[Method]

    override def format: String = s"{ ${contents.map(_.format).mkString(", ")} }"
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
    */
  @JSExportTopLevel("AggregateUseCaseTypeExpression")
  case class AggregateUseCaseTypeExpression(
    loc: At,
    usecase: AggregateUseCase,
    contents: Contents[AggregateContents] = Contents.empty[AggregateContents]()
  ) extends AggregateTypeExpression(contents):
    override def format: String = usecase.useCase.toLowerCase() + " " + super.format
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
    override def format: String = {
      if min.isEmpty && max.isEmpty then kind
      else s"$kind(${min.getOrElse("")},${max.getOrElse("")})"
    }

    override def isAssignmentCompatible(other: TypeExpression): Boolean = {
      super.isAssignmentCompatible(other) || other.isInstanceOf[Pattern]
    }
  }

  /** A type expression for values that ensure a unique identifier for a specific entity.
    *
    * @param loc
    *   The location of the unique identifier type expression
    * @param entityPath
    *   The path identifier of the entity type
    */
  @JSExportTopLevel("UniqueId")
  case class UniqueId(loc: At, entityPath: PathIdentifier) extends PredefinedType {
    inline override def kind: String = "Id"

    override def format: String = s"$kind(${entityPath.format})"

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

  /** The simplest type expression: Abstract An abstract type expression is one that is not defined
    * explicitly. It is treated as a concrete type but without any structural or type information.
    * This is useful for types that are defined only at implementation time or for types whose
    * variations are so complicated they need to remain abstract at the specification level.
    * @param loc
    *   The location of the Bool type expression
    */
  @JSExportTopLevel("Abstract")
  case class Abstract(loc: At) extends PredefinedType {

    override def isAssignmentCompatible(other: TypeExpression): Boolean = true
  }

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
    inline override def kind: String = "Range"

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

  @JSExportTopLevel("ZonedDatTime")
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

  /** Base trait for the four kinds of message references */
  sealed trait MessageRef extends Reference[Type] {
    def messageKind: AggregateUseCase

    override def format: String =
      s"${messageKind.useCase.toLowerCase} ${pathId.format}"
  }

  @JSExportTopLevel("MessageRef")
  object MessageRef {
    lazy val empty: MessageRef = new MessageRef {
      def messageKind: AggregateUseCase = AggregateUseCase.RecordCase

      override def pathId: PathIdentifier = PathIdentifier.empty

      override def loc: At = At.empty
    }
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

  /** A reference to a record message type
    *
    * @param loc
    *   The location of the reference
    * @param pathId
    *   The path identifier to the result type
    */
  @JSExportTopLevel("RecordRef")
  case class RecordRef(
    loc: At,
    pathId: PathIdentifier
  ) extends MessageRef:
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
    * @param brief
    *   A brief description (one sentence) for use in documentation
    * @param description
    *   An optional description of the type.
    */
  @JSExportTopLevel("Type")
  case class Type(
    loc: At,
    id: Identifier,
    typEx: TypeExpression,
    metadata: Contents[MetaData] = Contents.empty[MetaData]()
  ) extends Branch[TypeContents]
      with WithMetaData:
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
        case AggregateUseCaseTypeExpression(_, useCase, _) => useCase.useCase
        case _                                             => "Type"
      }
    }

    def format: String = ""
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
    * @param brief
    *   A brief descriptin of the constant
    * @param description
    *   A detailed description of the constant
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

  ////////////////////////////////////////////////////////////////////////////////////// STATEMENTS

  /** Base trait of all Statements that can occur in [[OnClause]]s */
  sealed trait Statement extends RiddlValue

  /** A statement whose behavior is specified as a text string allowing an arbitrary action to be
    * specified handled by RIDDL's syntax.
    *
    * @param loc
    *   The location where the action occurs in the source
    * @param what
    *   The action to take (emitted as pseudo-code)
    */
  @JSExportTopLevel("ArbitraryStatement")
  case class ArbitraryStatement(
    loc: At,
    what: LiteralString
  ) extends Statement {
    override def kind: String = "Arbitrary Statement"
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

  /** A statement that changes the focus of input in an application to a specific group
    *
    * @param loc
    *   The location of the statement
    * @param group
    *   The group that is the target of the input focus
    */
  @JSExportTopLevel("FocusStatement")
  case class FocusStatement(
    loc: At,
    group: GroupRef
  ) extends Statement {
    override def kind: String = "Focus Statement"
    def format: String = s"focus on ${group.format}"
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
    field: FieldRef,
    value: LiteralString
  ) extends Statement {
    override def kind: String = "Set Statement"
    def format: String = s"set ${field.format} to ${value.format}"
  }

  /** A statement that returns a value from a function
    *
    * @param loc
    *   The location in the source of the publish action
    * @param value
    *   The value to be returned
    */
  @JSExportTopLevel("ReturnStatement")
  case class ReturnStatement(
    loc: At,
    value: LiteralString
  ) extends Statement {
    override def kind: String = "Return Statement"
    def format: String = s"return ${value.format}"
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
    msg: MessageRef,
    portlet: PortletRef[Portlet]
  ) extends Statement {
    override def kind: String = "Send Statement"
    def format: String = s"send ${msg.format} to ${portlet.format}"
  }

  /** A statement that replies in a handler to a query
    *
    * @param loc
    *   The location in the source of the publish action
    * @param message
    *   The message to be returned
    */
  @JSExportTopLevel("ReplyStatement")
  case class ReplyStatement(
    loc: At,
    message: MessageRef
  ) extends Statement {
    override def kind: String = "Reply Statement"
    def format: String = s"reply ${message.format}"
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
    value: MessageRef
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
    */
  @JSExportTopLevel("TellStatement")
  case class TellStatement(
    loc: At,
    msg: MessageRef,
    processorRef: ProcessorRef[Processor[?]]
  ) extends Statement {
    override def kind: String = "Tell Statement"
    def format: String = s"tell ${msg.format} to ${processorRef.format}"
  }

  /** A statement that calls a function
    *
    * @param loc
    *   The location of the statement in the model
    * @param func
    *   The function to be called
    */
  @JSExportTopLevel("CallStatement")
  case class CallStatement(
    loc: At,
    func: FunctionRef
  ) extends Statement {
    override def kind: String = "Call Statement"
    def format: String = s"call ${func.format}"
  }

  /** A statement that suggests looping over the contents of a field with a non-zero cardinality, an
    * Inlet or an outlet
    * @param loc
    *   The location of the statement in the model
    * @param ref
    *   The reference to the Field, outlet or Inlet
    * @param do_
    *   The set of statements to execute for each iteration_
    */
  @JSExportTopLevel("ForEachStatement")
  case class ForEachStatement(
    loc: At,
    ref: FieldRef | OutletRef | InletRef,
    do_ : Contents[Statements]
  ) extends Statement {
    override def kind: String = "Foreach Statement"
    def format: String = s"foreach ${ref.format} do\n" +
      do_.map(_.format).mkString("\n") + "\n  end"
  }

  /** A statement that represents a class if-condition-then-A-else-B construct for logic decitions.
    *
    * @param loc
    *   The location of the statement in the model
    * @param cond
    *   The conditional part of the if-then-else
    * @param thens
    *   The statements to execute if `cond` is true
    * @param elses
    *   The tsatements to execute if `cond` is false
    */
  @JSExportTopLevel("IfThenElseStatement")
  case class IfThenElseStatement(
    loc: At,
    cond: LiteralString,
    thens: Contents[Statements],
    elses: Contents[Statements]
  ) extends Statement {
    override def kind: String = "IfThenElse Statement"

    def format: String =
      s"if ${cond.format} then {${thens.map(_.format).mkString("\n  ", "\n  ", "\n}") +
          (" else {" + elses.map(_.format).mkString("\n  ", "\n  ", "\n}\nend"))}"
  }

  /** A statement that terminates the On Clause */
  @JSExportTopLevel("StopStatement")
  case class StopStatement(
    loc: At
  ) extends Statement {
    override def kind: String = "Stop Statement"
    def format: String = "stop"
  }

  /** A statement that reads data from a Repository
    *
    * @param loc
    *   The location of the statement in the model
    * @param keyword
    *   The keyword used to color the nature of the read operation
    * @param what
    *   A string describing what should be read
    * @param from
    *   A reference to the type from which the value should be read in the repository
    * @param where
    *   A string describing the conditions on the read (like a SQL WHERE clause)
    */
  @JSExportTopLevel("ReadStatement")
  case class ReadStatement(
    loc: At,
    keyword: String,
    what: LiteralString,
    from: TypeRef,
    where: LiteralString
  ) extends Statement {
    override def kind: String = "Read Statement"
    def format: String = s"$keyword ${what.format} from ${from.format} where ${where.format}"
  }

  /** A statement that describes a write to a repository
    *
    * @param loc
    *   The location of the statement in the model
    * @param keyword
    *   The keyword used to color the nature of teh write operation (e.g. update, append, etc.)
    * @param what
    *   A description of the data that should be written to the repository
    * @param to
    *   The [[TypeRef]] to the component of the Repository
    */
  @JSExportTopLevel("WriteStatement")
  case class WriteStatement(
    loc: At,
    keyword: String,
    what: LiteralString,
    to: TypeRef
  ) extends Statement {
    override def kind: String = "Write Statement"
    def format: String = s"$keyword ${what.format} to ${to.format}"
  }

  /** A statement that provides a definition of the computation to execute in a specific programming
    * language
    *
    * @param loc
    *   The location of the statement
    * @param language
    *   The name of the programming language in which the `body` is written
    * @param body
    *   The code that should be executed by this statement.
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
    metadata: Contents[MetaData] = Contents.empty[MetaData]()
  ) extends Processor[AdaptorContents]:
    def format: String = Keyword.adaptor + " " + id.format
  end Adaptor

  @JSExportTopLevel("AdaptorRef")
  case class AdaptorRef(loc: At, pathId: PathIdentifier) extends ProcessorRef[Adaptor] {
    override def format: String = Keyword.adaptor + " " + pathId.format
  }

  //////////////////////////////////////////////////////////////////////////////////////// FUNCTION

  /** A function definition which can be part of a bounded referent or an entity.
    *
    * @param loc
    *   The location of the function definition
    * @param id
    *   The identifier that names the function
    * @param input
    *   An optional type expression that names and types the fields of the input of the function
    * @param output
    *   An optional type expression that names and types the fields of the output of the function
    * @param contents
    *   The set of types, functions, statements, authors, includes and terms that define this
    *   FUnction
    * @param metadata
    *   The set of descriptive values for this function
    */
  @JSExportTopLevel("Function")
  case class Function(
    loc: At,
    id: Identifier,
    input: Option[Aggregation] = None,
    output: Option[Aggregation] = None,
    contents: Contents[FunctionContents] = Contents.empty[FunctionContents](),
    metadata: Contents[MetaData] = Contents.empty[MetaData]()
  ) extends VitalDefinition[FunctionContents]
      with WithTypes[FunctionContents]
      with WithFunctions[FunctionContents]
      with WithStatements[FunctionContents] {
    override def format: String = Keyword.function + " " + id.format
    final override def kind: String = "Function"
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
    *   The string representation of the condition that ought to be true
    * @param metadata
    *   The list of meta data for the invariant
    */
  @JSExportTopLevel("Invariant")
  case class Invariant(
    loc: At,
    id: Identifier,
    condition: Option[LiteralString] = Option.empty[LiteralString],
    metadata: Contents[MetaData] = Contents.empty[MetaData]()
  ) extends Leaf {
    override def isEmpty: Boolean = condition.isEmpty
    def format: String = Keyword.invariant + " " + id.format + condition.map(_.format)
  }

  /////////////////////////////////////////////////////////////////////////////////////// ON CLAUSE

  /** A sealed trait for the kinds of OnClause that can occur within a Handler definition.
    */
  sealed trait OnClause extends Branch[Statements] with WithStatements[Statements] with WithMetaData

  /** Defines the actions to be taken when a message does not match any of the OnMessageClauses.
    * OnOtherClause corresponds to the "other" case of an [[Handler]].
    *
    * @param loc
    *   THe location of the "on other" clause
    * @param contents
    *   A set of examples that define the behavior when a message doesn't match
    */
  @JSExportTopLevel("OnOtherClause")
  case class OnOtherClause(
    loc: At,
    contents: Contents[Statements] = Contents.empty[Statements](),
    metadata: Contents[MetaData] = Contents.empty[MetaData]()
  ) extends OnClause {
    def id: Identifier = Identifier(loc, s"pther")

    override def kind: String = "On Other"

    override def format: String = ""
  }

  /** Defines the actions to be taken when the component this OnClause occurs in is initialized.
    *
    * @param loc
    *   THe location of the "on other" clause
    * @param contents
    *   A set of statements that define the behavior when a message doesn't match
    */
  @JSExportTopLevel("OnInitializationClause")
  case class OnInitializationClause(
    loc: At,
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
    * @param contents
    *   A set of statements that define the behavior when the [[msg]] is received.
    */
  @JSExportTopLevel("OnMessageClause")
  case class OnMessageClause(
    loc: At,
    msg: MessageRef,
    from: Option[(Option[Identifier], Reference[Definition])],
    contents: Contents[Statements] = Contents.empty[Statements](),
    metadata: Contents[MetaData] = Contents.empty[MetaData]()
  ) extends OnClause {
    def id: Identifier = Identifier(msg.loc, msg.format)
    def format: String = ""
  }

  /** Defines the actions to be taken when the component this OnClause occurs in is initialized.
    *
    * @param loc
    *   THe location of the "on other" clause
    * @param contents
    *   A set of statements that define the behavior when a message doesn't match
    */
  @JSExportTopLevel("OnTerminationClause")
  case class OnTerminationClause(
    loc: At,
    contents: Contents[Statements] = Contents.empty[Statements](),
    metadata: Contents[MetaData] = Contents.empty[MetaData]()
  ) extends OnClause {
    def id: Identifier = Identifier(loc, s"term")

    override def kind: String = "On Term"

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
    metadata: Contents[MetaData] = Contents.empty[MetaData]()
  ) extends Branch[HandlerContents]
      with WithMetaData {
    override def isEmpty: Boolean = clauses.isEmpty

    def clauses: Seq[OnClause] = contents.filter[OnClause]

    def format: String = s"${Keyword.handler} ${id.format}"
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
    typ: TypeRef,
    metadata: Contents[MetaData] = Contents.empty[MetaData]()
  ) extends Leaf:
    def format: String = Keyword.state + " " + id.format
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

  /** Definition of an Entity
    *
    * @param loc
    *   The location in the input
    * @param id
    *   The name of the entity
    * @param contents
    *   The definitional content of this entity: handlers, states, functions, invariants, etc.
    */
  @JSExportTopLevel("Entity")
  case class Entity(
    loc: At,
    id: Identifier,
    contents: Contents[EntityContents] = Contents.empty[EntityContents](),
    metadata: Contents[MetaData] = Contents.empty[MetaData]()
  ) extends Processor[EntityContents]
      with WithStates[EntityContents]:
    override def format: String = Keyword.entity + " " + id.format
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
    metadata: Contents[MetaData] = Contents.empty[MetaData]()
  ) extends Processor[RepositoryContents]:
    def format: String = Keyword.entity + " " + id.format
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
  @JSExportTopLevel("Projector")
  case class Projector(
    loc: At,
    id: Identifier,
    contents: Contents[ProjectorContents] = Contents.empty[ProjectorContents](),
    metadata: Contents[MetaData] = Contents.empty[MetaData]()
  ) extends Processor[ProjectorContents]:
    def repositories: Seq[RepositoryRef] = contents.filter[RepositoryRef]
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
  @JSExportTopLevel("Context")
  case class Context(
    loc: At,
    id: Identifier,
    contents: Contents[ContextContents] = Contents.empty[ContextContents](),
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
    def format: String = Keyword.context + " " + id.format
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
    * @param brief
    *   A brief description (one sentence) for use in documentation
    * @param description
    *   An optional description of the Inlet
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
    * @param brief
    *   A brief description (one sentence) for use in documentation
    * @param description
    *   An optional description of the Outlet.
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
    * @param options
    *   The options for this connector
    * @param metadata
    *   The meta data for this connector
    */
  @JSExportTopLevel("Connector")
  case class Connector(
    loc: At,
    id: Identifier,
    from: OutletRef,
    to: InletRef,
    metadata: Contents[MetaData] = Contents.empty[MetaData]()
  ) extends Leaf {
    override def format: String = Keyword.connector + " " + id.format

    override def isEmpty: Boolean = super.isEmpty && from.isEmpty && to.isEmpty
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

  /** Definition of a Streamlet. A computing element for processing data from [[Inlet]]s to
    * [[Outlet]]s. A processor's processing is specified by free text statements in [[Handler]]s.
    * Streamlets come in various shapes: Source, Sink, Flow, Merge, Split, and Router depending on
    * how many inlets and outlets they have
    *
    * @param loc
    *   The location of the Processor definition
    * @param id
    *   The name of the processor
    * @param shape
    *   The shape of the processor's inputs and outputs
    * @param contents
    *   The definitional content for this Context
    */
  @JSExportTopLevel("Streamlet")
  case class Streamlet(
    loc: At,
    id: Identifier,
    shape: StreamletShape,
    contents: Contents[StreamletContents] = Contents.empty[StreamletContents](),
    metadata: Contents[MetaData] = Contents.empty[MetaData]()
  ) extends Processor[StreamletContents]
      with WithInlets[StreamletContents]
      with WithOutlets[StreamletContents] {
    final override def kind: String = shape.getClass.getSimpleName
    def format: String = shape.keyword + " " + id.format

    shape match {
      case Source(_) =>
        require(
          contents.isEmpty || (outlets.size == 1 && inlets.isEmpty),
          s"Invalid Source Streamlet ins: ${outlets.size} == 1, ${inlets.size} == 0"
        )
      case Sink(_) =>
        require(
          contents.isEmpty || (outlets.isEmpty && inlets.size == 1),
          "Invalid Sink Streamlet"
        )
      case Flow(_) =>
        require(
          contents.isEmpty || (outlets.size == 1 && inlets.size == 1),
          "Invalid Flow Streamlet"
        )
      case Merge(_) =>
        require(
          contents.isEmpty || (outlets.size == 1 && inlets.size >= 2),
          "Invalid Merge Streamlet"
        )
      case Split(_) =>
        require(
          contents.isEmpty || (outlets.size >= 2 && inlets.size == 1),
          "Invalid Split Streamlet"
        )
      case Router(_) =>
        require(
          contents.isEmpty || (outlets.size >= 2 && inlets.size >= 2),
          "Invalid Router Streamlet"
        )
      case Void(_) =>
        require(
          contents.isEmpty || (outlets.isEmpty && inlets.isEmpty),
          "Invalid Void Stream"
        )
    }

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
    * @param brief
    *   A brief description (one sentence) for use in documentation
    * @param description
    *   An optional description of the saga action
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
    * @param input
    *   A definition of the aggregate input values needed to invoke the saga, if any.
    * @param output
    *   A definition of the aggregate output values resulting from invoking the saga, if any.
    * @param contents
    *   The definitional content for this Context
    */
  @JSExportTopLevel("Saga")
  case class Saga(
    loc: At,
    id: Identifier,
    input: Option[Aggregation] = None,
    output: Option[Aggregation] = None,
    contents: Contents[SagaContents] = Contents.empty[SagaContents](),
    metadata: Contents[MetaData] = Contents.empty[MetaData]()
  ) extends VitalDefinition[SagaContents]
      with WithSagaSteps[SagaContents] {
    override def format: String = Keyword.saga + " " + id.format
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
    * @param brief
    *   A brief description of the parallel group
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
    * @param brief
    *   A brief description of the sequence group
    * @param description
    *   A longer description of the sequence
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
    * @param brief
    *   A brief description of the optional group
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

  /** An [[GenericInteraction]] that is vaguely written as a textual description */
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
    * @param brief
    *   A brief description of this interaction
    * @param description
    *   A full description of this interaction
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
    * @param brief
    *   A brief description of the interaction step
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
    * @param brief
    *   A brief description of the interaction for documentation purposes
    * @param description
    *   A full description of the interaction for documentation purposes
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
    * @param brief
    *   A brief description of this interaction
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
    * @param brief
    *   A brief description for documentation purposes
    * @param description
    *   A more full description of the interaction for documentation purposes
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
    *   THe user that receives the output
    * @param brief
    *   A brief description of this interaction
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
    * @param brief
    *   A description of this interaction step
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
    * @param brief
    *   A description of this interaction step
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
  ) extends Branch[UseCaseContents]
      with WithMetaData {
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
      with WithOutputs[OccursInGroup]
      with WithMetaData:
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
      with WithOutputs[OccursInOutput]
      with WithMetaData:
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
      with WithInputs[OccursInInput]
      with WithMetaData:
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
    * @param defn
    *   The definition whose [[AST.Author]]s we are seeking
    * @param parents
    *   The parents of the definition whose [[AST.Author]]s we are seeking
    * @return
    *   The list of [[AST.Author]]s of definition
    */
  @JSExport
  def findAuthors(
    definition: WithMetaData,
    parents: Contents[RiddlValue]
  ): Seq[AuthorRef] =
    val result = definition.authorRefs
    if result.isEmpty then parents.filter[WithMetaData].flatMap(_.authorRefs)
    else result
    end if
  end findAuthors

  /** Get all the top level domain definitions even if they are in include statements
    * @param root
    *   The model's [[AST.Root]] node.
    * @return
    *   A Seq of [[AST.Domain]]s as a [[AST.Contents]] extension
    */
  @JSExport
  def getTopLevelDomains(root: Root): Seq[Domain] = {
    root.domains ++ root.includes.flatMap(_.contents.filter[Domain])
  }

  /** Get all the first level nested domains of a domain even if they are in include statements
    * @param domain
    *   The parent [[AST.Domain]] whose subdomains will be returned
    * @return
    *   The subdomains of the provided domain as a [[AST.Contents]] extension
    */
  @JSExport
  def getDomains(domain: Domain): Seq[Domain] = {
    domain.domains ++ domain.includes.flatMap(_.contents.filter[Domain])
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
    *   A Seq of Context expressed as a [[AST.Contents]] extension
    */
  @JSExport
  def getContexts(domain: Domain): Seq[Context] = {
    domain.contexts ++ domain.includes.flatMap(_.contents.filter[Context])
  }

  /** get all the epics defined in a domain even if they are in includes of that domain
    *
    * @param domain
    *   The domain to examine for epics
    * @return
    *   A [[scala.Seq]] of [[AST.Epic]] expressed as a [[AST.Contents]] extension
    */
  @JSExport
  def getEpics(domain: Domain): Seq[Epic] = {
    domain.epics ++ domain.includes.flatMap(_.contents.filter[Epic])
  }

  /** get all the entities defined in a referent even if they are in includes of that domain
    *
    * @param context
    *   The domain to examine for entities
    * @return
    *   A Seq of [[AST.Entity]] expressed as a [[AST.Contents]] extension
    */
  @JSExport
  def getEntities(context: Context): Seq[Entity] = {
    context.entities ++ context.includes.flatMap(_.contents.filter[Entity])
  }

  /** get all the authors defined in a domain even if they are in includes of that domain
    *
    * @param domain
    *   The domain to examine for authors
    * @return
    *   A Seq of [[AST.Author]] expressed as a [[AST.Contents]] extension
    */
  @JSExport
  def getAuthors(domain: Domain): Seq[Author] = {
    val nested = domain.includes.flatMap(_.contents.filter[Author])
    domain.authors ++ domain.domains.flatMap(getAuthors) ++ nested
  }

  /** get all the authors defined in the root node even if they are in includes
    *
    * @param root
    *   The domain to examine for entities
    * @return
    *   A Seq of [[AST.Author]] expressed as a [[AST.Contents]] extension
    */
  @JSExport
  def getAuthors(root: Root): Seq[Author] = {
    root.domains.flatMap(getAuthors)
  }

  /** get all the [[Author]]s defined in a [[Domain]] node even if they are in includes
    *
    * @param domain
    *   The domain to examine for entities
    * @return
    *   A Seq of [[AST.Author]] expressed as a [[AST.Contents]] extension
    */
  @JSExport
  def getUsers(domain: Domain): Seq[User] = {
    val nested = domain.includes.flatMap(_.contents.filter[User])
    domain.users ++ domain.domains.flatMap(getUsers) ++ nested
  }

  /** Get the [[AST.User]] definitions found at the [[AST.Root]] level or in its [[AST.Include]]s
    * @param root
    *   The [[AST.Root]] node to examine
    * @return
    *   A Seq of [[AST.User]] expressed as a [[AST.Contents]] extension
    */
  @JSExport
  def getUsers(root: Root): Seq[User] = {
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
      case RangeType(_, min, max)  => s"Range($min,$max)"
      case UniqueId(_, entityPath) => s"Id(${entityPath.format})"
      case m @ AggregateUseCaseTypeExpression(_, messageKind, _) =>
        s"${messageKind.useCase} of ${m.fields.size} fields and ${m.methods.size} methods"
      case pt: PredefinedType => pt.kind
    end match
  end errorDescription
end AST
