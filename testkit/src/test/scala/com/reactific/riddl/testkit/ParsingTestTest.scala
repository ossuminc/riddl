/*
 * Copyright 2022 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.testkit
import com.reactific.riddl.language.AST.*
import com.reactific.riddl.language.parsing.RiddlParserInput

class ParsingTestTest extends ParsingTest {

  "ParsingTest" should {

    "parse[Pipe]" in {
      val rpi = RiddlParserInput("""connector foo is { ??? }""")
      parseDefinition[Connector](rpi) match {
        case Right((pipe, _)) =>
          val expected = Connector((1, 1, rpi), Identifier((1, 11, rpi), "foo"))
          pipe mustBe expected
        case Left(errors) => fail(errors.format)
      }
    }

    "parse[Statement]" in {
      val rpi = RiddlParserInput(""" "statement" """)
      parseDefinition[Statement](rpi) match {
        case Right((oj, _)) =>
          val expected = ArbitraryStatement((1, 1, rpi), Identifier((1, 1, rpi), ""),
            LiteralString((1, 2, rpi), "statement"))
          oj mustBe expected
        case Left(errors) => fail(errors.format)
      }
    }

    "parse[Saga]" in {
      val rpi = RiddlParserInput("""saga foo is { ??? }""")
      parseDefinition[Saga](rpi) match {
        case Right((saga, _)) =>
          val expected = Saga((1, 1, rpi), Identifier((1, 6, rpi), "foo"))
          saga mustBe expected
        case Left(errors) => fail(errors.format)
      }
    }

    "parseTopLevelDomain[Domain]" in {
      parseTopLevelDomain[Domain](
        "domain foo is { ??? }",
        _.domains.head
      ) match {
        case Left(messages)     => fail(messages.format)
        case Right((domain, _)) => domain mustBe empty
      }
    }

    "parseTopLevelDomain[Application]" in {
      parseTopLevelDomain[Application](
        "domain foo is { application X is { ??? } }",
        _.domains.head.applications.head
      ) match {
        case Left(messages)  => fail(messages.format)
        case Right((typ, _)) => typ.id.value mustBe "X"
      }
    }

    "parseTopLevelDomain[Epic]" in {
      parseTopLevelDomain[Epic](
        "domain foo is { epic X is { ??? } }",
        _.domains.head.epics.head
      ) match {
        case Left(messages)  => fail(messages.format)
        case Right((typ, _)) => typ.id.value mustBe "X"
      }
    }

    "parseTopLevelDomain[Type]" in {
      parseTopLevelDomain[Type](
        "domain foo is { type X is String }",
        _.domains.head.types.head
      ) match {
        case Left(messages)  => fail(messages.format)
        case Right((typ, _)) => typ.id.value mustBe "X"
      }
    }

    "parseTopLevelDomain[Context]" in {
      parseTopLevelDomain[Context](
        "domain foo is { context X is { ??? } }",
        _.domains.head.contexts.head
      ) match {
        case Left(messages)  => fail(messages.format)
        case Right((typ, _)) => typ.id.value mustBe "X"
      }
    }

    "parseTopLevelDomain[Entity]" in {
      parseTopLevelDomain[Entity](
        "domain foo is { context C is { entity X is { ??? } } }",
        _.domains.head.contexts.head.entities.head
      ) match {
        case Left(messages)  => fail(messages.format)
        case Right((typ, _)) => typ.id.value mustBe "X"
      }
    }

    "parseTopLevelDomain[Adaptor]" in {
      parseTopLevelDomain[Adaptor](
        "domain foo is { context C is { adaptor X to context C is { ??? } } }",
        _.domains.head.contexts.head.adaptors.head
      ) match {
        case Left(messages)  => fail(messages.format)
        case Right((typ, _)) => typ.id.value mustBe "X"
      }
    }

    "parseTopLevelDomain[Function]" in {
      parseTopLevelDomain[Function](
        "domain foo is { context C is { function X is { ??? } } }",
        _.domains.head.contexts.head.functions.head
      ) match {
        case Left(messages)  => fail(messages.format)
        case Right((typ, _)) => typ.id.value mustBe "X"
      }
    }

    "parseTopLevelDomain[Saga]" in {
      parseTopLevelDomain[Saga](
        "domain foo is { context C is { saga X is { ??? } } }",
        _.domains.head.contexts.head.sagas.head
      ) match {
        case Left(messages)  => fail(messages.format)
        case Right((typ, _)) => typ.id.value mustBe "X"
      }
    }

    "parseTopLevelDomain[Processor]" in {
      parseTopLevelDomain[Streamlet](
        "domain foo is { context C is { source X is { ??? } } }",
        _.domains.head.contexts.head.streamlets.head
      ) match {
        case Left(messages)  => fail(messages.format)
        case Right((src, _)) => src.id.value mustBe "X"
      }
    }

    "parseTopLevelDomain[Projector]" in {
      parseTopLevelDomain[Projector](
        "domain foo is { context C is { projector X is { ??? } } }",
        _.domains.head.contexts.head.
          projectors.head
      ) match {
        case Left(messages)  => fail(messages.format)
        case Right((src, _)) => src.id.value mustBe "X"
      }
    }

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
      parseDefinition[Function]("function foo { ??? }") match {
        case Left(messages)  => fail(messages.format)
        case Right((fun, _)) => fun.id.value must be("foo")

      }
    }
    "parseInContext[Term]" in {
      parseInContext[Term]("term X is briefly \"X\"", _.terms.head) match {
        case Left(messages)  => fail(messages.format)
        case Right((typ, _)) => typ.id.value must be("X")
      }
    }
  }
}
