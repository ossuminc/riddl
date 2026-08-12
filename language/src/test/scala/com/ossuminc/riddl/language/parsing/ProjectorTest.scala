/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language.parsing

import com.ossuminc.riddl.language.{Contents, *}
import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.At
import com.ossuminc.riddl.utils.PlatformContext
import org.scalatest.TestData

abstract class ProjectorTest(using PlatformContext) extends AbstractParsingTest {

  "Projector" should {
    "use a Repository" in { (td: TestData) =>
      val rpi = RiddlParserInput(
        """domain ignore {
        |  context ignore {
        |    repository storage is {
        |       ???
        |    }
        |    projector transform is {
        |       updates repository storage
        |    }
        |  }
        |}
        |""".stripMargin,
        td
      )
      parseTopLevelDomain[Projector](rpi, _.domains.head.contexts.head.projectors.head) match
        case Left(messages) =>
          if messages.justErrors.nonEmpty then fail(messages.format)
          succeed
        case Right(proj: Projector, input) =>
          val rrs = proj.contents.filter[RepositoryRef]
          rrs mustNot be(empty)
          val rr: RepositoryRef = rrs.head
          rr.pathId mustBe PathIdentifier(At(7, 27, input), Seq("storage"))

    }
    "does not use data statements" in { (td: TestData) =>
      val rpi = RiddlParserInput(
        """domain ignore {
          |  context ignore {
          |   projector transform is {
          |     command PutIt { field: Integer }
          |     record Foo { data: String }
          |     handler X is {
          |       on command PutIt {
          |         put "thing" to record Foo
          |       }
          |     }
          |   }
          |""".stripMargin,
        td
      )
      parseTopLevelDomain[Projector](rpi, _.domains.head.contexts.head.projectors.head) match {
        case Left(messages) =>
          val errors = messages.justErrors
          if errors.isEmpty then fail("Should have generated an error")
          succeed
        case Right(_, _) =>
          fail("Should have failed")
      }
    }
    "can have a relationship with a repository" in { (td: TestData) =>
      val rpi = RiddlParserInput(
        """domain ignore {
          |  context ignore {
          |    repository storage is {
          |       ???
          |    }
          |    projector transform is {
          |       updates repository storage
          |       relationship updates to repository storage as 1:1 with {
          |         brief "Just to show that this projector updates the repository"
          |       }
          |    }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseTopLevelDomain[Projector](rpi, _.domains.head.contexts.head.projectors.head) match
        case Left(messages) =>
          val errors = messages.justErrors
          if errors.nonEmpty then fail(errors.format) else succeed
        case Right(proj: Projector, input) =>
          val rels = proj.contents.filter[Relationship]
          rels mustNot be(empty)
          val rel: Relationship = rels.head
          rel.format must be("relationship updates to repository storage")
    }
  }

  /** A70. The semantics live in `RIDDL-Computational-Model.md` §6.2 and §6.5–§6.8; these cases pin
    * only what the PARSER must accept and refuse.
    */
  "Correlation" should {
    def projectorWith(correlation: String, td: TestData): RiddlParserInput =
      RiddlParserInput(
        s"""domain ignore is {
           |  context ignore is {
           |    record Fulfillment is { customerId: String, orderId: String, paidAmount: Number }
           |    event PaymentTaken is { amount: Number }
           |    projector FulfillmentView is {
           |$correlation
           |    }
           |  }
           |}
           |""".stripMargin,
        td
      )

    def parseCorrelation(
      text: String,
      td: TestData
    ): Either[Messages.Messages, (Projector, RiddlParserInput)] =
      parseTopLevelDomain[Projector](
        projectorWith(text, td),
        _.domains.head.contexts.head.projectors.head
      )

    "parse the full shape with a compound key" in { (td: TestData) =>
      parseCorrelation(
        """      correlation Fulfillment by customerId, orderId yields command Fulfillment is {
          |        handler Collect is {
          |          on e: event PaymentTaken is { set field paidAmount to e.amount }
          |        }
          |      } times out after "30 days" {
          |        do "escalate to operations"
          |      }""".stripMargin,
        td
      ) match
        case Left(messages) => fail(messages.format)
        case Right(proj: Projector, _) =>
          val correlations = proj.correlations
          correlations mustNot be(empty)
          val c = correlations.head
          c.id.value must be("Fulfillment")
          // Keys keep WRITTEN order -- §6.5 makes identity the full tuple and forbids
          // canonicalizing, because component order can matter to a generator's composite index.
          c.keys.map(_.value) must be(Seq("customerId", "orderId"))
          c.yields.pathId.value must be(Seq("Fulfillment"))
          c.timeout.s must be("30 days")
          c.timeoutStatements.toSeq mustNot be(empty)
          c.handlers.size must be(1)
    }

    "reject `yields record`, which the grammar no longer admits" in { (td: TestData) =>
      // Reid, 2026-08-12: a projector's only output is a change to a repository, and a repository
      // is changed by handling a COMMAND. Making `yields` take a command_ref puts that rule in the
      // grammar, so the wrong keyword dies here rather than being diagnosed later -- and a record
      // could never have worked anyway, since `messageRef` is the four real messages only (A9b),
      // leaving no `on` clause able to name what the correlation produced.
      parseCorrelation(
        """      correlation Fulfillment by customerId yields record Fulfillment is {
          |        handler Collect is {
          |          on e: event PaymentTaken is { set field paidAmount to e.amount }
          |        }
          |      } times out after "30 days" { do "escalate" }""".stripMargin,
        td
      ) match
        case Left(messages) =>
          if messages.justErrors.isEmpty then fail("expected a parse error") else succeed
        case Right(_, _) => fail("'yields record' must not parse")
    }

    "reject a correlation with no timeout clause" in { (td: TestData) =>
      // The timeout is mandatory ON PURPOSE (Reid, 2026-08-11): it is what makes an unbounded
      // correlation unrepresentable rather than something validation has to diagnose.
      parseCorrelation(
        """      correlation Fulfillment by customerId yields command Fulfillment is {
          |        handler Collect is {
          |          on e: event PaymentTaken is { set field paidAmount to e.amount }
          |        }
          |      }""".stripMargin,
        td
      ) match
        case Left(messages) =>
          if messages.justErrors.isEmpty then fail("expected a parse error") else succeed
        case Right(_, _) => fail("a correlation without a timeout clause must not parse")
    }

    "reject an empty timeout body" in { (td: TestData) =>
      // `do "nothing"` is the idiom when discarding really is correct; an empty block is a parse
      // error here exactly as it is everywhere else in RIDDL.
      parseCorrelation(
        """      correlation Fulfillment by customerId yields command Fulfillment is {
          |        handler Collect is {
          |          on e: event PaymentTaken is { set field paidAmount to e.amount }
          |        }
          |      } times out after "30 days" { }""".stripMargin,
        td
      ) match
        case Left(messages) =>
          if messages.justErrors.isEmpty then fail("expected a parse error") else succeed
        case Right(_, _) => fail("an empty timeout body must not parse")
    }

    "keep `out`, `after` and `times` usable as identifiers" in { (td: TestData) =>
      // They are keywords for tokenization but deliberately NOT in `definitionKeywords`: they are
      // particles of the `times out after` phrase and ordinary English besides.
      val rpi = RiddlParserInput(
        """domain ignore is {
          |  context ignore is {
          |    record Timings is { out: Number, after: String, times: Number }
          |  }
          |}
          |""".stripMargin,
        td
      )
      parseTopLevelDomain[Context](rpi, _.domains.head.contexts.head) match
        case Left(messages) => fail(messages.format)
        case Right(_, _)    => succeed
    }
  }

}
