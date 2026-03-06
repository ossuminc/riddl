/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.{Contents, *}
import com.ossuminc.riddl.language.Messages.*
import com.ossuminc.riddl.language.At
import com.ossuminc.riddl.utils.{AbstractTestingBasisWithTestData, PlatformContext}
import org.scalatest.TestData

/** Tests for the scope-based parsing API (parseAsDomain,
  * parseAsContext, parseAsEntity, parseAsEpic,
  * parseAsStreamlet).
  */
trait ScopedParsingTest(using PlatformContext) extends AbstractTestingBasisWithTestData {

  "TopLevelParser.parseAsDomain" should {
    "parse context definitions as domain content" in { (td: TestData) =>
      val input = RiddlParserInput(
        """context Accounts is {
          |  entity Account is { ??? }
          |}""".stripMargin,
        td
      )
      TopLevelParser.parseAsDomain(input) match {
        case Left(errors) => fail(errors.format)
        case Right(domain) =>
          domain.id.value mustBe "SyntheticScope"
          domain.contexts must not be empty
          domain.contexts.head.id.value mustBe "Accounts"
      }
    }

    "parse type definitions as domain content" in { (td: TestData) =>
      val input = RiddlParserInput(
        "type UserId is UUID",
        td
      )
      TopLevelParser.parseAsDomain(input) match {
        case Left(errors) => fail(errors.format)
        case Right(domain) =>
          domain.types must not be empty
          domain.types.head.id.value mustBe "UserId"
      }
    }

    "parse multiple definitions as domain content" in { (td: TestData) =>
      val input = RiddlParserInput(
        """type Name is String
          |context Orders is { ??? }
          |user u is "a person"
          |""".stripMargin,
        td
      )
      TopLevelParser.parseAsDomain(input) match {
        case Left(errors) => fail(errors.format)
        case Right(domain) =>
          domain.types.size mustBe 1
          domain.contexts.size mustBe 1
          domain.users.size mustBe 1
      }
    }

    "report errors for invalid domain content" in { (td: TestData) =>
      val input = RiddlParserInput(
        "this is not valid riddl",
        td
      )
      TopLevelParser.parseAsDomain(input) match {
        case Left(_)  => succeed
        case Right(_) => fail("Should have failed to parse")
      }
    }
  }

  "TopLevelParser.parseAsContext" should {
    "parse entity definitions as context content" in { (td: TestData) =>
      val input = RiddlParserInput(
        """entity Account is {
          |  handler input is { ??? }
          |}""".stripMargin,
        td
      )
      TopLevelParser.parseAsContext(input) match {
        case Left(errors) => fail(errors.format)
        case Right(context) =>
          context.id.value mustBe "SyntheticScope"
          context.entities must not be empty
          context.entities.head.id.value mustBe "Account"
      }
    }

    "parse type and handler definitions" in { (td: TestData) =>
      val input = RiddlParserInput(
        """type AccountId is UUID
          |type Balance is Number
          |entity Ledger is { ??? }""".stripMargin,
        td
      )
      TopLevelParser.parseAsContext(input) match {
        case Left(errors) => fail(errors.format)
        case Right(context) =>
          context.types.size mustBe 2
          context.entities.size mustBe 1
      }
    }

    "parse streamlet definitions in context" in { (td: TestData) =>
      val input = RiddlParserInput(
        """source EventStream is {
          |  outlet out is String
          |}""".stripMargin,
        td
      )
      TopLevelParser.parseAsContext(input) match {
        case Left(errors) => fail(errors.format)
        case Right(context) =>
          context.streamlets must not be empty
      }
    }
  }

  "TopLevelParser.parseAsEntity" should {
    "parse handler and state definitions" in { (td: TestData) =>
      val input = RiddlParserInput(
        """type Fields is { name: String }
          |state current of SyntheticScope.Fields
          |handler input is { ??? }""".stripMargin,
        td
      )
      TopLevelParser.parseAsEntity(input) match {
        case Left(errors) => fail(errors.format)
        case Right(entity) =>
          entity.id.value mustBe "SyntheticScope"
          entity.states must not be empty
          entity.handlers must not be empty
          entity.types must not be empty
      }
    }

    "parse function definitions in entity" in { (td: TestData) =>
      val input = RiddlParserInput(
        """function ComputeBalance is {
          |  requires { amount: Number }
          |  returns { balance: Number }
          |  ???
          |}""".stripMargin,
        td
      )
      TopLevelParser.parseAsEntity(input) match {
        case Left(errors) => fail(errors.format)
        case Right(entity) =>
          entity.functions must not be empty
          entity.functions.head.id.value mustBe "ComputeBalance"
      }
    }
  }

  "TopLevelParser.parseAsModule" should {
    "parse domain definitions as module content" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain Inner is { ??? }
          |author Auth is { name is "Test" email is "t@t" }
          |""".stripMargin,
        td
      )
      TopLevelParser.parseAsModule(input) match {
        case Left(errors) => fail(errors.format)
        case Right(module) =>
          module.id.value mustBe "SyntheticScope"
          module.domains must not be empty
          module.domains.head.id.value mustBe "Inner"
      }
    }
  }

  "TopLevelParser.parseAsAdaptor" should {
    "parse handler definitions with provided direction" in { (td: TestData) =>
      val input = RiddlParserInput(
        "handler input is { ??? }",
        td
      )
      val direction = InboundAdaptor(At.empty)
      val ctxRef = ContextRef(
        At.empty,
        PathIdentifier(At.empty, Seq("OtherContext"))
      )
      TopLevelParser.parseAsAdaptor(
        input, direction, ctxRef
      ) match {
        case Left(errors) => fail(errors.format)
        case Right(adaptor) =>
          adaptor.id.value mustBe "SyntheticScope"
          adaptor.handlers must not be empty
      }
    }
  }

  "TopLevelParser.parseAsProjector" should {
    "parse type and handler definitions" in { (td: TestData) =>
      val input = RiddlParserInput(
        """type ViewData is { name: String }
          |handler input is { ??? }""".stripMargin,
        td
      )
      TopLevelParser.parseAsProjector(input) match {
        case Left(errors) => fail(errors.format)
        case Right(projector) =>
          projector.id.value mustBe "SyntheticScope"
          projector.types must not be empty
          projector.handlers must not be empty
      }
    }
  }

  "TopLevelParser.parseAsRepository" should {
    "parse schema and handler definitions" in { (td: TestData) =>
      val input = RiddlParserInput(
        """handler commands is { ??? }
          |type RecordType is { key: String }""".stripMargin,
        td
      )
      TopLevelParser.parseAsRepository(input) match {
        case Left(errors) => fail(errors.format)
        case Right(repository) =>
          repository.id.value mustBe "SyntheticScope"
          repository.handlers must not be empty
          repository.types must not be empty
      }
    }
  }

  "TopLevelParser.parseAsSaga" should {
    "parse saga steps with provided input/output" in { (td: TestData) =>
      val input = RiddlParserInput(
        """step One is {
          |  ???
          |} reverted by {
          |  ???
          |}
          |step Two is {
          |  ???
          |} reverted by {
          |  ???
          |}""".stripMargin,
        td
      )
      TopLevelParser.parseAsSaga(input) match {
        case Left(errors) => fail(errors.format)
        case Right(saga) =>
          saga.sagaSteps.size mustBe 2
          saga.sagaSteps.head.id.value mustBe "One"
      }
    }
  }

  "TopLevelParser.parseAsEpic" should {
    "parse use case definitions with provided user story" in { (td: TestData) =>
      val userStory = UserStory(
        At.empty,
        UserRef(At.empty, PathIdentifier(At.empty, Seq("u"))),
        LiteralString(At.empty, "place an order"),
        LiteralString(At.empty, "they get stuff")
      )
      val input = RiddlParserInput(
        """case HappyPath is {
          |  user u wants "complete checkout"
          |    so that "order is placed"
          |  ???
          |}""".stripMargin,
        td
      )
      TopLevelParser.parseAsEpic(input, userStory) match {
        case Left(errors) => fail(errors.format)
        case Right(epic) =>
          epic.cases must not be empty
          epic.cases.head.id.value mustBe "HappyPath"
          epic.userStory mustBe userStory
      }
    }
  }

  "TopLevelParser.parseAsStreamlet" should {
    "parse handler definitions with provided shape and ports" in { (td: TestData) =>
      val shape = Flow(At.empty)
      val inlets = Seq(
        Inlet(
          At.empty,
          Identifier(At.empty, "in"),
          TypeRef(At.empty, "type", PathIdentifier(At.empty, Seq("String")))
        )
      )
      val outlets = Seq(
        Outlet(
          At.empty,
          Identifier(At.empty, "out"),
          TypeRef(At.empty, "type", PathIdentifier(At.empty, Seq("String")))
        )
      )
      val input = RiddlParserInput(
        """handler input is { ??? }""",
        td
      )
      TopLevelParser.parseAsStreamlet(
        input, shape, inlets, outlets
      ) match {
        case Left(errors) => fail(errors.format)
        case Right(streamlet) =>
          streamlet.shape mustBe a[Flow]
          // Inlets and outlets from caller
          streamlet.inlets.size mustBe 1
          streamlet.outlets.size mustBe 1
          // Handler from parsed content
          streamlet.handlers must not be empty
      }
    }

    "parse with empty inlets and outlets (void-like)" in { (td: TestData) =>
      val shape = Void(At.empty)
      val input = RiddlParserInput(
        """type Config is { key: String }""",
        td
      )
      TopLevelParser.parseAsStreamlet(
        input, shape, Seq.empty, Seq.empty
      ) match {
        case Left(errors) => fail(errors.format)
        case Right(streamlet) =>
          streamlet.shape mustBe a[Void]
          streamlet.inlets mustBe empty
          streamlet.outlets mustBe empty
          streamlet.types must not be empty
      }
    }
  }
}
