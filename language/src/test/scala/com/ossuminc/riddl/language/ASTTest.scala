/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language

import com.ossuminc.riddl.language.parsing.Keyword
import com.ossuminc.riddl.language.AST.*
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.nio.file.Path
import java.net.URI

/** Unit Tests For Abstract Syntax Tree */
class ASTTest extends AnyWordSpec with Matchers {

  "Descriptions" should {
    "have empty Description.empty" in {
      Description.empty.format mustBe ("")
    }
    "have empty BlockDescription().format" in {
      BlockDescription().format mustBe ("")
    }
    "have useful FileDescription" in {
      val fd = FileDescription(At(), Path.of("."))
      fd.format must include("/")
      fd.format must include(".")
    }
    "have useful URLDescription" in {
      val url_text = "https://raw.githubusercontent.com/ossuminc/riddl/main/project/plugins.sbt"
      val url: java.net.URL = URI.create(url_text).toURL
      val ud = URLDescription(At(), url)
      ud.loc.isEmpty mustBe (true)
      ud.url.toExternalForm must be(url_text)
      ud.format must be(url.toExternalForm)
      val head = ud.lines.head
      head.s must include("sbt-ossuminc")
    }
  }

  "Domain" should {
    "return anonymous name when empty" in {
      val domain = Domain(At(), Identifier.empty)
      domain.identify must be("Anonymous Domain")
    }
  }

  "Types" should {
    "support domain definitions" in {
      Domain((0, 0), Identifier((1, 1), "foo")) must be
      Domain((0, 0), Identifier((1, 1), "foo"))
    }
    "support all type constructs" in {
      AliasedTypeExpression(0 -> 0, "record", PathIdentifier(0 -> 0, Seq("Foo"))).format mustBe "record Foo"
      Enumeration((0, 0), Seq.empty[Enumerator]).format mustBe "{  }"
      Alternation((0, 0), Seq.empty[AliasedTypeExpression]).format mustBe "one of {  }"
      Aggregation((0, 0), Seq.empty[Field]).format mustBe "{  }"
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
      URL((0, 0)).format mustBe "URL(\"https\")"
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
  val application: Application = Application(
    At.empty,
    Identifier(At.empty, "application"),
    contents = Seq(authorRef)
  )
  val author: Author = Author(
    At.empty,
    Identifier(At(), "Reid"),
    LiteralString.empty,
    LiteralString.empty
  )
  val brief: Option[LiteralString] = Some(LiteralString(At.empty, "brief"))
  val description: Option[Description] = Some(BlockDescription(At.empty, Seq(LiteralString(At.empty, "Description"))))
  val entityRef: EntityRef = EntityRef(At.empty, PathIdentifier(At.empty, Seq("Entity")))
  val aggregate: AggregateUseCaseTypeExpression = AggregateUseCaseTypeExpression(
    At.empty,
    CommandCase,
    Seq(Field(At(), Identifier(At(), "foo"), String_(At(), None, None)))
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
  val statements: Seq[Statement] = Seq(
    ArbitraryStatement(At.empty, LiteralString(At.empty, "arbitrary")),
    BecomeStatement(At.empty, entityRef, HandlerRef(At(), PathIdentifier(At(), Seq("Entity")))),
    CallStatement(At.empty, FunctionRef(At(), PathIdentifier(At(), Seq("Lambda")))),
    CodeStatement(At.empty, language = LiteralString(At.empty, "scala"), body = "def f[A](x: A): A"),
    ErrorStatement(At.empty, LiteralString(At.empty, "error message")),
    FocusStatement(At.empty, GroupRef(At.empty, "panel", PathIdentifier(At.empty, Seq("panel")))),
    ForEachStatement(At.empty, fieldRef, Seq.empty),
    IfThenElseStatement(At.empty, LiteralString.empty, Seq.empty, Seq.empty),
    MorphStatement(At.empty, entityRef, StateRef(At.empty, PathIdentifier(At(), Seq("state"))), messageRef),
    ReadStatement(At.empty, "read", LiteralString(At(), "something"), typeRef, LiteralString(At(), "foo")),
    ReplyStatement(At.empty, messageRef),
    ReturnStatement(At.empty, LiteralString(At(), "result")),
    StopStatement(At.empty),
    TellStatement(At.empty, messageRef, entityRef),
    WriteStatement(At.empty, "put", LiteralString(At(), "what"), typeRef)
  )
  val function: Function =
    Function(At.empty, Identifier(At(), "Lambda"), None, None, Seq.empty, statements, brief, description)
  val functionRef: FunctionRef = FunctionRef(At.empty, PathIdentifier(At.empty, Seq("Lambda")))
  val onClauses: Seq[OnClause] = Seq(
    OnInitializationClause(At.empty, statements, brief, description),
    OnMessageClause(At.empty, messageRef, None, statements, brief, description),
    OnOtherClause(At.empty, statements, brief, description),
    OnTerminationClause(At.empty, statements, brief, description)
  )
  val handler: Handler = Handler(At.empty, Identifier(At(), "handler"), onClauses, brief, description)
  val entity: Entity = Entity(At.empty, Identifier(At.empty, "Entity"), Seq(handler), brief, description)
  val handlerRef: HandlerRef = HandlerRef(At.empty, PathIdentifier(At(), Seq("handler")))
  val sagaStep: SagaStep = SagaStep(At.empty, Identifier(At.empty, "sagaStep"))
  val state: State = State(At.empty, Identifier(At.empty, "state"), TypeRef())
  val stateRef: StateRef = StateRef(At.empty, PathIdentifier(At(), Seq("state")))
  val storyCase: UseCase = UseCase(At.empty, Identifier(At.empty, "story-case"))
  val epic: Epic = Epic(At.empty, Identifier(At.empty, "epic"))
  val term: Term = Term(At.empty, Identifier(At.empty, "term"))

  "User" should {
    "have a test" in {
      actor.format mustBe s"user ${actor.id.format} is ${actor.is_a.format}"
    }
  }
  val domain: AST.Domain =
    Domain(At(), Identifier(At(), "test"), contents = Seq(author))
  val context: AST.Context = Context(At(), Identifier(At(), "test"))

  "Adaptor" should {
    "pass simple tests" in {
      adaptor.loc mustBe At.empty
      adaptor.id.value mustBe "adaptor"
      adaptor.direction mustBe InboundAdaptor(At.empty)
      adaptor.context.pathId.value mustBe Seq("a", "b", "context")
    }
  }
  "Application" should {
    "have a test" in {
      application.loc mustBe At.empty
      application.id.value mustBe "application"
      application.contents.filter[AuthorRef] mustBe Seq(authorRef)
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
      val authors = AST.findAuthors(application, Seq(domain))
      authors mustBe Seq(authorRef)
    }
  }

  "Context" should {
    "correctly identify emptiness" in { context.contents mustBe empty }
    "correctly identify non-emptiness" in {
      val types = List(Type(At(), Identifier(At(), "A"), Bool(At())))
      Context(At(), Identifier(At(), "test"), contents = types).contents mustBe
        types
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
      val types = List(Type(At(), Identifier(At(), "A"), Bool(At())))
      Domain(At(), Identifier(At(), "test"), contents = types).contents mustBe
        types
    }
  }

  "Epic" should { "format correctly" in { epic.format mustBe "Epic epic" } }

  "Entity" should {
    "contents" should {
      "contain all contents" in {
        val states = Seq(
          State(
            At(),
            Identifier(At(), "bar"),
            TypeRef()
          )
        )
        val handlers = Seq(Handler(At(), Identifier(At(), "con")))

        val functions = Seq(
          Function(
            At(),
            Identifier(At(), "my_func"),
            None
          )
        )

        val invariants = Seq(
          Invariant(At(), Identifier(At(), "my_id"), Option(LiteralString(At(), "true")), None)
        )
        val types = Seq(
          Type(At(), Identifier(At(), "mytype"), Bool(At())),
          Type(At(), Identifier(At(), "mytype2"), Bool(At()))
        )
        val options = Seq(
          OptionValue(At(), "aggregate", Seq.empty),
          OptionValue(At(), "transient", Seq.empty),
          OptionValue(At(), "kind", Seq(LiteralString(At(), "concept")))
        )
        val entity = AST.Entity(
          loc = At(),
          id = Identifier(At(), "foo"),
          contents = options ++ states ++ types ++ handlers ++ functions ++ invariants,
          description = None
        )

        entity.contents.toSet mustBe
          (options ++ states ++ types ++ handlers ++ functions ++ invariants).toSet
      }
    }
  }
  "Function" should {
    "be structurally correct" in {
      function.id.value mustBe "Lambda"
      function.statements mustBe statements
      function.input mustBe empty
      function.output mustBe empty
      function.brief mustBe brief
      function.description mustBe description
    }
  }

  "Group" should {
    val group = Group(At(), "panel", Identifier(At(), "42"), None, Seq.empty)
    "has an alias" in {
      group.alias must be("panel")
    }
  }

  "Handler" should {
    "have some onClauses" in {
      handler.contents mustBe onClauses
    }
    "be named 'handler'" in {
      handler.id.value mustBe "handler"
    }
  }

  "Include" should {
    "identify as root container, etc" in {
      val incl = Include()
      incl.isRootContainer mustBe true
      incl.loc mustBe At.empty
      incl.format mustBe "include \"\""
    }
  }

  "Inlet" should { "have a test" in { pending } }
  "InletJoint" should { "have a test" in { pending } }
  "Input" should { "have a test" in { pending } }
  "Invariant" should { "have a test" in { pending } }
  "OnMessageClause" should { "have a test" in { pending } }
  "OnOtherClause" should { "have a test" in { pending } }
  "Outlet" should { "have a test" in { pending } }
  "OutletJoint" should { "have a test" in { pending } }
  "Output" should { "have a test" in { pending } }
  "Pipe" should { "have a test" in { pending } }
  "Plant" should { "have a test" in { pending } }
  "Processor" should { "have a test" in { pending } }
  "Projector" should { "have a test" in { pending } }
  "Repository" should { "have a test" in { pending } }

  "RootContainer" should {
    "be at location 0,0" in { Root(Nil).loc mustBe At.empty }
    "have no description" in { Root(Nil).description mustBe None }
    "have no brief" in { Root(Nil).brief mustBe None }
    "have no id" in { Root(Nil).identify mustBe "Root" }
    "identify as root container" in {
      Root(Nil).isRootContainer mustBe true
    }
  }

  "Saga" should { "have a test" in {} }
  "SagaStep" should { "have a test" in { pending } }
  "State" should { "format correctly" in { state.format mustBe "state state" } }
  "Story Case" should {
    "format correctly" in { storyCase.format mustBe "case story-case" }
  }

  "Term" should {
    "format correctly" in {
      term.format mustBe s"${Keyword.term} ${term.id.format} is None"
    }
  }
}
