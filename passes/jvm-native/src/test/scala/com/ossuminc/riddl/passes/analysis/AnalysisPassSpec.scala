/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.analysis

import com.ossuminc.riddl.language.parsing.{RiddlParserInput, TopLevelParser}
import com.ossuminc.riddl.utils.{pc, ec}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.must.Matchers

class AnalysisPassSpec extends AnyWordSpec with Matchers {

  private val simpleModel = """
    |domain TestDomain {
    |  context TestContext {
    |    type TestType = String
    |    entity TestEntity {
    |      state main of TestType
    |    }
    |  }
    |}
    |""".stripMargin

  private val modelWithRelationships = """
    |domain Shop {
    |  context Orders {
    |    type OrderId = Id(Orders.Order)
    |    type CustomerId = Id(Customers.Customer)
    |    type OrderState = record { orderId: OrderId, customerId: CustomerId }
    |
    |    entity Order {
    |      state main of OrderState
    |    }
    |  }
    |
    |  context Customers {
    |    type CustomerId = Id(Customers.Customer)
    |    type CustomerState = record { id: CustomerId, name: String }
    |
    |    entity Customer {
    |      state main of CustomerState
    |    }
    |  }
    |}
    |""".stripMargin

  "AnalysisPass" should {
    "analyze a simple model" in {
      val input = RiddlParserInput(simpleModel, "test")
      TopLevelParser.parseInput(input) match
        case Left(errors) => fail(s"Parse failed: ${errors.format}")
        case Right(root) =>
          val result = AnalysisPass.analyze(root)

          result.token.value must not be empty
          result.metadata.rootDomainName mustBe Some("TestDomain")
          result.domains must have size 1
          result.contexts must have size 1
          result.entities must have size 1
    }

    "collect statistics" in {
      val input = RiddlParserInput(simpleModel, "test")
      TopLevelParser.parseInput(input) match
        case Left(errors) => fail(s"Parse failed: ${errors.format}")
        case Right(root) =>
          val result = AnalysisPass.analyze(root)

          result.countByKind("Domain") mustBe 1
          result.countByKind("Context") mustBe 1
          result.countByKind("Entity") mustBe 1
          result.maxDepth must be > 0
    }

    "analyze from parser input" in {
      val input = RiddlParserInput(simpleModel, "test")
      TopLevelParser.parseInput(input) match
        case Left(errors) => fail(s"Parse failed: ${errors.format}")
        case Right(root) =>
          val result = AnalysisPass.analyze(root)
          result.domains must have size 1
    }

    "return errors for invalid input" in {
      val invalidModel = "this is not valid RIDDL"
      val input = RiddlParserInput(invalidModel, "test")

      AnalysisPass.analyzeInput(input) match
        case Left(errors) =>
          errors.hasErrors mustBe true
        case Right(_) =>
          fail("Should have returned errors for invalid input")
    }

    "provide pass creators" in {
      val passes = AnalysisPass.analysisPasses
      passes must have size 8
    }
  }

  "AnalysisResult convenience methods" should {
    "provide parent lookups" in {
      val input = RiddlParserInput(simpleModel, "test")
      TopLevelParser.parseInput(input) match
        case Left(errors) => fail(s"Parse failed: ${errors.format}")
        case Right(root) =>
          val result = AnalysisPass.analyze(root)
          val entity = result.entities.head

          result.contextOf(entity) mustBe defined
          result.parentsOf(entity) must not be empty
    }

    "provide definition accessors by kind" in {
      val input = RiddlParserInput(modelWithRelationships, "test")
      TopLevelParser.parseInput(input) match
        case Left(errors) => fail(s"Parse failed: ${errors.format}")
        case Right(root) =>
          val result = AnalysisPass.analyze(root)
          result.domains must have size 1
          result.contexts must have size 2
          result.entities must have size 2
          result.types must not be empty
          result.sagas mustBe empty
          result.epics mustBe empty
          result.repositories mustBe empty
          result.streamlets mustBe empty
          result.projectors mustBe empty
          result.adaptors mustBe empty
          result.functions mustBe empty
    }

    "provide qualifiedNameOf" in {
      val input = RiddlParserInput(simpleModel, "test")
      TopLevelParser.parseInput(input) match
        case Left(errors) => fail(s"Parse failed: ${errors.format}")
        case Right(root) =>
          val result = AnalysisPass.analyze(root)
          val entity = result.entities.head
          val qn = result.qualifiedNameOf(entity)
          qn must include("TestDomain")
          qn must include("TestContext")
          qn must include("TestEntity")
    }

    "provide context dependency graph" in {
      val input = RiddlParserInput(modelWithRelationships, "test")
      TopLevelParser.parseInput(input) match
        case Left(errors) => fail(s"Parse failed: ${errors.format}")
        case Right(root) =>
          val result = AnalysisPass.analyze(root)
          result.contextDependencies must not be empty
          result.contextDependents must not be empty
    }

    "provide new pass outputs" in {
      val input = RiddlParserInput(simpleModel, "test")
      TopLevelParser.parseInput(input) match
        case Left(errors) => fail(s"Parse failed: ${errors.format}")
        case Right(root) =>
          val result = AnalysisPass.analyze(root)
          result.handlerCompleteness mustBe a[Seq[?]]
          result.messageFlow mustBe a[MessageFlowOutput]
          result.entityLifecycles mustBe a[Map[?, ?]]
          result.dependencies mustBe a[DependencyOutput]
    }

    "provide glossary entries" in {
      val input = RiddlParserInput(simpleModel, "test")
      TopLevelParser.parseInput(input) match
        case Left(errors) => fail(s"Parse failed: ${errors.format}")
        case Right(root) =>
          val result = AnalysisPass.analyze(root)
          val entries = result.glossaryEntries
          entries must not be empty
          entries.map(_.name) must contain("TestDomain")
          entries.map(_.name) must contain("TestContext")
          entries.map(_.name) must contain("TestEntity")
          entries.foreach { entry =>
            entry.kind must not be empty
            entry.qualifiedPath must not be empty
          }
    }

    "provide incomplete definitions" in {
      val incompleteModel = """
        |domain IncDomain {
        |  context EmptyCtx is { ??? }
        |  context DocCtx is {
        |    type DocType = String
        |  } with { briefly "Has docs" }
        |}
        |""".stripMargin
      val input = RiddlParserInput(incompleteModel, "test")
      TopLevelParser.parseInput(input) match
        case Left(errors) => fail(s"Parse failed: ${errors.format}")
        case Right(root) =>
          val result = AnalysisPass.analyze(root)
          val incomplete = result.incompleteDefinitions
          incomplete.map(_.name) must contain("EmptyCtx")
          incomplete.map(_.name) must not contain "DocCtx"
    }

    "provide message types by kind" in {
      val msgModel = """
        |domain MsgDomain {
        |  context MsgContext {
        |    type CreateOrder = command { orderId: Id(MsgContext), item: String }
        |    type OrderCreated = event { orderId: Id(MsgContext), item: String }
        |    type GetOrder = query { orderId: Id(MsgContext) }
        |    type OrderResult = result { orderId: Id(MsgContext), item: String }
        |  }
        |}
        |""".stripMargin
      val input = RiddlParserInput(msgModel, "test")
      TopLevelParser.parseInput(input) match
        case Left(errors) => fail(s"Parse failed: ${errors.format}")
        case Right(root) =>
          val result = AnalysisPass.analyze(root)
          val msgTypes = result.messageTypes
          msgTypes must not be empty
          val flat = result.messageTypesFlat
          flat.map(_.typ.id.value) must contain("CreateOrder")
          flat.map(_.typ.id.value) must contain("OrderCreated")
          flat.map(_.typ.id.value) must contain("GetOrder")
          flat.map(_.typ.id.value) must contain("OrderResult")
    }

    "provide diagram accessors" in {
      val input = RiddlParserInput(simpleModel, "test")
      TopLevelParser.parseInput(input) match
        case Left(errors) => fail(s"Parse failed: ${errors.format}")
        case Right(root) =>
          val result = AnalysisPass.analyze(root)
          val ctx = result.contexts.head
          val domain = result.domains.head
          result.dataFlowDiagramFor(ctx) mustBe a[Option[?]]
          result.domainDiagramFor(domain) mustBe a[Option[?]]
          result.allDomainDiagrams mustBe a[Map[?, ?]]
    }
  }
}
