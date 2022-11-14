/*
 * Copyright 2022 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.testkit
import com.reactific.riddl.language.AST.*

class ParsingTestTest extends ParsingTest {

  "ParsingTest" should {

    "parseTopLevelDomain[Domain]" in {
      parseTopLevelDomain[Domain](
        "domain foo is { ??? }",
        _.contents.head
      ) match {
        case Left(messages)     => fail(messages.format)
        case Right((domain, _)) => domain mustBe (empty)
      }
    }

    "parseTopLevelDomain[Application]" in {
      parseTopLevelDomain[Application](
        "domain foo is { application X is { ??? } }",
        _.contents.head.applications.head
      ) match {
        case Left(messages)  => fail(messages.format)
        case Right((typ, _)) => typ.id.value mustBe ("X")
      }
    }

    "parseTopLevelDomain[Story]" in {
      parseTopLevelDomain[Story](
        "domain foo is { story X is { ??? } }",
        _.contents.head.stories.head
      ) match {
        case Left(messages)  => fail(messages.format)
        case Right((typ, _)) => typ.id.value mustBe ("X")
      }
    }

    "parseTopLevelDomain[Plant]" in {
      parseTopLevelDomain[Plant](
        "domain foo is { plant X is { ??? } }",
        _.contents.head.plants.head
      ) match {
        case Left(messages)  => fail(messages.format)
        case Right((typ, _)) => typ.id.value mustBe ("X")
      }
    }

    "parseTopLevelDomain[Type]" in {
      parseTopLevelDomain[Type](
        "domain foo is { type X is String }",
        _.contents.head.types.head
      ) match {
        case Left(messages)  => fail(messages.format)
        case Right((typ, _)) => typ.id.value mustBe ("X")
      }
    }

    "parseTopLevelDomain[Context]" in {
      parseTopLevelDomain[Context](
        "domain foo is { context X is { ??? } }",
        _.contents.head.contexts.head
      ) match {
        case Left(messages)  => fail(messages.format)
        case Right((typ, _)) => typ.id.value mustBe ("X")
      }
    }

    "parseTopLevelDomain[Entity]" in {
      parseTopLevelDomain[Entity](
        "domain foo is { context C is { entity X is { ??? } } }",
        _.contents.head.contexts.head.entities.head
      ) match {
        case Left(messages)  => fail(messages.format)
        case Right((typ, _)) => typ.id.value mustBe ("X")
      }
    }

    "parseTopLevelDomain[Adaptor]" in {
      parseTopLevelDomain[Adaptor](
        "domain foo is { context C is { adaptor X to context C is { ??? } } }",
        _.contents.head.contexts.head.adaptors.head
      ) match {
        case Left(messages)  => fail(messages.format)
        case Right((typ, _)) => typ.id.value mustBe ("X")
      }
    }

    "parseTopLevelDomain[Function]" in {
      parseTopLevelDomain[Function](
        "domain foo is { context C is { function X is { ??? } } }",
        _.contents.head.contexts.head.functions.head
      ) match {
        case Left(messages)  => fail(messages.format)
        case Right((typ, _)) => typ.id.value mustBe ("X")
      }
    }

    "parseTopLevelDomain[Saga]" in {
      parseTopLevelDomain[Saga](
        "domain foo is { context C is { saga X is { ??? } } }",
        _.contents.head.contexts.head.sagas.head
      ) match {
        case Left(messages)  => fail(messages.format)
        case Right((typ, _)) => typ.id.value mustBe ("X")
      }
    }

    /*

case x if x == classOf[AST.Invariant]   => invariant(_)
case x if x == classOf[AST.Processor]   => processor(_)
case x if x == classOf[AST.Projection]  => projection(_)
case x if x == classOf[AST.Pipe]        => pipeDefinition(_)
case x if x == classOf[AST.InletJoint]  => joint(_)
case x if x == classOf[AST.OutletJoint] => joint(_)
case x if x == classOf[AST.Example]     => example(_)
     */

    "parseTopLevelDomains" in {
      parseTopLevelDomains("domain foo is { ??? }") match {
        case Left(messages) => fail(messages.format)
        case Right(root)    => root.contents mustNot be(empty)
      }
    }
    "parseDomainDefinition" in {
      parseDomainDefinition[Type](
        "domain foo { type I is Integer }",
        _.types.head
      ) match {
        case Left(messages)  => fail(messages.format)
        case Right((typ, _)) => typ.id.value must be("I")
      }
    }
    "parseDefinition[Domain,Type]" in {
      parseDefinition[Domain, Type](
        "domain foo { type I is Integer }",
        _.types.head
      ) match {
        case Left(messages)  => fail(messages.format)
        case Right((typ, _)) => typ.id.value must be("I")
      }
    }
    "parseDefinition[Domain,Term]" in {
      parseDefinition[Domain, Term](
        "domain foo { term X is briefly \"X\" }",
        _.terms.head
      ) match {
        case Left(messages)  => fail(messages.format)
        case Right((typ, _)) => typ.id.value must be("X")
      }
    }
    "parseDefinition[Function]" in {
      parseDefinition[Function](
        "function foo { ??? }"
      ) match {
        case Left(messages)  => fail(messages.format)
        case Right((fun, _)) => fun.id.value must be("foo")

      }
    }
    "parseInContext[Term]" in {
      parseInContext[Term](
        "term X is briefly \"X\"",
        _.terms.head
      ) match {
        case Left(messages)  => fail(messages.format)
        case Right((typ, _)) => typ.id.value must be("X")
      }
    }
  }
}

