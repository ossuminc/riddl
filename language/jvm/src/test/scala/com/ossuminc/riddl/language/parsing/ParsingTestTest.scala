/*
 * Copyright 2022 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.parsing.RiddlParserInput

import org.scalatest.TestData

class ParsingTestTest extends ParsingTest {

  "ParsingTest" should {

    "parse[Pipe]" in { (td: TestData) =>
      val rpi = RiddlParserInput("""connector foo is { ??? }""", td)
      parseDefinition[Connector](rpi) match {
        case Right((pipe, _)) =>
          val expected = Connector((1, 1, rpi), Identifier((1, 11, rpi), "foo"))
          pipe mustBe expected
        case Left(errors) => fail(errors.format)
      }
    }

    "parse[Saga]" in { (td: TestData) =>
      val rpi = RiddlParserInput("""saga foo is { ??? }""")
      parseDefinition[Saga](rpi) match {
        case Right((saga, _)) =>
          val expected = Saga((1, 1, rpi), Identifier((1, 6, rpi), "foo"))
          saga mustBe expected
        case Left(errors) => fail(errors.format)
      }
    }

    "parseTopLevelDomain[Domain]" in { (td: TestData) =>
      val input = RiddlParserInput("domain foo is { ??? }", td)
      parseTopLevelDomain[Domain](input, _.domains.head) match {
        case Left(messages)     => fail(messages.format)
        case Right((domain, _)) => domain mustBe empty
      }
    }

    "parseTopLevelDomain[Application]" in { (td: TestData) =>
      val input = RiddlParserInput("domain foo is { application X is { ??? } }", td)
      parseTopLevelDomain[Application](input, _.domains.head.applications.head) match {
        case Left(messages)  => fail(messages.format)
        case Right((typ, _)) => typ.id.value mustBe "X"
      }
    }

    "parseTopLevelDomain[Epic]" in { (td: TestData) =>
      val input = RiddlParserInput("domain foo is { epic X is { ??? } }", td)
      parseTopLevelDomain[Epic](input, _.domains.head.epics.head) match {
        case Left(messages)  => fail(messages.format)
        case Right((typ, _)) => typ.id.value mustBe "X"
      }
    }

    "parseTopLevelDomain[Type]" in { (td: TestData) =>
      val input = RiddlParserInput("domain foo is { type X is String }", td)
      parseTopLevelDomain[Type](input, _.domains.head.types.head) match {
        case Left(messages)  => fail(messages.format)
        case Right((typ, _)) => typ.id.value mustBe "X"
      }
    }

    "parseTopLevelDomain[Context]" in { (td: TestData) =>
      val input = RiddlParserInput("domain foo is { context X is { ??? } }", td)
      parseTopLevelDomain[Context](input, _.domains.head.contexts.head) match {
        case Left(messages)  => fail(messages.format)
        case Right((typ, _)) => typ.id.value mustBe "X"
      }
    }

    "parseTopLevelDomain[Entity]" in { (td: TestData) =>
      val input = RiddlParserInput("domain foo is { context C is { entity X is { ??? } } }", td)
      parseTopLevelDomain[Entity](input, _.domains.head.contexts.head.entities.head) match {
        case Left(messages)  => fail(messages.format)
        case Right((typ, _)) => typ.id.value mustBe "X"
      }
    }

    "parseTopLevelDomain[Adaptor]" in { (td: TestData) =>
      val input = RiddlParserInput("domain foo is { context C is { adaptor X to context C is { ??? } } }", td)
      parseTopLevelDomain[Adaptor](input, _.domains.head.contexts.head.adaptors.head) match {
        case Left(messages)  => fail(messages.format)
        case Right((typ, _)) => typ.id.value mustBe "X"
      }
    }

    "parseTopLevelDomain[Function]" in { (td: TestData) =>
      val input = RiddlParserInput("domain foo is { context C is { function X is { ??? } } }", td)
      parseTopLevelDomain[Function](input, _.domains.head.contexts.head.functions.head) match {
        case Left(messages)  => fail(messages.format)
        case Right((typ, _)) => typ.id.value mustBe "X"
      }
    }

    "parseTopLevelDomain[Saga]" in { (td: TestData) =>
      val input = RiddlParserInput("domain foo is { context C is { saga X is { ??? } } }", td)
      parseTopLevelDomain[Saga](input, _.domains.head.contexts.head.sagas.head) match {
        case Left(messages)  => fail(messages.format)
        case Right((typ, _)) => typ.id.value mustBe "X"
      }
    }

    "parseTopLevelDomain[Processor]" in { (td: TestData) =>
      val input = RiddlParserInput("domain foo is { context C is { source X is { ??? } } }", td)
      parseTopLevelDomain[Streamlet](input, _.domains.head.contexts.head.streamlets.head) match {
        case Left(messages)  => fail(messages.format)
        case Right((src, _)) => src.id.value mustBe "X"
      }
    }

    "parseTopLevelDomain[Projector]" in { (td: TestData) =>
      val input = RiddlParserInput("domain foo is { context C is { projector X is { ??? } } }", td)
      parseTopLevelDomain[Projector](input, _.domains.head.contexts.head.projectors.head) match {
        case Left(messages)  => fail(messages.format)
        case Right((src, _)) => src.id.value mustBe "X"
      }
    }

    "parseTopLevelDomains" in { (td: TestData) =>
      val input = RiddlParserInput("domain foo is { ??? }", td)
      parseTopLevelDomains(input) match {
        case Left(messages) => fail(messages.format)
        case Right(root)    => root.contents mustNot be(empty)
      }
    }
    "parseDomainDefinition" in { (td: TestData) =>
      val input = RiddlParserInput("domain foo { type I is Integer }", td)
      parseDomainDefinition[Type](
        input,
        _.types.head
      ) match {
        case Left(messages)  => fail(messages.format)
        case Right((typ, _)) => typ.id.value must be("I")
      }
    }
    "parseDefinition[Domain,Type]" in { (td: TestData) =>
      val input = RiddlParserInput("domain foo { type I is Integer }", td)
      parseDefinition[Domain, Type](input, _.types.head) match {
        case Left(messages)  => fail(messages.format)
        case Right((typ, _)) => typ.id.value must be("I")
      }
    }
    "parseDefinition[Domain,Term]" in { (td: TestData) =>
      val input = RiddlParserInput("domain foo { term X is briefly \"X\" }", td)
      parseDefinition[Domain, Term](input, _.terms.head) match {
        case Left(messages)  => fail(messages.format)
        case Right((typ, _)) => typ.id.value must be("X")
      }
    }
    "parseDefinition[Function]" in { (td: TestData) =>
      parseDefinition[Function]("function foo { ??? }") match {
        case Left(messages)  => fail(messages.format)
        case Right((fun, _)) => fun.id.value must be("foo")

      }
    }
    "parseInContext[Term]" in { (td: TestData) =>
      val input = RiddlParserInput("term X is briefly \"X\"", td)
      parseInContext[Term](input, _.terms.head) match {
        case Left(messages)        => fail(messages.format)
        case Right((typ: Term, _)) => typ.id.value must be("X")
      }
    }
  }
}
