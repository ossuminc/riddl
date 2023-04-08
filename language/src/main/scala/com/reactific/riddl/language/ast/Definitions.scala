package com.reactific.riddl.language.ast

import com.reactific.riddl.language.parsing.RiddlParserInput
import com.reactific.riddl.language.parsing.Terminals.Keywords

/** Unit Tests For Definitions */
trait Definitions extends Expressions with Options {

  /** Base trait of any definition that is in the content of an adaptor
    */
  sealed trait AdaptorDefinition extends Definition

  /** Base trait of any definition that is in the content of an Application
    */
  sealed trait ApplicationDefinition extends Definition

  /** Base trait of any definition that is in the content of a context
    */
  sealed trait ContextDefinition extends Definition

  /** Base trait of any definition that is in the content of a domain
    */
  sealed trait DomainDefinition extends Definition

  /** Base trait of any definition that is in the content of an entity.
    */
  sealed trait EntityDefinition extends Definition

  /** Base trait of definitions that are part of a Handler Definition */
  sealed trait HandlerDefinition extends Definition

  /** Base trait of definitions that are part of an On Clause Definition */
  sealed trait OnClauseDefinition extends Definition

  /** Base trait of definitions defined in a processor */
  sealed trait ProcessorDefinition
      extends Definition
      with AdaptorDefinition
      with ApplicationDefinition
      with ContextDefinition
      with EntityDefinition
      with ProjectorDefinition
      with RepositoryDefinition
      with StreamletDefinition
      with SagaDefinition

  /** Base trait of definitions defined in a repository */
  sealed trait RepositoryDefinition extends Definition

  /** Base trait of definitions defined at root scope */
  sealed trait RootDefinition extends Definition

  /** Base trait of definitions define within a Streamlet */
  sealed trait StreamletDefinition extends Definition

  /** Base trait of definitions that are in the body of a Story definition */
  sealed trait EpicDefinition extends Definition

  sealed trait VitalDefinitionDefinition
      extends AdaptorDefinition
      with ApplicationDefinition
      with ContextDefinition
      with DomainDefinition
      with EntityDefinition
      with FunctionDefinition
      with StreamletDefinition
      with ProjectorDefinition
      with RepositoryDefinition
      with SagaDefinition
      with EpicDefinition

  /** Base trait of definitions that can accept a message directly via a
    * reference
    * @tparam T
    *   The kind of reference needed
    */
  trait ProcessorRef[+T <: Processor[?, ?]] extends Reference[T]

  /** A term definition for the glossary */
  case class Term(
    loc: At,
    id: Identifier,
    brief: Option[LiteralString] = None,
    description: Option[Description] = None
  ) extends LeafDefinition
      with VitalDefinitionDefinition {
    override def isEmpty: Boolean = description.isEmpty

    def format: String = s"${Keywords.term} ${id.format}"

    final val kind: String = "Term"
  }

  /** Added to definitions that support a list of term definitions */
  trait WithTerms {
    def terms: Seq[Term]

    def hasTerms: Boolean = terms.nonEmpty
  }

  /** A [[RiddlValue]] to record an inclusion of a file while parsing.
    *
    * @param loc
    *   The location of the include statement in the source
    * @param contents
    *   The Vital Definitions read from the file
    * @param source
    *   A string providing the source (path or URL) of the included source
    */
  case class Include[T <: Definition](
    loc: At = At(RiddlParserInput.empty),
    contents: Seq[T] = Seq.empty[T],
    source: Option[String] = None
  ) extends Definition
      with VitalDefinitionDefinition
      with RootDefinition {

    def id: Identifier = Identifier.empty

    def brief: Option[LiteralString] = Option.empty[LiteralString]

    def description: Option[Description] = None

    override def isRootContainer: Boolean = true

    def format: String = ""

    final val kind: String = "Include"
  }

  /** Added to definitions that support includes */
  trait WithIncludes[T <: Definition] extends Container[T] {
    def includes: Seq[Include[T]]

    def contents: Seq[T] = {
      includes.flatMap(_.contents)
    }
  }

  /** A [[RiddlValue]] that holds the author's information
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
    */
  case class Author(
    loc: At,
    id: Identifier,
    name: LiteralString,
    email: LiteralString,
    organization: Option[LiteralString] = None,
    title: Option[LiteralString] = None,
    url: Option[java.net.URL] = None,
    brief: Option[LiteralString] = None,
    description: Option[Description] = None
  ) extends LeafDefinition
      with RootDefinition
      with DomainDefinition {
    override def isEmpty: Boolean = {
      name.isEmpty && email.isEmpty && organization.isEmpty && title.isEmpty
    }

    final val kind: String = "Author"

    def format: String = s"${Keywords.author} ${id.format}"
  }

  case class AuthorRef(loc: At, pathId: PathIdentifier)
      extends Reference[Author] {
    override def format: String = s"${Keywords.author} ${pathId.format}"

    def kind: String = ""
  }

  trait WithAuthors extends Definition {
    def authors: Seq[AuthorRef]

    override def hasAuthors: Boolean = authors.nonEmpty
  }

  sealed trait VitalDefinition[OPT <: OptionValue, DEF <: Definition]
      extends Definition
      with WithOptions[OPT]
      with WithAuthors
      with WithIncludes[DEF]
      with WithTerms {

    import scala.language.implicitConversions

    /** Implicit conversion of boolean to Int for easier computation of
      * statistics below
      *
      * @param b
      *   The boolean to convert to an Int
      * @return
      */
    implicit def bool2int(b: Boolean): Int = if (b) 1 else 0

    /** Compute the completeness of this definition. Vital definitions should
      * have options, terms, and authors but includes are optional.
      * Incompleteness is signalled by child definitions that are empty.
      *
      * @return
      *   A numerator and denominator for percent complete
      */
    def completeness: (Int, Int) = {
      // TODO: make subclass implementations
      (hasOptions * 1 + hasTerms + hasAuthors + brief.nonEmpty +
        description.nonEmpty) -> 5
    }

    /** Compute the 'maturity' of a definition. Maturity is a score with no
      * maximum but with scoring rules that target 100 points per definition.
      * Maturity is broken down this way:
      *   - has a description - up to 50 points depending on # of non empty
      *     lines
      *   - has a brief description - 5 points
      *   - has options specified - 5 points
      *   - has terms defined -
      *   - has an author in or above the definition - 5 points \-
      *   - definition specific things: 0.65
      *
      * @return
      */
    def maturity: Int = {
      var score = 0
      if (hasOptions) score += 5
      if (hasTerms) score += 5
      if (description.nonEmpty) {
        score += 5 + Math.max(description.get.lines.count(_.nonEmpty), 50)
      }
      if (brief.nonEmpty) score += 5
      if (includes.nonEmpty) score += 3
      score += authors.size
      score
    }

    override def isVital: Boolean = true
  }

  final val maxMaturity = 100

  /** Base trait of any definition that is a container and contains types
    */
  trait WithTypes extends Definition {
    def types: Seq[Type]

    override def hasTypes: Boolean = types.nonEmpty
  }

  /** Definition of a Processor. This is a base class for all Processor
    * definitions (things that have inlets, outlets, handlers, and take messages
    * directly with a reference).
    */
  trait Processor[OPT <: OptionValue, DEF <: Definition]
      extends VitalDefinition[OPT, DEF]
      with WithTypes {

    def handlers: Seq[Handler]

    def inlets: Seq[Inlet]

    def outlets: Seq[Outlet]
  }

  /** The root of the containment hierarchy, corresponding roughly to a level
    * about a file.
    *
    * @param contents
    *   The sequence of domains contained by this root container
    */
  case class RootContainer(
    domains: Seq[Domain] = Nil,
    authors: Seq[Author] = Nil,
    inputs: Seq[RiddlParserInput] = Nil
  ) extends Definition {

    override def isRootContainer: Boolean = true

    def loc: At = At.empty

    override def id: Identifier = Identifier(loc, "Root")

    override def identify: String = "Root"

    override def identifyWithLoc: String = "Root"

    override def description: Option[Description] = None

    override def brief: Option[LiteralString] = None

    final val kind: String = "Root"

    def format: String = ""

    def contents: Seq[Definition] = domains ++ authors
  }

  object RootContainer {
    def apply(domains: Seq[Domain], inputs: Seq[RiddlParserInput]): RootContainer = {
      RootContainer(domains, Seq.empty[Author], inputs)
    }
    val empty: RootContainer =
      RootContainer(Seq.empty[Domain], Seq.empty[Author], Seq.empty[RiddlParserInput])
  }

  /** Base trait for the four kinds of message references */
  sealed trait MessageRef extends Reference[Type] {
    def messageKind: AggregateUseCase

    override def format: String =
      s"${messageKind.kind.toLowerCase} ${pathId.format}"
  }

  object MessageRef {
    lazy val empty: MessageRef = new MessageRef {
      def messageKind: AggregateUseCase = RecordCase

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
  case class CommandRef(loc: At, pathId: PathIdentifier) extends MessageRef {
    def messageKind: AggregateUseCase = CommandCase
  }

  /** A Reference to an event message type
    *
    * @param loc
    *   The location of the reference
    * @param pathId
    *   The path identifier to the event type
    */
  case class EventRef(loc: At, pathId: PathIdentifier) extends MessageRef {
    def messageKind: AggregateUseCase = EventCase
  }

  /** A reference to a query message type
    *
    * @param loc
    *   The location of the reference
    * @param pathId
    *   The path identifier to the query type
    */
  case class QueryRef(loc: At, pathId: PathIdentifier) extends MessageRef {
    def messageKind: AggregateUseCase = QueryCase
  }

  /** A reference to a result message type
    *
    * @param loc
    *   The location of the reference
    * @param pathId
    *   The path identifier to the result type
    */
  case class ResultRef(loc: At, pathId: PathIdentifier) extends MessageRef {
    def messageKind: AggregateUseCase = ResultCase
  }

  /** A reference to a record message type
    *
    * @param loc
    *   The location of the reference
    * @param pathId
    *   The path identifier to the result type
    */
  case class RecordRef(
    loc: At = At.empty,
    pathId: PathIdentifier = PathIdentifier.empty
  ) extends MessageRef {
    def messageKind: AggregateUseCase = RecordCase
    override def isEmpty: Boolean =
      super.isEmpty && loc.isEmpty && pathId.isEmpty
  }

  /** A definition that represents a constant value for reference in behaviors
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
  case class Constant(
    loc: At,
    id: Identifier,
    typeEx: TypeExpression,
    value: Expression,
    brief: Option[LiteralString],
    description: Option[Description]
  ) extends LeafDefinition
      with ProcessorDefinition
      with DomainDefinition {
    override def kind: String = "Constant"

    /** Format the node to a string */
    override def format: String =
      s"${Keywords.const} ${id.format} is ${typeEx.format} = ${value.format}"
  }

  /** A type definition which associates an identifier with a type expression.
    *
    * @param loc
    *   The location of the type definition
    * @param id
    *   The name of the type being defined
    * @param typ
    *   The type expression of the type being defined
    * @param brief
    *   A brief description (one sentence) for use in documentation
    * @param description
    *   An optional description of the type.
    */
  case class Type(
    loc: At,
    id: Identifier,
    typ: TypeExpression,
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None
  ) extends Definition
      with StateDefinition
      with ProcessorDefinition
      with FunctionDefinition
      with DomainDefinition {
    override def contents: Seq[TypeDefinition] = {
      typ match {
        case Aggregation(_, fields)                       => fields
        case Enumeration(_, enumerators)                  => enumerators
        case AggregateUseCaseTypeExpression(_, _, fields) => fields
        case _ => Seq.empty[TypeDefinition]
      }
    }

    final val kind: String = {
      typ match {
        case AggregateUseCaseTypeExpression(_, useCase, _) => useCase.kind
        case _                                             => "Type"
      }
    }

    def format: String = ""
  }

  /** A reference to a type definition
    *
    * @param loc
    *   The location in the source where the reference to the type is made
    * @param pathId
    *   The path identifier of the reference type
    */
  case class TypeRef(
    loc: At = At.empty,
    pathId: PathIdentifier = PathIdentifier.empty
  ) extends Reference[Type] {
    override def format: String = s"${Keywords.`type`} ${pathId.format}"
  }

  // ////////////////////////////////////////////////////////// Gherkin

  /** A GherkinClause for the Given part of a Gherkin [[Example]]
    *
    * @param loc
    *   The location of the Given clause
    * @param scenario
    *   The strings that define the scenario
    */
  case class GivenClause(loc: At, scenario: Seq[LiteralString])
      extends GherkinClause {
    def format: String = ""
  }

  /** A [[GherkinClause]] for the When part of a Gherkin [[Example]]
    *
    * @param loc
    *   The location of the When clause
    * @param condition
    *   The condition expression that defines the trigger for the [[Example]]
    */
  case class WhenClause(loc: At, condition: Condition) extends GherkinClause {
    def format: String = ""
  }

  /** A [[GherkinClause]] for the Then part of a Gherkin [[Example]]. This part
    * specifies what should be done if the [[WhenClause]] evaluates to true.
    *
    * @param loc
    *   The location of the Then clause
    * @param action
    *   The action to be performed
    */
  case class ThenClause(loc: At, action: Action) extends GherkinClause {
    def format: String = ""
  }

  /** A [[GherkinClause]] for the But part of a Gherkin [[Example]]. This part
    * specifies what should be done if the [[WhenClause]] evaluates to false.
    *
    * @param loc
    *   The location of the But clause
    * @param action
    *   The action to be performed
    */
  case class ButClause(loc: At, action: Action) extends GherkinClause {
    def format: String = ""
  }

  /** A Gherkin example. Examples have names, [[id]], and a sequence of each of
    * the four kinds of Gherkin clauses: [[GivenClause]], [[WhenClause]],
    * [[ThenClause]], [[ButClause]]
    *
    * @see
    *   [[https://cucumber.io/docs/gherkin/reference/ The Gherkin Reference]]
    * @param loc
    *   The location of the start of the example
    * @param id
    *   The name of the example
    * @param givens
    *   The list of Given/And statements
    * @param whens
    *   The list of When/And statements
    * @param thens
    *   The list of Then/And statements
    * @param buts
    *   The List of But/And statements
    * @param brief
    *   A brief description (one sentence) for use in documentation
    * @param description
    *   An optional description of the example
    */
  case class Example(
    loc: At,
    id: Identifier,
    givens: Seq[GivenClause] = Seq.empty[GivenClause],
    whens: Seq[WhenClause] = Seq.empty[WhenClause],
    thens: Seq[ThenClause] = Seq.empty[ThenClause],
    buts: Seq[ButClause] = Seq.empty[ButClause],
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = Option.empty[Description]
  ) extends LeafDefinition
      with OnClauseDefinition
      with FunctionDefinition
      with EpicDefinition {
    final val kind: String = "Example"

    def format: String = ""

    override def isEmpty: Boolean = givens.isEmpty && whens.isEmpty &&
      thens.isEmpty && buts.isEmpty
  }

  // ////////////////////////////////////////////////////////// Entities

  /** A reference to an entity
    *
    * @param loc
    *   The location of the entity reference
    * @param pathId
    *   The path identifier of the referenced entity.
    */
  case class EntityRef(loc: At, pathId: PathIdentifier)
      extends ProcessorRef[Entity] {
    override def format: String = s"${Keywords.entity} ${pathId.format}"
  }

  /** A reference to a function.
    *
    * @param loc
    *   The location of the function reference.
    * @param pathId
    *   The path identifier of the referenced function.
    */
  case class FunctionRef(loc: At, pathId: PathIdentifier)
      extends Reference[Function] {
    override def format: String = s"${Keywords.function} ${pathId.format}"
  }

  /** A function definition which can be part of a bounded context or an entity.
    *
    * @param loc
    *   The location of the function definition
    * @param id
    *   The identifier that names the function
    * @param input
    *   An optional type expression that names and types the fields of the input
    *   of the function
    * @param output
    *   An optional type expression that names and types the fields of the
    *   output of the function
    * @param examples
    *   The set of examples that define the behavior of the function.
    * @param brief
    *   A brief description (one sentence) for use in documentation
    * @param description
    *   An optional description of the function.
    */
  case class Function(
    loc: At,
    id: Identifier,
    input: Option[Aggregation] = None,
    output: Option[Aggregation] = None,
    types: Seq[Type] = Seq.empty[Type],
    functions: Seq[Function] = Seq.empty[Function],
    examples: Seq[Example] = Seq.empty[Example],
    authors: Seq[AuthorRef] = Seq.empty[AuthorRef],
    includes: Seq[Include[FunctionDefinition]] = Seq
      .empty[Include[FunctionDefinition]],
    options: Seq[FunctionOption] = Seq.empty[FunctionOption],
    terms: Seq[Term] = Seq.empty[Term],
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None
  ) extends VitalDefinition[FunctionOption, FunctionDefinition]
      with WithTypes
      with SagaDefinition
      with EntityDefinition
      with ContextDefinition
      with FunctionDefinition {
    override lazy val contents: Seq[FunctionDefinition] = {
      super.contents ++ input.map(_.fields).getOrElse(Seq.empty[Field]) ++
        output.map(_.fields).getOrElse(Seq.empty[Field]) ++ types ++
        functions ++ examples
    }

    override def isEmpty: Boolean = examples.isEmpty && input.isEmpty &&
      output.isEmpty

    final val kind: String = "Function"

    override def maturity: Int = {
      var score = super.maturity
      if (input.nonEmpty) score += 2
      if (output.nonEmpty) score += 3
      if (types.nonEmpty) score += Math.max(types.count(_.nonEmpty), 13)
      if (examples.nonEmpty) score += Math.max(types.count(_.nonEmpty), 25)
      if (functions.nonEmpty) score += Math.max(functions.count(_.nonEmpty), 12)
      Math.max(score, maxMaturity)
    }
  }

  /** An invariant expression that can be used in the definition of an entity.
    * Invariants provide conditional expressions that must be true at all times
    * in the lifecycle of an entity.
    *
    * @param loc
    *   The location of the invariant definition
    * @param id
    *   The name of the invariant
    * @param expression
    *   The conditional expression that must always be true.
    * @param brief
    *   A brief description (one sentence) for use in documentation
    * @param description
    *   An optional description of the invariant.
    */
  case class Invariant(
    loc: At,
    id: Identifier,
    expression: Option[Condition] = None,
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None
  ) extends LeafDefinition
      with EntityDefinition
      with ProjectorDefinition
      with StateDefinition {
    override def isEmpty: Boolean = expression.isEmpty

    def format: String = ""

    final val kind: String = "Invariant"
  }

  /** A sealed trait for the kinds of OnClause that can occur within a Handler
    * definition.
    */
  sealed trait OnClause extends HandlerDefinition {
    def examples: Seq[Example]
  }

  /** Defines the actions to be taken when a message does not match any of the
    * OnMessageClauses. OnOtherClause corresponds to the "other" case of an
    * [[Handler]].
    *
    * @param loc
    *   THe location of the "on other" clause
    * @param examples
    *   A set of examples that define the behavior when a message doesn't match
    * @param brief
    *   A brief description (one sentence) for use in documentation
    * @param description
    *   An optional description of the on clause.
    */
  case class OnOtherClause(
    loc: At,
    examples: Seq[Example] = Seq.empty[Example],
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None
  ) extends OnClause {
    def id: Identifier = Identifier(loc, s"Other")

    override def isEmpty: Boolean = examples.isEmpty

    override def kind: String = "On Other"

    override def contents: Seq[Example] = examples

    override def format: String = ""
  }

  /** Defines the actions to be taken when the component this OnClause occurs in
    * is initialized.
    *
    * @param loc
    *   THe location of the "on other" clause
    * @param examples
    *   A set of examples that define the behavior when a message doesn't match
    * @param brief
    *   A brief description (one sentence) for use in documentation
    * @param description
    *   An optional description of the on clause.
    */
  case class OnInitClause(
    loc: At,
    examples: Seq[Example] = Seq.empty[Example],
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None
  ) extends OnClause {
    def id: Identifier = Identifier(loc, s"Init")

    override def isEmpty: Boolean = examples.isEmpty

    override def kind: String = "On Init"

    override def contents: Seq[Example] = examples

    override def format: String = ""
  }

  /** Defines the actions to be taken when a particular message is received by
    * an entity. [[OnMessageClause]]s are used in the definition of a
    * [[Handler]] with one for each kind of message that handler deals with.
    *
    * @param loc
    *   The location of the "on" clause
    * @param msg
    *   A reference to the message type that is handled
    * @param from
    *   Optional message generating
    * @param examples
    *   A set of examples that define the behavior when the [[msg]] is received.
    * @param brief
    *   A brief description (one sentence) for use in documentation
    * @param description
    *   An optional description of the on clause.
    */
  case class OnMessageClause(
    loc: At,
    msg: MessageRef,
    from: Option[Reference[Definition]],
    examples: Seq[Example] = Seq.empty[Example],
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None
  ) extends OnClause {
    def id: Identifier = Identifier(msg.loc, s"On ${msg.format}")

    override def isEmpty: Boolean = examples.isEmpty

    override def contents: Seq[Example] = examples

    def format: String = ""

    final val kind: String = "OnMessageClause"
  }

  /** Defines the actions to be taken when the component this OnClause occurs in
    * is initialized.
    *
    * @param loc
    *   THe location of the "on other" clause
    * @param examples
    *   A set of examples that define the behavior when a message doesn't match
    * @param brief
    *   A brief description (one sentence) for use in documentation
    * @param description
    *   An optional description of the on clause.
    */
  case class OnTermClause(
    loc: At,
    examples: Seq[Example] = Seq.empty[Example],
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None
  ) extends OnClause {
    def id: Identifier = Identifier(loc, s"Term")

    override def isEmpty: Boolean = examples.isEmpty

    override def kind: String = "On Term"

    override def contents: Seq[Example] = examples

    override def format: String = ""
  }

  /** A named handler of messages (commands, events, queries) that bundles
    * together a set of [[OnMessageClause]] definitions and by doing so defines
    * the behavior of an entity. Note that entities may define multiple handlers
    * and switch between them to change how it responds to messages over time or
    * in response to changing conditions
    *
    * @param loc
    *   The location of the handler definition
    * @param id
    *   The name of the handler.
    * @param clauses
    *   The set of [[OnMessageClause]] definitions that define how the entity
    *   responds to received messages.
    * @param brief
    *   A brief description (one sentence) for use in documentation
    * @param description
    *   An optional description of the handler
    */
  case class Handler(
    loc: At,
    id: Identifier,
    clauses: Seq[OnClause] = Seq.empty[OnClause],
    authors: Seq[AuthorRef] = Seq.empty[AuthorRef],
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None
  ) extends Container[HandlerDefinition]
      with AdaptorDefinition
      with ApplicationDefinition
      with ContextDefinition
      with EntityDefinition
      with StateDefinition
      with RepositoryDefinition
      with StreamletDefinition
      with ProjectorDefinition {
    override def isEmpty: Boolean = clauses.isEmpty

    override def contents: Seq[HandlerDefinition] = clauses

    final val kind: String = "Handler"

    def format: String = s"${Keywords.handler} ${id.format}"
  }

  /** A reference to a Handler
    *
    * @param loc
    *   The location of the handler reference
    * @param pathId
    *   The path identifier of the referenced handler
    */
  case class HandlerRef(loc: At, pathId: PathIdentifier)
      extends Reference[Handler] {
    override def format: String = s"${Keywords.handler} ${pathId.format}"
  }

  /** Represents the state of an entity. The MorphAction can cause the state
    * definition of an entity to change.
    *
    * @param loc
    *   The location of the state definition
    * @param id
    *   The name of the state definition
    * @param typ
    *   A reference to a type definition that provides the range of values that
    *   the state may assume.
    * @param types
    *   Types defined within the body of the state
    * @param handlers
    *   The handler definitions that may occur when this state is active
    * @param invariants
    *   Expressions of boolean logic that must always evaluate to true before
    *   and after an entity changes when this state is active.
    * @param brief
    *   A brief description (one sentence) for use in documentation
    * @param description
    *   An optional description of the state.
    */
  case class State(
    loc: At,
    id: Identifier,
    typ: TypeRef,
    types: Seq[Type] = Seq.empty[Type],
    handlers: Seq[Handler] = Seq.empty[Handler],
    invariants: Seq[Invariant] = Seq.empty[Invariant],
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None
  ) extends EntityDefinition {

    override def contents: Seq[StateDefinition] = types ++
      handlers ++ invariants

    def format: String = s"${Keywords.state} ${id.format}"

    final val kind: String = "State"
  }

  /** A reference to an entity's state definition
    *
    * @param loc
    *   The location of the state reference
    * @param pathId
    *   The path identifier of the referenced state definition
    */
  case class StateRef(loc: At, pathId: PathIdentifier)
      extends Reference[State] {
    override def format: String = s"${Keywords.state} ${pathId.format}"
  }

  /** Definition of an Entity
    *
    * @param options
    *   The options for the entity
    * @param loc
    *   The location in the input
    * @param id
    *   The name of the entity
    * @param states
    *   The state values of the entity
    * @param types
    *   Type definitions useful internally to the entity definition
    * @param handlers
    *   A set of event handlers
    * @param functions
    *   Utility functions defined for the entity
    * @param invariants
    *   Invariant properties of the entity
    * @param brief
    *   A brief description (one sentence) for use in documentation
    * @param description
    *   Optional description of the entity
    */
  case class Entity(
    loc: At,
    id: Identifier,
    options: Seq[EntityOption] = Seq.empty[EntityOption],
    states: Seq[State] = Seq.empty[State],
    types: Seq[Type] = Seq.empty[Type],
    constants: Seq[Constant] = Seq.empty[Constant],
    handlers: Seq[Handler] = Seq.empty[Handler],
    functions: Seq[Function] = Seq.empty[Function],
    invariants: Seq[Invariant] = Seq.empty[Invariant],
    inlets: Seq[Inlet] = Seq.empty[Inlet],
    outlets: Seq[Outlet] = Seq.empty[Outlet],
    includes: Seq[Include[EntityDefinition]] = Seq
      .empty[Include[EntityDefinition]],
    authors: Seq[AuthorRef] = Seq.empty[AuthorRef],
    terms: Seq[Term] = Seq.empty[Term],
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None
  ) extends Processor[EntityOption, EntityDefinition]
      with ContextDefinition {

    override lazy val contents: Seq[EntityDefinition] = {
      super.contents ++ states ++ types ++ handlers ++ functions ++
        invariants ++ terms ++ inlets ++ outlets
    }

    final val kind: String = "Entity"

    override def isEmpty: Boolean = contents.isEmpty && options.isEmpty

    override def maturity: Int = {
      var score = super.maturity
      if (states.nonEmpty) score += Math.max(states.count(_.nonEmpty), 10)
      if (types.nonEmpty) score += Math.max(types.count(_.nonEmpty), 25)
      if (handlers.nonEmpty) score += 1
      if (invariants.nonEmpty)
        score +=
          Math.max(invariants.count(_.nonEmpty), 10)
      if (functions.nonEmpty) score += Math.max(functions.count(_.nonEmpty), 5)
      Math.max(score, maxMaturity)
    }
  }

  sealed trait AdaptorDirection extends RiddlValue

  case class InboundAdaptor(loc: At) extends AdaptorDirection {
    def format: String = "from"
  }

  case class OutboundAdaptor(loc: At) extends AdaptorDirection {
    def format: String = "to"
  }

  /** Definition of an Adaptor. Adaptors are defined in Contexts to convert
    * messages from another bounded context. Adaptors translate incoming
    * messages into corresponding messages using the ubiquitous language of the
    * defining bounded context. There should be one Adapter for each external
    * Context
    *
    * @param loc
    *   Location in the parsing input
    * @param id
    *   Name of the adaptor
    * @param direction
    *   An indication of whether this is an inbound or outbound adaptor.
    * @param context
    *   A reference to the bounded context from which messages are adapted
    * @param handlers
    *   A set of [[Handler]]s that indicate what to do when messages occur.
    * @param brief
    *   A brief description (one sentence) for use in documentation
    * @param description
    *   Optional description of the adaptor.
    */
  case class Adaptor(
    loc: At,
    id: Identifier,
    direction: AdaptorDirection,
    context: ContextRef,
    handlers: Seq[Handler] = Seq.empty[Handler],
    inlets: Seq[Inlet] = Seq.empty[Inlet],
    outlets: Seq[Outlet] = Seq.empty[Outlet],
    types: Seq[Type] = Seq.empty[Type],
    constants: Seq[Constant] = Seq.empty[Constant],
    includes: Seq[Include[AdaptorDefinition]] = Seq
      .empty[Include[AdaptorDefinition]],
    authors: Seq[AuthorRef] = Seq.empty[AuthorRef],
    options: Seq[AdaptorOption] = Seq.empty[AdaptorOption],
    terms: Seq[Term] = Seq.empty[Term],
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None
  ) extends Processor[AdaptorOption, AdaptorDefinition]
      with ContextDefinition {
    override lazy val contents: Seq[AdaptorDefinition] = {
      super.contents ++ handlers ++ inlets ++ outlets ++ terms
    }
    final val kind: String = "Adaptor"

    override def maturity: Int = {
      var score = super.maturity
      if (handlers.nonEmpty)
        score +=
          Math.max(handlers.count(_.nonEmpty), maxMaturity)
      Math.max(score, maxMaturity)
    }
  }

  case class AdaptorRef(loc: At, pathId: PathIdentifier)
      extends ProcessorRef[Adaptor] {
    override def format: String = s"${Keywords.adaptor} ${pathId.format}"
  }

  /** A RIDDL repository is an abstraction for anything that can retain
    * information(e.g. messages for retrieval at a later time. This might be a
    * relational database, NoSQL database, data lake, API, or something not yet
    * invented. There is no specific technology implied other than the retention
    * and retrieval of information. You should think of repositories more like a
    * message-oriented version of the Java Repository Pattern than any
    * particular kind ofdatabase.
    *
    * @see
    *   https://java-design-patterns.com/patterns/repository/#explanation
    * @param loc
    *   Location in the source of the Repository
    * @param id
    *   The unique identifier for this Repository
    * @param types
    *   The types, typically messages, that the Repository uses
    * @param handlers
    *   The handler for specifying how messages should be handled by the
    *   repository
    * @param authors
    *   The author(s) who wrote this repository specification.
    * @param includes
    *   Included files
    * @param options
    *   Options that can be used by the translators
    * @param terms
    *   Definitions of terms about this repository
    * @param brief
    *   A brief description of this repository
    * @param description
    *   A detailed description of this repository
    */
  case class Repository(
    loc: At,
    id: Identifier,
    types: Seq[Type] = Seq.empty[Type],
    handlers: Seq[Handler] = Seq.empty[Handler],
    inlets: Seq[Inlet] = Seq.empty[Inlet],
    outlets: Seq[Outlet] = Seq.empty[Outlet],
    authors: Seq[AuthorRef] = Seq.empty[AuthorRef],
    includes: Seq[Include[RepositoryDefinition]] = Seq
      .empty[Include[RepositoryDefinition]],
    options: Seq[RepositoryOption] = Seq.empty[RepositoryOption],
    terms: Seq[Term] = Seq.empty[Term],
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None
  ) extends Processor[RepositoryOption, RepositoryDefinition]
      with ContextDefinition {
    override def kind: String = "Repository"

    override lazy val contents: Seq[RepositoryDefinition] = {
      super.contents ++ types ++ handlers ++ inlets ++ outlets ++ terms
    }
  }

  /** A reference to a repository definition
    *
    * @param loc
    *   The location of the state reference
    * @param pathId
    *   The path identifier of the referenced projector definition
    */
  case class RepositoryRef(loc: At, pathId: PathIdentifier)
      extends ProcessorRef[Projector] {
    override def format: String = s"${Keywords.repository} ${pathId.format}"
  }

  /** Projectors get their name from Euclidean Geometry but are probably more
    * analogous to a relational database view. The concept is very simple in
    * RIDDL: projectors gather data from entities and other sources, transform
    * that data into a specific record type, and support querying that data
    * arbitrarily.
    *
    * @see
    *   https://en.wikipedia.org/wiki/View_(SQL)).
    * @see
    *   https://en.wikipedia.org/wiki/Projector_(mathematics)
    * @param loc
    *   Location in the source of the Projector
    * @param id
    *   The unique identifier for this Projector
    * @param authors
    *   The authors of this definition
    * @param options
    *   Options that can be used by the translators
    * @param types
    *   The type definitions necessary to construct the query results
    * @param handlers
    *   Specifies how to handle
    * @param terms
    *   Definitions of terms about this Projector
    * @param brief
    *   A brief description of this Projector
    * @param description
    *   A detailed description of this Projector
    */
  case class Projector(
    loc: At,
    id: Identifier,
    authors: Seq[AuthorRef] = Seq.empty[AuthorRef],
    options: Seq[ProjectorOption] = Seq.empty[ProjectorOption],
    includes: Seq[Include[ProjectorDefinition]] = Seq
      .empty[Include[ProjectorDefinition]],
    types: Seq[Type] = Seq.empty[Type],
    constants: Seq[Constant] = Seq.empty[Constant],
    inlets: Seq[Inlet] = Seq.empty[Inlet],
    outlets: Seq[Outlet] = Seq.empty[Outlet],
    handlers: Seq[Handler] = Seq.empty[Handler],
    invariants: Seq[Invariant] = Seq.empty[Invariant],
    terms: Seq[Term] = Seq.empty[Term],
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None
  ) extends Processor[ProjectorOption, ProjectorDefinition]
      with ContextDefinition
      with WithTypes {
    override lazy val contents: Seq[ProjectorDefinition] = {
      super.contents ++ handlers ++ invariants ++ terms
    }
    final val kind: String = "Projector"

    override def maturity: Int = {
      var score = super.maturity
      val records: Seq[Type] = types.filter(_.typ.isContainer)
      if (records.nonEmpty)
        score +=
          Math.max(types.count(_.nonEmpty), maxMaturity)
      Math.max(score, maxMaturity)
    }
  }

  /** A reference to an context's projector definition
    *
    * @param loc
    *   The location of the state reference
    * @param pathId
    *   The path identifier of the referenced projector definition
    */
  case class ProjectorRef(loc: At, pathId: PathIdentifier)
      extends ProcessorRef[Projector] {
    override def format: String = s"${Keywords.projector} ${pathId.format}"
  }

  /** A bounded context definition. Bounded contexts provide a definitional
    * boundary on the language used to describe some aspect of a system. They
    * imply a tightly integrated ecosystem of one or more microservices that
    * share a common purpose. Context can be used to house entities, read side
    * projectors, sagas, adaptations to other contexts, apis, and etc.
    *
    * @param loc
    *   The location of the bounded context definition
    * @param id
    *   The name of the context
    * @param options
    *   The options for the context
    * @param types
    *   Types defined for the scope of this context
    * @param entities
    *   Entities defined for the scope of this context
    * @param adaptors
    *   Adaptors to messages from other contexts
    * @param sagas
    *   Sagas with all-or-none semantics across various entities
    * @param functions
    *   Features specified for the context
    * @param brief
    *   A brief description (one sentence) for use in documentation
    * @param description
    *   An optional description of the context
    */
  case class Context(
    loc: At,
    id: Identifier,
    options: Seq[ContextOption] = Seq.empty[ContextOption],
    types: Seq[Type] = Seq.empty[Type],
    constants: Seq[Constant] = Seq.empty[Constant],
    entities: Seq[Entity] = Seq.empty[Entity],
    adaptors: Seq[Adaptor] = Seq.empty[Adaptor],
    sagas: Seq[Saga] = Seq.empty[Saga],
    streamlets: Seq[Streamlet] = Seq.empty[Streamlet],
    functions: Seq[Function] = Seq.empty[Function],
    terms: Seq[Term] = Seq.empty[Term],
    includes: Seq[Include[ContextDefinition]] = Seq
      .empty[Include[ContextDefinition]],
    handlers: Seq[Handler] = Seq.empty[Handler],
    projectors: Seq[Projector] = Seq.empty[Projector],
    repositories: Seq[Repository] = Seq.empty[Repository],
    inlets: Seq[Inlet] = Seq.empty[Inlet],
    outlets: Seq[Outlet] = Seq.empty[Outlet],
    connections: Seq[Connector] = Seq.empty[Connector],
    authors: Seq[AuthorRef] = Seq.empty[AuthorRef],
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None
  ) extends Processor[ContextOption, ContextDefinition]
      with DomainDefinition {
    override lazy val contents: Seq[ContextDefinition] = super.contents ++
      types ++ entities ++ adaptors ++ sagas ++ streamlets ++ functions ++
      terms ++ handlers ++ projectors ++ repositories ++ inlets ++
      outlets ++ connections

    final val kind: String = "Context"

    override def isEmpty: Boolean = contents.isEmpty && options.isEmpty

    override def maturity: Int = {
      var score = super.maturity
      if (types.nonEmpty) score += Math.max(types.count(_.nonEmpty), 10)
      if (adaptors.nonEmpty) score += Math.max(types.count(_.nonEmpty), 5)
      if (sagas.nonEmpty) score += Math.max(types.count(_.nonEmpty), 5)
      if (streamlets.nonEmpty) score += Math.max(types.count(_.nonEmpty), 10)
      if (functions.nonEmpty) score += Math.max(types.count(_.nonEmpty), 10)
      if (handlers.nonEmpty) score += 10
      if (projectors.nonEmpty) score += Math.max(types.count(_.nonEmpty), 10)
      Math.max(score, maxMaturity)
    }
  }

  /** A reference to a bounded context
    *
    * @param loc
    *   The location of the reference
    * @param pathId
    *   The path identifier for the referenced context
    */
  case class ContextRef(loc: At, pathId: PathIdentifier)
      extends ProcessorRef[Context] {
    override def format: String = s"context ${pathId.format}"
  }

  /** A sealed trait for Inlets and Outlets */
  sealed trait Portlet extends Definition

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
  case class Inlet(
    loc: At,
    id: Identifier,
    type_ : Reference[Type],
    brief: Option[LiteralString] = None,
    description: Option[Description] = None
  ) extends Portlet
      with LeafDefinition
      with ProcessorDefinition
      with AlwaysEmpty {
    def format: String =
      s"${Keywords.inlet} ${id.format} is ${type_.format}"

    final val kind: String = "Inlet"
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
  case class Outlet(
    loc: At,
    id: Identifier,
    type_ : Reference[Type],
    brief: Option[LiteralString] = None,
    description: Option[Description] = None
  ) extends Portlet
      with LeafDefinition
      with ProcessorDefinition
      with AlwaysEmpty {
    def format: String = s"${Keywords.outlet} ${id.format} is ${type_.format}"

    final val kind: String = "Outlet"
  }

  case class Connector(
    loc: At,
    id: Identifier,
    options: Seq[ConnectorOption] = Seq.empty[ConnectorOption],
    flows: Option[TypeRef] = Option.empty[TypeRef],
    from: Option[OutletRef] = Option.empty[OutletRef],
    to: Option[InletRef] = Option.empty[InletRef],
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = Option.empty[Description]
  ) extends LeafDefinition
      with ContextDefinition
      with WithOptions[ConnectorOption] {
    final override def isEmpty: Boolean = super.isEmpty && flows.isEmpty &&
      from.isEmpty && to.isEmpty

    final val kind: String = "Connector"
    override def format: String = s"${Keywords.connector}"
  }

  sealed trait StreamletShape extends RiddlValue {
    def keyword: String
  }

  case class Void(loc: At) extends StreamletShape {
    def format: String = Keywords.void

    def keyword: String = Keywords.void
  }

  case class Source(loc: At) extends StreamletShape {
    def format: String = Keywords.source

    def keyword: String = Keywords.source
  }

  case class Sink(loc: At) extends StreamletShape {
    def format: String = Keywords.sink

    def keyword: String = Keywords.sink
  }

  case class Flow(loc: At) extends StreamletShape {
    def format: String = Keywords.flow

    def keyword: String = Keywords.flow
  }

  case class Merge(loc: At) extends StreamletShape {
    def format: String = Keywords.merge

    def keyword: String = Keywords.merge
  }

  case class Split(loc: At) extends StreamletShape {
    def format: String = Keywords.split

    def keyword: String = Keywords.split
  }

  case class Router(loc: At) extends StreamletShape {
    def format: String = Keywords.router

    def keyword: String = Keywords.router
  }

  /** Definition of a Streamlet. A computing element for processing data from
    * [[Inlet]]s to [[Outlet]]s. A processor's processing is specified by
    * Gherkin [[Example]]s. Streamlets come in various shapes: Source, Sink,
    * Flow, Merge, Split, and Router depending on how many inlets and outlets
    * they have
    *
    * @param loc
    *   The location of the Processor definition
    * @param id
    *   The name of the processor
    * @param shape
    *   The shape of the processor's inputs and outputs
    * @param inlets
    *   The list of inlets that provide the data the processor needs
    * @param outlets
    *   The list of outlets that the processor produces
    * @param handlers
    *   Definitions of how the processor handles each event type
    * @param brief
    *   A brief description (one sentence) for use in documentation
    * @param description
    *   An optional description of the processor
    */
  case class Streamlet(
    loc: At,
    id: Identifier,
    shape: StreamletShape,
    inlets: Seq[Inlet] = Seq.empty[Inlet],
    outlets: Seq[Outlet] = Seq.empty[Outlet],
    handlers: Seq[Handler] = Seq.empty[Handler],
    types: Seq[Type] = Seq.empty[Type],
    includes: Seq[Include[StreamletDefinition]] = Seq
      .empty[Include[StreamletDefinition]],
    authors: Seq[AuthorRef] = Seq.empty[AuthorRef],
    options: Seq[StreamletOption] = Seq.empty[StreamletOption],
    terms: Seq[Term] = Seq.empty[Term],
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None
  ) extends Processor[StreamletOption, StreamletDefinition]
      with ContextDefinition {
    override def contents: Seq[StreamletDefinition] = super.contents ++
      inlets ++ outlets ++ handlers ++ terms

    final val kind: String = shape.getClass.getSimpleName

    override def maturity: Int = {
      var score = super.maturity
      if (inlets.nonEmpty) score += Math.max(inlets.count(_.nonEmpty), 5)
      if (outlets.nonEmpty) score += Math.max(outlets.count(_.nonEmpty), 5)
      if (handlers.nonEmpty) score += Math.max(handlers.count(_.nonEmpty), 40)
      Math.max(score, maxMaturity)
    }

    shape match {
      case Source(_) =>
        require(
          isEmpty || (outlets.size == 1 && inlets.isEmpty),
          s"Invalid Source Streamlet ins: ${outlets.size} == 1, ${inlets.size} == 0"
        )
      case Sink(_) =>
        require(
          isEmpty || (outlets.isEmpty && inlets.size == 1),
          "Invalid Sink Streamlet"
        )
      case Flow(_) =>
        require(
          isEmpty || (outlets.size == 1 && inlets.size == 1),
          "Invalid Flow Streamlet"
        )
      case Merge(_) =>
        require(
          isEmpty || (outlets.size == 1 && inlets.size >= 2),
          "Invalid Merge Streamlet"
        )
      case Split(_) =>
        require(
          isEmpty || (outlets.size >= 2 && inlets.size == 1),
          "Invalid Split Streamlet"
        )
      case Router(_) =>
        require(
          isEmpty || (outlets.size >= 2 && inlets.size >= 2),
          "Invalid Router Streamlet"
        )
      case Void(_) =>
        require(
          isEmpty || (outlets.isEmpty && inlets.isEmpty),
          "Invalid Void Stream"
        )
    }

  }

  /** A reference to an context's projector definition
    *
    * @param loc
    *   The location of the state reference
    * @param pathId
    *   The path identifier of the referenced projector definition
    */
  case class StreamletRef(loc: At, pathId: PathIdentifier)
      extends ProcessorRef[Streamlet] {
    override def format: String = s"${Keywords.streamlet} ${pathId.format}"
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
  case class InletRef(loc: At, pathId: PathIdentifier)
      extends PortletRef[Inlet] {
    override def format: String = s"${Keywords.inlet} ${pathId.format}"
  }

  /** A reference to an [[Outlet]]
    *
    * @param loc
    *   The location of the outlet reference
    * @param pathId
    *   The path identifier of the referenced [[Outlet]]
    */
  case class OutletRef(loc: At, pathId: PathIdentifier)
      extends PortletRef[Outlet] {
    override def format: String = s"${Keywords.outlet} ${pathId.format}"
  }

  /** The definition of one step in a saga with its undo step and example.
    *
    * @param loc
    *   The location of the saga action definition
    * @param id
    *   The name of the SagaAction
    * @param doAction
    *   The command to be done.
    * @param undoAction
    *   The command that undoes [[doAction]]
    * @param brief
    *   A brief description (one sentence) for use in documentation
    * @param description
    *   An optional description of the saga action
    */
  case class SagaStep(
    loc: At,
    id: Identifier,
    doAction: Seq[Example] = Seq.empty[Example],
    undoAction: Seq[Example] = Seq.empty[Example],
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None
  ) extends SagaDefinition {
    def contents: Seq[Example] = doAction ++ undoAction

    def format: String = s"${Keywords.step} ${id.format}"

    final val kind: String = "SagaStep"
  }

  /** The definition of a Saga based on inputs, outputs, and the set of
    * [[SagaStep]]s involved in the saga. Sagas define a computing action based
    * on a variety of related commands that must all succeed atomically or have
    * their effects undone.
    *
    * @param loc
    *   The location of the Saga definition
    * @param id
    *   The name of the saga
    * @param options
    *   The options of the saga
    * @param input
    *   A definition of the aggregate input values needed to invoke the saga, if
    *   any.
    * @param output
    *   A definition of the aggregate output values resulting from invoking the
    *   saga, if any.
    * @param sagaSteps
    *   The set of [[SagaStep]]s that comprise the saga.
    * @param brief
    *   A brief description (one sentence) for use in documentation
    * @param description
    *   An optional description of the saga.
    */
  case class Saga(
    loc: At,
    id: Identifier,
    options: Seq[SagaOption] = Seq.empty[SagaOption],
    input: Option[Aggregation] = None,
    output: Option[Aggregation] = None,
    sagaSteps: Seq[SagaStep] = Seq.empty[SagaStep],
    functions: Seq[Function] = Seq.empty[Function],
    inlets: Seq[Inlet] = Seq.empty[Inlet],
    outlets: Seq[Outlet] = Seq.empty[Outlet],
    authors: Seq[AuthorRef] = Seq.empty[AuthorRef],
    includes: Seq[Include[SagaDefinition]] = Seq.empty[Include[SagaDefinition]],
    terms: Seq[Term] = Seq.empty[Term],
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None
  ) extends VitalDefinition[SagaOption, SagaDefinition]
      with ContextDefinition {
    override lazy val contents: Seq[SagaDefinition] = {
      super.contents ++ input.map(_.fields).getOrElse(Seq.empty[Field]) ++
        output.map(_.fields).getOrElse(Seq.empty[Field]) ++ sagaSteps ++ terms
    }
    final val kind: String = "Saga"

    override def isEmpty: Boolean = super.isEmpty && options.isEmpty &&
      input.isEmpty && output.isEmpty

    override def maturity: Int = {
      var score = super.maturity
      if (input.nonEmpty) score += 10
      if (output.nonEmpty) score += 10
      if (sagaSteps.nonEmpty) score += Math.max(sagaSteps.count(_.nonEmpty), 40)
      Math.max(score, maxMaturity)
    }
  }

  case class SagaRef(loc: At, pathId: PathIdentifier) extends Reference[Saga] {
    def format: String = s"${Keywords.saga} ${pathId.format}"
  }

  /** An StoryActor (Role) who is the initiator of the user story. Actors may be
    * persons or machines
    *
    * @param loc
    *   The location of the actor in the source
    * @param id
    *   The name (role) of the actor
    * @param is_a
    *   What kind of thing the actor is
    * @param brief
    *   A brief description of the actor
    * @param description
    *   A longer description of the actor and its role
    */
  case class Actor(
    loc: At,
    id: Identifier,
    is_a: LiteralString,
    brief: Option[LiteralString] = None,
    description: Option[Description] = None
  ) extends LeafDefinition
      with DomainDefinition {
    def format: String = s"${Keywords.actor} ${id.format} is ${is_a.format}"

    override def kind: String = "Actor"
  }

  /** A reference to an StoryActor using a path identifier
    *
    * @param loc
    *   THe location of the StoryActor in the source code
    * @param pathId
    *   The path identifier that locates the references StoryActor
    */
  case class ActorRef(loc: At, pathId: PathIdentifier)
      extends Reference[Actor] {
    def format: String = s"${Keywords.actor} ${pathId.format}"
  }

  sealed trait Interaction extends UseCaseDefinition {
    /** Format the node to a string */
    override def format: String = s"$kind ${id.format}"
  }

  /** An interaction expression that specifies that each contained expression
    * should be executed in parallel
    *
    * @param loc
    *   Location of the parallel group
    * @param contents
    *   The expressions to execute in parallel
    * @param brief
    *   A brief description of the parallel group
    */
  case class ParallelInteractions(
    loc: At,
    id: Identifier = Identifier.empty,
    contents: Seq[Interaction] = Seq.empty[Interaction],
    brief: Option[LiteralString] = None,
    description: Option[Description] = None
  ) extends Interaction {
    override def kind: String = "Parallel Interaction"
  }

    /** An interaction expression that specifies that each contained expression
     * should be executed in strict sequential order
     *
     * @param loc
     *   Location of the sequence
     * @param contents
     *   The interactions to execute in sequence
     * @param brief
     *   A brief description of the sequence group
     */
    case class SequentialInteractions(
      loc: At,
      id: Identifier = Identifier.empty,
      contents: Seq[Interaction] = Seq.empty[Interaction],
      brief: Option[LiteralString],
      description: Option[Description] = None
    ) extends Interaction {
      override def kind: String = "Sequential Interaction"
  }

    /** An interaction expression that specifies that each contained expression
     * should be executed in strict sequential order
     *
     * @param loc
     *   Location of the sequence
     * @param contents
     *   The interactions to execute in sequence
     * @param brief
     *   A brief description of the sequence group
     */
    case class SequentialInteractions(
      loc: At,
      contents: Seq[Interaction],
      brief: Option[LiteralString]
    ) extends Interaction {

    /** Format the node to a string */
    override def format: String = ""
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
  case class OptionalInteractions(
    loc: At,
    id: Identifier = Identifier.empty,
    contents: Seq[Interaction] = Seq.empty[Interaction],
    brief: Option[LiteralString],
    description: Option[Description] = None
  ) extends Interaction {
    override def kind: String = "Optional Interaction"
  }

  /** One abstract step in an Interaction between things. The set of case
    * classes associated with this sealed trait provide more type specificity to
    * these three fields.
    */
  sealed trait GenericInteraction extends Interaction with LeafDefinition {
    def from: Reference[Definition]

    def relationship: RiddlNode

    def to: Reference[Definition]
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
  case class ArbitraryInteraction(
    loc: At,
    id: Identifier = Identifier.empty,
    from: Reference[Definition],
    relationship: LiteralString,
    to: Reference[Definition],
    brief: Option[LiteralString] = None,
    description: Option[Description] = None
  ) extends GenericInteraction {
    override def kind: String = "Arbitrary Interaction"
  }

  case class SelfInteraction(
    loc: At,
    id: Identifier = Identifier.empty,
    from: Reference[Definition],
    relationship: LiteralString,
    brief: Option[LiteralString] = None,
    description: Option[Description] = None
  ) extends GenericInteraction {
    override def kind: String = "Self Interaction"
    override def to: Reference[Definition] = from
  }

  /** An interaction where an Actor receives output
    * @param loc
    *   The locaiton of the interaction in the source
    * @param from
    *   The output received
    * @param relationship
    *   THe name of the relationship
    * @param to
    *   THe actor that receives the output
    * @param brief
   * A brief description of this interaction
    */
  case class TakeOutputInteraction(
    loc: At,
    id: Identifier = Identifier.empty,
    from: OutputRef,
    relationship: LiteralString,
    to: ActorRef,
    brief: Option[LiteralString] = None,
    description: Option[Description] = None
  ) extends GenericInteraction {
    override def kind: String = "Take Output Interaction"
  }

  /** A interaction where and Actor provides input
    *
    * @param loc
    *   The location of the interaction in the source
    * @param from
    *   The actor providing the input
    * @param relationship
    *   A description of the relationship in this interaction
    * @param to
    *   The input definition that receives the input
    * @param brief
    *   A description of this interaction step
    */
  case class PutInputInteraction(
    loc: At,
    id: Identifier = Identifier.empty,
    from: ActorRef,
    relationship: LiteralString,
    to: InputRef,
    brief: Option[LiteralString] = None,
    description: Option[Description] = None
  ) extends GenericInteraction {
    override def kind: String = "Put Input Interaction"
  }

  /** The definition of a Jacobsen Use Case RIDDL defines these epics by
    * allowing a linkage between the actor and RIDDL applications or bounded
    * contexts.
    * @param loc
    *   Where in the source this use case occurs
    * @param id
    *   The unique identifier for this use case
    * @param contents
    *   The interactions between actors and system components that define the
    *   use case.
    * @param brief
    *   A brief description of this use case
    * @param description
    *   A longer description of this use case
    */
  case class UseCase(
    loc: At,
    id: Identifier,
    userStory: Option[UserStory] = None,
    contents: Seq[Interaction] = Seq.empty[Interaction],
    brief: Option[LiteralString] = None,
    description: Option[Description] = None
  ) extends EpicDefinition with Container[Interaction] {
    override def kind: String = "UseCase"
    override def format: String = s"${Keywords.case_} ${id.format}"
  }

  /** An agile user story definition in the usual "As a {role} I want
    * {capability} so that {benefit}" style.
    *
    * @param loc
    *   Location of the user story
    * @param actor
    *   The actor, or instigator, of the story.
    * @param capability
    *   The capability the actor wishes to utilize
    * @param benefit
    *   The benefit of that utilization
    */
  case class UserStory(
    loc: At,
    actor: ActorRef,
    capability: LiteralString,
    benefit: LiteralString
  ) extends RiddlValue {
    def format: String = ""

    override def isEmpty: Boolean = false
  }

  /** The definition of an Epic that bundles multiple Jacobsen Use Cases into an
    * overall story about user interactions with the system. This define
    * functionality from the perspective of actors (men or machines)
    * interactions with the system that is part of their role.
    *
    * @param loc
    *   The location of the Epic definition
    * @param id
    *   The name of the Epic
    * @param userStory
    *   The [[UserStory]] (per agile and xP) that provides the overall big
    *   picture of this Epic
    * @param shownBy
    *   A list of URLs to visualizations or other materials related to the epic
    * @param cases
    *   A list of UseCase's that define the epic
    * @param brief
    *   A brief description (one sentence) for use in the glossary and
    *   summaries.
    * @param description
    *   An more detailed description of the Epic
    */
  case class Epic(
    loc: At,
    id: Identifier,
    userStory: Option[UserStory] = Option.empty[UserStory],
    shownBy: Seq[java.net.URL] = Seq.empty[java.net.URL],
    cases: Seq[UseCase] = Seq.empty[UseCase],
    authors: Seq[AuthorRef] = Seq.empty[AuthorRef],
    includes: Seq[Include[EpicDefinition]] = Seq
      .empty[Include[EpicDefinition]],
    options: Seq[EpicOption] = Seq.empty[EpicOption],
    terms: Seq[Term] = Seq.empty[Term],
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None
  ) extends VitalDefinition[EpicOption, EpicDefinition]
      with DomainDefinition {
    override def contents: Seq[EpicDefinition] = {
      super.contents ++ cases ++ terms
    }

    override def isEmpty: Boolean = {
      contents.isEmpty && shownBy.isEmpty && userStory.isEmpty
    }

    final val kind: String = "Epic"

    override def format: String = s"${Keywords.epic} ${id.format}"

    override def maturity: Int = {
      var score = super.maturity
      if (userStory.nonEmpty) score += 3
      if (shownBy.nonEmpty) score += 10
      if (cases.nonEmpty) score += Math.max(cases.count(_.nonEmpty), 25)
      Math.max(score, maxMaturity)
    }
  }

  /** A reference to a Story definintion.
    * @param loc
    *   Location of the StoryRef
    * @param pathId
    *   The path id of the referenced Story
    */
  case class EpicRef(loc: At, pathId: PathIdentifier) extends Reference[Epic] {
    def format: String = s"${Keywords.epic} ${pathId.format}"
  }

  /** Sealed trait for all UI elements that derive from it
    */
  sealed trait UIElement extends ApplicationDefinition

  /** A group of UIElement that can be treated as a whole. For example, a form,
    * a button group, etc.
    * @param loc
    *   The location of the group
    * @param id
    *   The unique identifier of the group
    * @param types
    *   Type definitions to define types shared by more than one UIElement
    * @param elements
    *   The list of UIElements
    * @param brief
    *   A brief description of the group
    * @param description
    *   A more detailed description of the group
    */
  case class Group(
    loc: At,
    id: Identifier,
    types: Seq[Type] = Seq.empty[Type],
    elements: Seq[UIElement] = Seq.empty[UIElement],
    brief: Option[LiteralString] = None,
    description: Option[Description] = None
  ) extends UIElement {
    override def kind: String = "Group"

    override lazy val contents: Seq[ApplicationDefinition] = {
      types ++ elements
    }

    /** Format the node to a string */
    override def format: String = ""
  }

  /** A Reference to a Group
    * @param loc
    *   The At locator of the group reference
    * @param pathId
    *   The path to the referenced group
    */
  case class GroupRef(loc: At, pathId: PathIdentifier)
      extends Reference[Group] {
    def format: String = s"${Keywords.group} ${pathId.format}"
  }

  /** A UI Element that presents some information to the user
    *
    * @param loc
    *   Location of the view in the source
    * @param id
    *   unique identifier oof the view
    * @param types
    *   any type definitions the view needs
    * @param putOut
    *   A result reference for the data too be presented
    * @param brief
    *   A brief description of the view
    * @param description
    *   A detailed description of the view
    */
  case class Output(
    loc: At,
    id: Identifier,
    types: Seq[Type],
    putOut: MessageRef,
    brief: Option[LiteralString] = None,
    description: Option[Description] = None
  ) extends UIElement {
    override def kind: String = "Output"

    override lazy val contents: Seq[ApplicationDefinition] = types

    /** Format the node to a string */
    override def format: String = ""
  }

  /** A reference to an View using a path identifier
    *
    * @param loc
    *   The location of the ViewRef in the source code
    * @param pathId
    *   The path identifier that refers to the View
    */
  case class OutputRef(loc: At, pathId: PathIdentifier)
      extends Reference[Output] {
    def format: String = s"${Keywords.output} ${pathId.format}"
  }

  /** A Give is a UI Element to allow the user to 'give' some data to the
    * application. It is analogous to a form in HTML
    *
    * @param loc
    *   Location of the Give
    * @param id
    *   Name of the give
    * @param types
    *   type definitions needed for the Give
    * @param putIn
    *   a Type reference of the type given by the user
    * @param brief
    *   A brief description of the Give
    * @param description
    *   a detailed description of the Give
    */
  case class Input(
    loc: At,
    id: Identifier,
    types: Seq[Type],
    putIn: MessageRef,
    brief: Option[LiteralString] = None,
    description: Option[Description] = None
  ) extends UIElement {
    override def kind: String = "Input"

    override lazy val contents: Seq[Definition] = types

    /** Format the node to a string */
    override def format: String = ""
  }

  /** A reference to an Input using a path identifier
    *
    * @param loc
    *   THe location of the GiveRef in the source code
    * @param pathId
    *   The path identifier that refers to the Give
    */
  case class InputRef(loc: At, pathId: PathIdentifier)
      extends Reference[Input] {
    def format: String = s"${Keywords.input} ${pathId.format}"
  }

  /** An application from which a person, robot, or other active agent (the
    * user) will obtain information, or to which that user will provided
    * information.
    * @param loc
    *   The location of the application in the source
    * @param id
    *   The unique identifier for the application
    * @param options
    *   The options for the application
    * @param types
    *   Types that are needed for the communication with the user
    * @param groups
    *   A list of group definitions needed by the application
    * @param handlers
    *   The handlers for this application to process incoming messages
    * @param inlets
    *   Message inlets for the application
    * @param outlets
    *   Message outlets for the application
    * @param authors
    *   Author definitions for the application, for attribution of application
    *   components.
    * @param terms
    *   Definitions of terms useful in comprehending the application's purpose
    * @param includes
    *   Included source code
    * @param brief
    *   A brief description of the application
    * @param description
    *   A longer description of the application.
    */
  case class Application(
    loc: At,
    id: Identifier,
    options: Seq[ApplicationOption] = Seq.empty[ApplicationOption],
    types: Seq[Type] = Seq.empty[Type],
    constants: Seq[Constant] = Seq.empty[Constant],
    groups: Seq[Group] = Seq.empty[Group],
    handlers: Seq[Handler] = Seq.empty[Handler],
    inlets: Seq[Inlet] = Seq.empty[Inlet],
    outlets: Seq[Outlet] = Seq.empty[Outlet],
    authors: Seq[AuthorRef] = Seq.empty[AuthorRef],
    terms: Seq[Term] = Seq.empty[Term],
    includes: Seq[Include[ApplicationDefinition]] = Seq.empty,
    brief: Option[LiteralString] = None,
    description: Option[Description] = None
  ) extends Processor[ApplicationOption, ApplicationDefinition]
      with DomainDefinition {
    override def kind: String = "Application"

    override lazy val contents: Seq[ApplicationDefinition] = {
      super.contents ++ types ++ groups ++ terms ++ includes
    }
  }

  /** A reference to an Application using a path identifier
    *
    * @param loc
    *   THe location of the StoryActor in the source code
    * @param id
    *   The path identifier that refers to the Application
    */
  case class ApplicationRef(loc: At, pathId: PathIdentifier)
      extends ProcessorRef[Application] {
    def format: String = s"${Keywords.application} ${pathId.format}"
  }

  /** The definition of a domain. Domains are the highest building block in
    * RIDDL and may be nested inside each other to form a hierarchy of domains.
    * Generally, domains follow hierarchical organization structure but other
    * taxonomies and ontologies may be modelled with domains too.
    *
    * @param loc
    *   The location of the domain definition
    * @param id
    *   The name of the domain
    * @param options
    *   Options for the domain
    * @param types
    *   Type definitions with a domain (nearly global) scope, with applicability
    *   to many contexts or subdomains
    * @param contexts
    *   The contexts defined in the scope of the domain
    * @param actors
    *   Actor definitions for use in stories
    * @param stories
    *   Story definitions for this domain
    * @param applications
    *   Application definitions for this domain
    * @param domains
    *   Nested sub-domains within this domain
    * @param terms
    *   Definition of terms pertaining to this domain that provide explanation
    *   of concepts from the domain.
    * @param brief
    *   A brief description (one sentence) for use in documentation
    * @param description
    *   An optional description of the domain.
    */
  case class Domain(
    loc: At,
    id: Identifier,
    options: Seq[DomainOption] = Seq.empty[DomainOption],
    authors: Seq[AuthorRef] = Seq.empty[AuthorRef],
    authorDefs: Seq[Author] = Seq.empty[Author],
    types: Seq[Type] = Seq.empty[Type],
    constants: Seq[Constant] = Seq.empty[Constant],
    contexts: Seq[Context] = Seq.empty[Context],
    actors: Seq[Actor] = Seq.empty[Actor],
    stories: Seq[Epic] = Seq.empty[Epic],
    applications: Seq[Application] = Seq.empty[Application],
    domains: Seq[Domain] = Seq.empty[Domain],
    terms: Seq[Term] = Seq.empty[Term],
    includes: Seq[Include[DomainDefinition]] = Seq
      .empty[Include[DomainDefinition]],
    brief: Option[LiteralString] = Option.empty[LiteralString],
    description: Option[Description] = None
  ) extends VitalDefinition[DomainOption, DomainDefinition]
      with RootDefinition
      with WithTypes
      with DomainDefinition {

    override lazy val contents: Seq[DomainDefinition] = {
      super.contents ++ domains ++ types ++ constants ++ contexts ++ actors ++
        stories ++ applications ++ terms ++ authorDefs
    }
    final val kind: String = "Domain"

    override def maturity: Int = {
      var score = super.maturity
      if (types.nonEmpty) score += Math.max(types.count(_.nonEmpty), 15)
      if (contexts.nonEmpty) score += Math.max(contexts.count(_.nonEmpty), 15)
      if (stories.nonEmpty) score += Math.max(stories.count(_.nonEmpty), 15)
      if (applications.nonEmpty) score += Math.max(stories.count(_.nonEmpty), 5)
      if (domains.nonEmpty) score += Math.max(domains.count(_.nonEmpty), 10)
      Math.max(score, maxMaturity)
    }
  }

  /** A reference to a domain definition
    *
    * @param loc
    *   The location at which the domain definition occurs
    * @param id
    *   The path identifier for the referenced domain.
    */
  case class DomainRef(loc: At, pathId: PathIdentifier)
      extends Reference[Domain] {
    override def format: String = s"${Keywords.domain} ${pathId.format}"
  }
}
