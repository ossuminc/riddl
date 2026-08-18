/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language

import com.ossuminc.riddl.language.{Contents, *}
import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.AST.RelationshipCardinality.OneToOne
import com.ossuminc.riddl.language.parsing.Keyword
import com.ossuminc.riddl.language.{AST, At}
import com.ossuminc.riddl.utils.AbstractTestingBasis
import wvlet.airframe.ulid.ULID

/** Unit Tests For Abstract Syntax Tree */
class ASTTest extends AbstractTestingBasis {

  "Lifecycle clauses" should {
    // The compatibility policy requires a new parameter to have a default. `parameters` was added
    // in rc.14 without one, which is the only rc.14 change that broke synapify's build.
    "construct OnInitializationClause without naming `parameters`" in {
      val clause = OnInitializationClause(At.empty)
      clause.parameters mustBe empty
    }
    "construct OnTerminationClause without naming `parameters`" in {
      val clause = OnTerminationClause(At.empty)
      clause.parameters mustBe empty
    }
    // Defaulting in PLACE rather than moving the field keeps every existing positional
    // construction working — there are five in production code (HandlerParser x2, BASTReader,
    // JsonAstBuilder x2) that pass `parameters` second.
    "still accept `parameters` positionally in its original second position" in {
      val args = Seq(MethodArgument(At.empty, "total", Integer(At.empty)))
      OnInitializationClause(At.empty, args).parameters mustBe args
      OnTerminationClause(At.empty, args).parameters mustBe args
    }
  }

  "Descriptions" should {
    "have empty Description.empty" in {
      Description.empty.format mustBe ""
    }
    "have empty BlockDescription().format" in {
      BlockDescription().format mustBe ""
    }
  }

  "Domain" should {
    "return anonymous name when empty" in {
      val domain = Domain(At(), Identifier.empty)
      domain.identify must be("Anonymous Domain")
    }
  }

  "Identifier" should {
    "emit bare names verbatim" in {
      Identifier(At.empty, "Order").format mustBe "Order"
      Identifier(At.empty, "Order_2").format mustBe "Order_2"
    }
    "treat hyphenated names as bare (matching the parser's rule)" in {
      // simpleIdentifier is [A-Za-z][A-Za-z0-9_-]* — hyphens are allowed
      Identifier(At.empty, "my-entity").format mustBe "my-entity"
    }
    "quote names with special characters" in {
      Identifier(At.empty, "CI/CD Pipeline").format mustBe "'CI/CD Pipeline'"
      Identifier(At.empty, "Order Item").format mustBe "'Order Item'"
    }
    "quote names that start with a digit" in {
      Identifier(At.empty, "3dModel").format mustBe "'3dModel'"
    }
    "quote every character the quoted-identifier form allows" in {
      Identifier(At.empty, "a+b-c|d/e@f$g%h&i,j:k").format mustBe "'a+b-c|d/e@f$g%h&i,j:k'"
    }
    "preserve an empty value" in {
      Identifier(At.empty, "").format mustBe ""
    }
    "expose isBareIdentifier that agrees with simpleIdentifier" in {
      Identifier.isBareIdentifier("Order") mustBe true
      Identifier.isBareIdentifier("my-entity") mustBe true
      Identifier.isBareIdentifier("A1_b-2") mustBe true
      Identifier.isBareIdentifier("3d") mustBe false // starts with a digit
      Identifier.isBareIdentifier("_x") mustBe false // must start with a letter
      Identifier.isBareIdentifier("CI/CD") mustBe false
      Identifier.isBareIdentifier("") mustBe false
    }
  }

  "PathIdentifier quoting" should {
    "emit an all-bare path as a plain dotted form" in {
      PathIdentifier(At.empty, Seq("A", "B", "C")).format mustBe "A.B.C"
      PathIdentifier(At.empty, Seq("my-ctx", "my-entity")).format mustBe "my-ctx.my-entity"
    }
    "wrap the whole path in one pair of quotes when a component is special" in {
      PathIdentifier(At.empty, Seq("A", "CI/CD Pipeline", "C")).format mustBe "'A.CI/CD Pipeline.C'"
      PathIdentifier(At.empty, Seq("CI/CD Pipeline")).format mustBe "'CI/CD Pipeline'"
    }
  }

  "Types" should {
    "support domain definitions" in {
      Domain((0, 0), Identifier((1, 1), "foo")) must be
      Domain((0, 0), Identifier((1, 1), "foo"))
    }
    "support all type constructs" in {
      AliasedTypeExpression(
        0 -> 0,
        "record",
        PathIdentifier(0 -> 0, Seq("Foo"))
      ).format mustBe "record Foo"
      Enumeration((0, 0), Contents.empty[Enumerator]()).format mustBe "{  }"
      Alternation((0, 0), Contents.empty[AliasedTypeExpression]()).format mustBe "one of {  }"
      Aggregation((0, 0), Contents.empty[AggregateContents]()).format mustBe "{  }"
      Optional(
        (0, 0),
        AliasedTypeExpression((0, 0), "record", PathIdentifier((0, 0), Seq("String")))
      ).format mustBe "record String?"
      ZeroOrMore(
        (0, 0),
        AliasedTypeExpression((0, 0), "record", PathIdentifier((0, 0), Seq("Time")))
      ) mustBe ZeroOrMore(
        (0, 0),
        AliasedTypeExpression((0, 0), "record", PathIdentifier((0, 0), Seq("Time")))
      )
      OneOrMore(
        (0, 0),
        AliasedTypeExpression((0, 0), "record", PathIdentifier((0, 0), Seq("URL")))
      ) mustBe OneOrMore(
        (0, 0),
        AliasedTypeExpression((0, 0), "record", PathIdentifier((0, 0), Seq("URL")))
      )
      ZonedDateTime((0, 0)).format mustBe "ZonedDateTime(\"UTC\")"
      UUID((0, 0)).format mustBe "UUID"
      URI((0, 0)).format mustBe "URL(\"https\")"
      Location((0, 0)).format mustBe "Location"

      Blob((0, 0), BlobKind.Audio).format mustBe "Blob(Audio)"
      Blob((0, 0), BlobKind.Video).format mustBe "Blob(Video)"
      Blob((0, 0), BlobKind.CSV).format mustBe "Blob(CSV)"
      Blob((0, 0), BlobKind.FileSystem).format mustBe "Blob(FileSystem)"
      Blob((0, 0), BlobKind.Text).format mustBe "Blob(Text)"
      Blob((0, 0), BlobKind.XML).format mustBe "Blob(XML)"
      Blob((0, 0), BlobKind.JSON).format mustBe "Blob(JSON)"
      Blob((0, 0), BlobKind.Image).format mustBe "Blob(Image)"
    }
  }

  "PathIdentifier" should {
    "format" in {
      PathIdentifier(At(), Nil).format mustBe ""
      PathIdentifier(At(), List("foo", "baz")).format mustBe "foo.baz"
      PathIdentifier(At(), List("foo", "bar", "baz")).format mustBe "foo.bar.baz"
      PathIdentifier(At(), List("foo")).format mustBe "foo"
    }
  }

  "String" should {
    "have kind 'String'" in { String_(At()).kind mustBe "String" }
  }

  "Bool" should {
    "have kind 'Boolean'" in { Bool(At()).kind mustBe "Boolean" }
  }

  val actor: User = User(
    At.empty,
    Identifier(At.empty, "user"),
    LiteralString(At.empty, "role")
  )
  val adaptor: Adaptor = Adaptor(
    At.empty,
    Identifier(At.empty, "adaptor"),
    InboundAdaptor(At.empty),
    ContextRef(At.empty, PathIdentifier(At.empty, Seq("a", "b", "context")))
  )
  val authorRef: AuthorRef =
    AuthorRef(At.empty, PathIdentifier(At.empty, Seq("a", "b", "c")))
  val author: Author = Author(
    At.empty,
    Identifier(At(), "Reid"),
    LiteralString.empty,
    LiteralString.empty
  )
  val brief: Option[BriefDescription] = Some(
    BriefDescription(At.empty, LiteralString(At.empty, "brief"))
  )
  val briefs: Seq[BriefDescription] = Seq(brief.get)
  val description: Option[Description] = Some(
    BlockDescription(At.empty, Seq(LiteralString(At.empty, "Description")))
  )
  val descriptions: Seq[Description] = Seq(description.get)
  val entityRef: EntityRef = EntityRef(At.empty, PathIdentifier(At.empty, Seq("Entity")))
  val aggregate: AggregateUseCaseTypeExpression = AggregateUseCaseTypeExpression(
    At.empty,
    AggregateUseCase.CommandCase,
    Contents(Field(At(), Identifier(At(), "foo"), String_(At(), None, None)))
  )
  val command: Type = Type(At.empty, Identifier(At(), "command"), aggregate)
  val type_ : Type = Type(
    At.empty,
    Identifier(At(), "Str"),
    AliasedTypeExpression(At(), "command", PathIdentifier(At(), Seq("command")))
  )
  val typeRef: TypeRef = TypeRef(At.empty, "type", PathIdentifier(At(), Seq("Str")))

  val fieldRef: FieldRef = FieldRef(At(), PathIdentifier(At(), Seq("command", "foo")))
  val messageRef: CommandRef = CommandRef(At(), PathIdentifier(At(), Seq("command")))
  val recordRef: RecordRef = RecordRef(At(), PathIdentifier(At(), Seq("record")))
  val statements: Contents[Statements] = Contents(
    PromptStatement(At.empty, LiteralString(At.empty, "prompt")),
    BecomeStatement(At.empty, entityRef, HandlerRef(At(), PathIdentifier(At(), Seq("Entity")))),
    CodeStatement(
      At.empty,
      language = LiteralString(At.empty, "scala"),
      body = "def f[A](x: A): A"
    ),
    ErrorStatement(At.empty, LiteralString(At.empty, "error message")),
    LetStatement(At.empty, Identifier(At.empty, "varName"), None, LiteralString(At.empty, "value")),
    MatchStatement(
      At.empty,
      LiteralString(At.empty, "expr"),
      Seq(
        MatchCase(
          At.empty,
          LiteralPattern(At.empty, LiteralString(At.empty, "pattern")),
          None,
          Contents.empty()
        )
      ),
      Contents.empty()
    ),
    MorphStatement(
      At.empty,
      entityRef,
      StateRef(At.empty, PathIdentifier(At(), Seq("state"))),
      recordRef
    ),
    SendStatement(At.empty, messageRef, InletRef(At.empty, PathIdentifier(At.empty, Seq("inlet")))),
    SetStatement(At.empty, fieldRef, LiteralString(At.empty, "value")),
    TellStatement(At.empty, messageRef, entityRef),
    WhenStatement(At.empty, LiteralString(At.empty, "condition"), Contents.empty())
  )
  val function: Function =
    Function(
      At.empty,
      Identifier(At(), "Lambda"),
      statements.asInstanceOf[Contents[FunctionContents]],
      (brief.toSeq ++ description.toSeq).toContents.asInstanceOf[Contents[MetaData]]
    )
  val functionRef: FunctionRef = FunctionRef(At.empty, PathIdentifier(At.empty, Seq("Lambda")))
  val onClauses: Contents[OnClause] = Contents(
    OnInitializationClause(At.empty, Seq.empty, statements),
    OnMessageClause(At.empty, messageRef, None, None, statements),
    OnOtherClause(At.empty, None, None, statements),
    OnTerminationClause(At.empty, Seq.empty, statements)
  )
  val handler: Handler =
    Handler(
      At.empty,
      Identifier(At(), "handler"),
      onClauses.asInstanceOf[Contents[HandlerContents]]
    )
  val entity: Entity = Entity(At.empty, Identifier(At.empty, "Entity"), Contents(handler))
  val handlerRef: HandlerRef = HandlerRef(At.empty, PathIdentifier(At(), Seq("handler")))
  val sagaStep: SagaStep = SagaStep(At.empty, Identifier(At.empty, "sagaStep"))
  val state: State = State(At.empty, Identifier(At.empty, "state"), RecordRef())
  val stateRef: StateRef = StateRef(At.empty, PathIdentifier(At(), Seq("state")))
  val user: User =
    User(At.empty, Identifier(At.empty, "john"), LiteralString(At.empty, "GenericUser"))
  val userStory: UserStory = UserStory(
    At.empty,
    UserRef(At.empty, PathIdentifier(At.empty, Seq("user"))),
    LiteralString(At.empty, "do something"),
    LiteralString(At.empty, "he can reap obvious benefits")
  )
  val storyCase: UseCase = UseCase(At.empty, Identifier(At.empty, "story-case"), userStory)
  val epic: Epic = Epic(At.empty, Identifier(At.empty, "epic"), userStory)
  val term: Term =
    Term(At.empty, Identifier(At.empty, "term"), Seq(LiteralString(At.empty, "definition")))

  val relationship: Relationship = Relationship(
    At.empty,
    Identifier(At.empty, "moreMiniMes"),
    withProcessor = entityRef,
    cardinality = RelationshipCardinality.OneToMany,
    Some(LiteralString(At(), "more Mini-Mes"))
  )

  "User" should {
    "have a test" in {
      actor.format mustBe s"user ${actor.id.format} is ${actor.is_a.format}"
    }
  }
  val domain: AST.Domain =
    Domain(At(), Identifier(At(), "test"), contents = Contents(author))
  val context: AST.Context =
    Context(At(), Identifier(At(), "test"), Contents(relationship), metadata = Contents(authorRef))

  "Adaptor" should {
    "pass simple tests" in {
      adaptor.loc mustBe At.empty
      adaptor.id.value mustBe "adaptor"
      adaptor.direction mustBe InboundAdaptor(At.empty)
      adaptor.referent.pathId.value mustBe Seq("a", "b", "context")
    }
  }
  "Author" should {
    "be sane" in {
      author.isEmpty mustBe true
      author.format mustBe "author Reid"
    }
  }
  "AuthorRef" should {
    "convert to string" in { authorRef.format mustBe "author a.b.c" }
  }
  "AST.findAuthors" should {
    "find authors" in {
      val authors = AST.findAuthors(context, domain.contents.asInstanceOf[Contents[RiddlValue]])
      authors mustBe Seq(authorRef)
    }
  }

  "Context" should {
    "correctly identify emptiness" in {
      Context(At(), Identifier(At(), "empty")).contents must be(empty)
    }
    "correctly identify non-emptiness" in {
      val types = Contents(Type(At(), Identifier(At(), "A"), Bool(At())))
        .asInstanceOf[Contents[ContextContents]]
      Context(At(), Identifier(At(), "test"), contents = types).contents must be(types)
    }
    "have a relationship" in {
      context.contents.filter[Relationship] must be(Seq(relationship))
    }
  }
  "WithTypes" must {
    "be sane" in {
      val wt = Domain(At.empty, Identifier.empty)
      wt.hasAuthors mustBe false
      wt.hasTypes mustBe false
      wt.hasOptions mustBe false
      wt.isEmpty mustBe true
      wt.format mustBe "domain "
    }
  }

  "Domain" should {
    "empty domain should have empty contents" in {
      domain.contents mustNot be(empty)
    }
    "non-empty domain should have non-empty contents" in {
      val types = Contents(Type(At(), Identifier(At(), "A"), Bool(At())))
        .asInstanceOf[Contents[DomainContents]]
      Domain(At(), Identifier(At(), "test"), contents = types).contents mustBe
        types
    }
  }

  // Named after its own keyword, so `format` must quote it: `Epic epic` does not re-parse.
  "Epic" should { "format correctly" in { epic.format mustBe "Epic 'epic'" } }

  "Entity" should {
    "contents" should {
      "contain all contents" in {
        val states = Contents(
          State(
            At(),
            Identifier(At(), "bar"),
            RecordRef()
          )
        )
        val handlers = Contents(Handler(At(), Identifier(At(), "con")))

        val functions = Contents(
          Function(
            At(),
            Identifier(At(), "my_func")
          )
        )

        val invariants = Contents(
          Invariant(At(), Identifier(At(), "my_id"), Option(LiteralString(At(), "true")))
        )
        val types = Contents(
          Type(At(), Identifier(At(), "mytype"), Bool(At())),
          Type(At(), Identifier(At(), "mytype2"), Bool(At()))
        )
        val options = Contents(
          OptionValue(At(), "aggregate", Seq.empty),
          OptionValue(At(), "transient", Seq.empty),
          OptionValue(At(), "kind", Seq(LiteralString(At(), "concept")))
        )

        val entityContents: Contents[EntityContents] =
          Contents
            .empty[EntityContents](
              states.size + types.size + handlers.size + functions.size + invariants.size
            )
            .merge(options)
            .merge(states)
            .merge(types)
            .merge(handlers)
            .merge(functions)
            .merge(invariants)
            .asInstanceOf[Contents[EntityContents]]
        val entity = AST.Entity(
          loc = At(),
          id = Identifier(At(), "foo"),
          contents = entityContents
        )

        entity.contents must be(entityContents)
      }
    }
  }
  "Function" should {
    "be structurally correct" in {
      function.id.value mustBe "Lambda"
      function.statements mustBe statements
      function.input must be(empty)
      function.output must be(empty)
      function.brief must be(brief)
      function.descriptions must be(descriptions)
    }
  }

  "Group" should {
    val group = Group(At(), "panel", Identifier(At(), "42"), Contents.empty())
    "has an alias" in {
      group.alias must be("panel")
    }
  }

  "Handler" should {
    "have some onClauses" in {
      handler.clauses mustBe onClauses
    }
    "be named 'handler'" in {
      handler.id.value mustBe "handler"
    }
  }

  val dataTypeRef: TypeRef = TypeRef(At.empty, "type", PathIdentifier(At.empty, Seq("DataPoint")))
  val inlet: Inlet = Inlet(At.empty, Identifier(At.empty, "in"), dataTypeRef)
  val outlet: Outlet = Outlet(At.empty, Identifier(At.empty, "out"), dataTypeRef)
  val inletRef: InletRef = InletRef(At.empty, PathIdentifier(At.empty, Seq("Sink", "in")))
  val outletRef: OutletRef = OutletRef(At.empty, PathIdentifier(At.empty, Seq("Source", "out")))

  "Inlet" should {
    "have a test" in {
      inlet.id.value mustBe "in"
      inlet.type_ mustBe dataTypeRef
      inlet.format mustBe "inlet in is type DataPoint"
      inlet.isEmpty mustBe true
    }
  }
  "InletJoint" should {
    // The InletJoint node was retired: a Connector now names its inlet end directly via an
    // InletRef, so the join is the Connector's `to` field.
    "have a test" in {
      val connector =
        Connector(At.empty, Identifier(At.empty, "channel"), outletRef, inletRef)
      connector.to mustBe inletRef
      connector.to.pathId.value mustBe Seq("Sink", "in")
      inletRef.format mustBe "inlet Sink.in"
    }
  }
  "Input" should {
    "have a test" in {
      val input = Input(At.empty, "form", Identifier(At.empty, "Signup"), "takes", dataTypeRef)
      input.id.value mustBe "Signup"
      input.takeIn mustBe dataTypeRef
      input.kind mustBe "form"
      input.identify mustBe "takes Signup"
      input.format mustBe "form takes type DataPoint"
    }
  }
  "Invariant" should {
    "have a test" in {
      val condition = LiteralString(At.empty, "x must be positive")
      val invariant = Invariant(At.empty, Identifier(At.empty, "Positive"), Some(condition))
      invariant.id.value mustBe "Positive"
      invariant.condition mustBe Some(condition)
      invariant.isEmpty mustBe false
      Invariant(At.empty, Identifier(At.empty, "Bare")).isEmpty mustBe true
    }
  }
  "OnMessageClause" should {
    "have a test" in {
      val omc = OnMessageClause(At.empty, messageRef, None, None, statements)
      omc.msg mustBe messageRef
      omc.from mustBe empty
      omc.binding mustBe empty // A55: the local message binding is optional
      // The synthetic id is the message reference's format, which is what round-trips to source
      omc.id.value mustBe messageRef.format
      omc.statements mustBe statements
    }
  }
  "OnOtherClause" should {
    "have a test" in {
      val ooc = OnOtherClause(At.empty, None, None, statements)
      ooc.id.value mustBe "other"
      ooc.kind mustBe "On Other"
      ooc.statements mustBe statements
      OnOtherClause(At.empty, None, None).statements mustBe empty
    }
  }
  "Outlet" should {
    "have a test" in {
      outlet.id.value mustBe "out"
      outlet.type_ mustBe dataTypeRef
      outlet.format mustBe "outlet out is type DataPoint"
      outlet.isEmpty mustBe true
    }
  }
  "OutletJoint" should {
    // The OutletJoint node was retired: a Connector now names its outlet end directly via an
    // OutletRef, so the join is the Connector's `from` field.
    "have a test" in {
      val connector =
        Connector(At.empty, Identifier(At.empty, "channel"), outletRef, inletRef)
      connector.from mustBe outletRef
      connector.from.pathId.value mustBe Seq("Source", "out")
      outletRef.format mustBe "outlet Source.out"
    }
  }
  "Output" should {
    "have a test" in {
      val output = Output(At.empty, "document", Identifier(At.empty, "Receipt"), "shows", typeRef)
      output.id.value mustBe "Receipt"
      output.putOut mustBe typeRef
      output.kind mustBe "document"
      output.identify mustBe "shows Receipt"
      output.format mustBe s"document Receipt shows ${typeRef.format}"
    }
  }
  "Pipe" should {
    // The Pipe node was renamed to Connector; it still joins one Outlet to one Inlet.
    "have a test" in {
      val connector = Connector(At.empty, Identifier(At.empty, "channel"), outletRef, inletRef)
      connector.id.value mustBe "channel"
      connector.format mustBe "connector channel"
      connector.isEmpty mustBe false
      Connector(
        At.empty,
        Identifier.empty,
        OutletRef(At.empty, PathIdentifier.empty),
        InletRef(At.empty, PathIdentifier.empty)
      ).isEmpty mustBe true
    }
  }
  "Plant" should {
    // The Plant node was retired: a Context is the streaming container, holding the streamlets
    // and the connectors that join them.
    "have a test" in {
      val source = Streamlet(At.empty, Identifier(At.empty, "Source"), None, Contents(outlet))
      val sink = Streamlet(At.empty, Identifier(At.empty, "Sink"), None, Contents(inlet))
      val connector = Connector(At.empty, Identifier(At.empty, "channel"), outletRef, inletRef)
      val plant =
        Context(At.empty, Identifier(At.empty, "plant"), Contents(source, sink, connector))
      // [4.1]: a streamlet is any processor WITH PORTLETS, so this asserts on the port-bearing set.
      plant.streamlets.map(_.id.value) mustBe Seq("Source", "Sink")
      plant.connectors.map(_.id.value) mustBe Seq("channel")
      source.effectiveShape mustBe Source(At.empty)
      sink.effectiveShape mustBe Sink(At.empty)
    }
  }
  "Processor" should {
    "have a test" in {
      // Every Processor is port-bearing; its shape is derived from arity unless ascribed.
      val derived = Context(At.empty, Identifier(At.empty, "derived"), Contents(inlet, outlet))
      derived.isProcessor mustBe true
      derived.inlets mustBe Seq(inlet)
      derived.outlets mustBe Seq(outlet)
      derived.arityShape mustBe Flow(At.empty)
      derived.ascribedShape mustBe empty
      derived.effectiveShape mustBe Flow(At.empty)

      // An ascribed shape wins over the arity-derived one
      val ascribed = derived.copy(ascribedShape = Some(Merge(At.empty)))
      ascribed.arityShape mustBe Flow(At.empty)
      ascribed.effectiveShape mustBe Merge(At.empty)

      // A processor with no ports at all is Void, not a crash
      Context(At.empty, Identifier(At.empty, "empty")).arityShape mustBe Void(At.empty)
    }
  }
  "Projector" should {
    "have a test" in {
      val repoRef = RepositoryRef(At.empty, PathIdentifier(At.empty, Seq("Store")))
      val projector =
        Projector(At.empty, Identifier(At.empty, "projector"), Contents(repoRef, handler))
      projector.id.value mustBe "projector"
      projector.format mustBe s"${Keyword.projector} 'projector'"
      projector.repositories mustBe Seq(repoRef)
      projector.handlers mustBe Seq(handler)
      projector.isProcessor mustBe true
    }
  }
  "Repository" should {
    "have a test" in {
      val repository = Repository(At.empty, Identifier(At.empty, "repository"), Contents(handler))
      repository.id.value mustBe "repository"
      repository.format mustBe s"${Keyword.repository} 'repository'"
      repository.handlers mustBe Seq(handler)
      repository.isProcessor mustBe true
      repository.effectiveShape mustBe Void(At.empty)
    }
  }

  "Root(Nil)" should {
    "be at location 0,0" in { Root(At.empty, Contents.empty()).loc must be(At.empty) }
    "have 'Root' id" in { Root(At.empty, Contents.empty()).identify must be("Root") }
    "have no modules" in { Root(At.empty, Contents.empty()).modules must be(empty) }
    "have no domains" in { Root(At.empty, Contents.empty()).domains must be(empty) }
    "have no comments" in { Root(At.empty, Contents.empty()).comments must be(empty) }
    "have no authors" in { Root(At.empty, Contents.empty()).authors must be(empty) }
    "identify as root container" in {
      Root(At.empty, Contents.empty()).isRootContainer mustBe true
    }
  }

  "Saga" should {
    "have a test" in {
      val saga = Saga(At.empty, Identifier(At.empty, "saga"), contents = Contents(sagaStep))
      saga.id.value mustBe "saga"
      saga.format mustBe s"${Keyword.saga} 'saga'"
      saga.sagaSteps mustBe Seq(sagaStep)
      saga.input mustBe empty
      saga.output mustBe empty
      saga.isEmpty mustBe false
      Saga(At.empty, Identifier(At.empty, "empty")).isEmpty mustBe true
    }
  }
  "SagaStep" should {
    "have a test" in {
      val doIt = Contents[Statements](PromptStatement(At.empty, LiteralString(At.empty, "do it")))
      val undoIt =
        Contents[Statements](PromptStatement(At.empty, LiteralString(At.empty, "undo it")))
      val step = SagaStep(At.empty, Identifier(At.empty, "step"), doIt, undoIt)
      step.id.value mustBe "step"
      step.format mustBe "step step"
      step.doStatements mustBe doIt
      step.undoStatements mustBe undoIt
      sagaStep.doStatements mustBe empty
      sagaStep.undoStatements mustBe empty
    }
  }
  "State" should { "format correctly" in { state.format mustBe "state state" } }
  "Story Case" should {
    "format correctly" in { storyCase.format mustBe "case story-case" }
  }

  "Term" should {
    "format correctly" in {
      term.format mustBe s"${Keyword.term} ${term.id.format}"
    }
  }

  "Context intention" should {
    "carry an optional intention and parse intention keywords" in {
      val c = Context(
        At.empty,
        Identifier(At.empty, "C"),
        Contents.empty(),
        intention = Some(Intention.Application)
      )
      c.intention must be(Some(Intention.Application))
      Context(At.empty, Identifier(At.empty, "P"), Contents.empty()).intention must be(None)
      Intention.fromKeyword("gateway") must be(Some(Intention.Gateway))
      Intention.fromKeyword("service").map(_.keyword) must be(Some("service"))
      Intention.fromKeyword("bogus") must be(None)
    }
  }

  "Processor ports" should {
    "expose inlets and outlets on every processor kind (via the Processor base)" in {
      val inlet = Inlet(
        At.empty,
        Identifier(At.empty, "in"),
        TypeRef(At.empty, "type", PathIdentifier(At.empty, Seq("Cmd")))
      )
      val outlet = Outlet(
        At.empty,
        Identifier(At.empty, "out"),
        TypeRef(At.empty, "type", PathIdentifier(At.empty, Seq("Evt")))
      )
      // Entity is a Processor but not a Streamlet; ports must still be accepted.
      val entity = Entity(At.empty, Identifier(At.empty, "E"), Contents(inlet, outlet))
      entity.inlets must be(Seq(inlet))
      entity.outlets must be(Seq(outlet))
    }
  }

  "Streamlet shape" should {
    "canonicalize synonyms and derive effectiveShape from arity" in {
      StreamletShape.fromKeyword("cascade", At.empty).map(_.keyword) must be(Some("flow"))
      StreamletShape.fromKeyword("fanout", At.empty).map(_.keyword) must be(Some("split"))
      StreamletShape.fromKeyword("fanin", At.empty).map(_.keyword) must be(Some("merge"))
      StreamletShape.fromKeyword("bogus", At.empty) must be(None)
      val i = Inlet(
        At.empty,
        Identifier(At.empty, "i"),
        TypeRef(At.empty, "type", PathIdentifier(At.empty, Seq("T")))
      )
      val o = Outlet(
        At.empty,
        Identifier(At.empty, "o"),
        TypeRef(At.empty, "type", PathIdentifier(At.empty, Seq("T")))
      )
      val p = Streamlet(At.empty, Identifier(At.empty, "P"), None, Contents(i, o))
      p.effectiveShape.keyword must be("flow") // 1 in + 1 out
      val src = Streamlet(At.empty, Identifier(At.empty, "S"), None, Contents(o))
      src.effectiveShape.keyword must be("source") // 1 out + 0 in
    }
  }
}
