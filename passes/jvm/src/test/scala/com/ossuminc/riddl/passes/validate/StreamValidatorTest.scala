/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.{At, Messages}
import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.passes.Riddl
import com.ossuminc.riddl.utils.pc
import com.ossuminc.riddl.utils.CommonOptions

import org.scalatest.TestData

/** Test cases for the StreamValidator */
class StreamValidatorTest extends AbstractValidatingTest {

  "StreamValidator" must {
    "error on connector type mismatch" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain uno {
        | type Typ1 = Integer
        | type Typ2 = Real
        | context a {
        |  flow foo is {
        |   inlet in is type uno.Typ1
        |   outlet out is type uno.Typ2
        |  }
        |  connector c1 is { from outlet a.foo.out to inlet a.foo.in }
        | }
        |} """.stripMargin,
        td
      )
      pc.withOptions(CommonOptions.noMinorWarnings) { _ =>
        parseAndValidateDomain(input, shouldFailOnErrors = false) { case (domain, _, messages) =>
          domain.isEmpty must be(false)
          messages.isEmpty must be(false)
          messages.hasErrors must be(true)
          val errors = messages.justErrors
          info(errors.format)
          errors.exists { (msg: Messages.Message) =>
            msg.message.startsWith("Type mismatch in Connector 'c1':")
          } must be(true)
        }
      }
    }
    "error on unattached inlets" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain solo {
          | type T = Integer
          | context a {
          |  merge confluence is {
          |    inlet one is type T
          |    inlet two is type T
          |    outlet out is type T
          |  }
          |  connector c1 {
          |    from outlet a.confluence.out to inlet a.confluence.two
          |  }
          | }
          |} """.stripMargin,
        td
      )
      parseAndValidateDomain(input) { case (domain, _, messages) =>
        domain.isEmpty mustBe false
        messages.isEmpty mustBe false
        messages.hasErrors mustBe false
        messages.exists(
          _.message.startsWith("Inlet 'one' is not connected")
        ) mustBe true
      }
    }
    "error on unattached outlets" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain solo {
          | type T = Integer
          | context a is {
          |  source from is {
          |    outlet out is type T
          |  }
          | }
          |} """.stripMargin,
        td
      )
      parseAndValidateDomain(input, shouldFailOnErrors = true) { case (domain, _, messages) =>
        domain.isEmpty mustBe false
        messages.isEmpty mustBe false
        messages.hasErrors mustBe false
        messages.exists(
          _.message == "Outlet 'out' is not connected"
        ) mustBe true
      }
    }

    "warn about needed persistence option" in { (td: TestData) =>
      val input = RiddlParserInput(
        """domain uno {
          | type T = Integer
          | context a {
          |  source from is { outlet out is type T }
          |  connector c1 {
          |    from outlet a.from.out to inlet uno.b.to.in
          |  }
          | }
          | context b {
          |   sink to  is { inlet in is type T }
          | }
          |} """.stripMargin,
        td
      )
      parseAndValidateDomain(input, shouldFailOnErrors = false) { case (domain, _, messages) =>
        domain.isEmpty mustBe false
        messages.isEmpty mustBe false
        messages.hasErrors mustBe false
        messages.exists(_.message.contains("is not connected")) mustBe
          true
        messages.exists(
          _.message.startsWith("The persistence option on Connector 'c1'")
        ) mustBe true
      }
    }
    "warn about useless persistence option" in { (td: TestData) =>
      pc.withOptions(CommonOptions.default) { _ =>
        val input = RiddlParserInput(
          """domain uno {
            | type T = Integer
            | context a {
            |  flow through is {
            |    inlet in is type T
            |    outlet out is type T
            |  }
            |  connector c1 {
            |    from outlet a.through.out to inlet uno.a.through.in
            |    option persistent
            |  }
            | }
            |} """.stripMargin,
          td
        )
        parseAndValidateDomain(input) { case (domain, _, messages) =>
          domain.isEmpty must be(false)
          domain.contents.size must be(2)
          messages.isEmpty must be(false)
          messages.hasErrors must be(false)
          messages.filter(_.message.contains("is not needed since both end")) match
            case List(message) =>
              message.message must include("The persistence option on Connector 'c1' is not")
              succeed
            case Nil => fail("Missing message")
        }
      }
    }

    def pid(name: String): PathIdentifier = PathIdentifier(At(), Seq("domain", "context", name))

    def inlet(name: String, pidName: String): Inlet =
      Inlet(At(), Identifier(At(), name), TypeRef(At(), "type", pid(pidName)))

    def outlet(name: String, pidName: String): Outlet =
      Outlet(At(), Identifier(At(), name), TypeRef(At(), "type", pid(pidName)))

    def root(streamlets: Seq[Streamlet]): Root = {
      Root(
        At(),
        Contents(
          Domain(
            At(),
            Identifier(At(), "domain"),
            Contents(
              Context(
                At(),
                Identifier(At(), "context"),
                Contents(
                  Type(At(), Identifier(At(), "Int"), Integer(At()))
                ) ++ streamlets
              )
            )
          )
        )
      )
    }

    "validate Streamlet types" in { _ =>
      intercept[IllegalArgumentException] {
        Riddl.validate(
          root(
            Seq(
              Streamlet(
                At(),
                Identifier(At(), "source"),
                Source(At()),
                Contents(inlet("in1", "int"), inlet("in2", "int"))
              )
            )
          )
        )
      }
      intercept[IllegalArgumentException] {
        Riddl.validate(
          root(
            Seq(
              Streamlet(
                At(),
                Identifier(At(), "sink"),
                Sink(At()),
                Contents(
                  inlet("in1", "int"),
                  outlet("out1", "int")
                )
              )
            )
          )
        )
      }
      intercept[IllegalArgumentException] {
        Riddl.validate(
          root(
            Seq(
              Streamlet(
                At(),
                Identifier(At(), "flow"),
                Flow(At()),
                Contents(
                  outlet("out1", "int"),
                  outlet("out2", "int")
                )
              )
            )
          )
        )
      }
      intercept[IllegalArgumentException] {
        Riddl.validate(
          root(
            Seq(
              Streamlet(
                At(),
                Identifier(At(), "flow"),
                Split(At()),
                Contents(
                  outlet("out1", "int"),
                  outlet("out2", "int")
                )
              )
            )
          )
        )
      }
      intercept[IllegalArgumentException] {
        Riddl.validate(
          root(
            Seq(
              Streamlet(
                At(),
                Identifier(At(), "flow"),
                Merge(At()),
                Contents(
                  inlet("in1", "int"),
                  inlet("in2", "int")
                )
              )
            )
          )
        )
      }
      intercept[IllegalArgumentException] {
        Riddl.validate(
          root(
            Seq(
              Streamlet(
                At(),
                Identifier(At(), "flow"),
                Router(At()),
                Contents(
                  outlet("out2", "int")
                )
              )
            )
          )
        )
      }
      intercept[IllegalArgumentException] {
        Riddl.validate(
          root(
            Seq(
              Streamlet(
                At(),
                Identifier(At(), "flow"),
                Void(At()),
                Contents(
                  inlet("in1", "int"),
                  outlet("out2", "int")
                )
              )
            )
          )
        )
      }
    }
  }
}
