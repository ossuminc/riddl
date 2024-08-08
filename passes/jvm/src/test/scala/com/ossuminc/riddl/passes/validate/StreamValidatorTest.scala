package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.{At, CommonOptions, Messages}
import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.passes.Riddl
import org.scalatest.TestData

/** Test cases for the StreamValidator */
class StreamValidatorTest extends ValidatingTest {

  "StreamValidator" must {
    "error on connector type mismatch" in { (td:TestData) =>
      val input = RiddlParserInput("""domain uno {
                    | type Typ1 = Integer
                    | type Typ2 = Real
                    | context a {
                    |  inlet in is type uno.Typ1
                    |  outlet out is type uno.Typ2
                    |  connector c1 is { from outlet a.out to inlet a.in }
                    | }
                    |} """.stripMargin,td)
      parseAndValidateDomain(input, CommonOptions.noMinorWarnings, shouldFailOnErrors = false) {
        case (domain, _, messages) =>
          // info(messages.format)
          domain.isEmpty mustBe false
          messages.isEmpty mustBe false
          messages.hasErrors mustBe true
          messages.exists { (msg: Messages.Message) =>
            msg.message.startsWith("Type mismatch in Connector 'c1':")
          } must be(true)
      }
    }
    "error on unattached inlets" in { (td:TestData) =>
      val input = RiddlParserInput("""domain solo {
                    | type T = Integer
                    | context a {
                    |  inlet oops is type T
                    |  inlet in is type T
                    |  outlet out is type T
                    |  connector c1 {
                    |    from outlet a.out to inlet a.in
                    |  }
                    | }
                    |} """.stripMargin,td)
      parseAndValidateDomain(input, shouldFailOnErrors = false) { case (domain, _, messages) =>
        domain.isEmpty mustBe false
        messages.isEmpty mustBe false
        messages.hasErrors mustBe false
        messages.exists(
          _.message.startsWith("Inlet 'oops' is not connected")
        ) mustBe true
      }
    }
    "error on unattached outlets" in { (td:TestData) =>
      val input = RiddlParserInput(
        """domain solo {
          | type T = Integer
          | context a {
          |  outlet out is type T
          | }
          |} """.stripMargin,td)
      parseAndValidateDomain(input, shouldFailOnErrors = false) { case (domain, _, messages) =>
        domain.isEmpty mustBe false
        messages.isEmpty mustBe false
        messages.hasErrors mustBe false
        messages.exists(
          _.message == "Outlet 'out' is not connected"
        ) mustBe true
      }
    }

    "warn about needed persistence option" in { (td:TestData) =>
      val input = RiddlParserInput(
        """domain uno {
          | type T = Integer
          | context a {
          |  outlet out is type T
          |  connector c1 {
          |    from outlet uno.a.out to inlet uno.b.in
          |  }
          | }
          | context b {
          |   inlet in is type T
          | }
          |} """.stripMargin,td)
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
    "warn about useless persistence option" in { (td:TestData) =>
      val input = RiddlParserInput(
        """domain uno {
          | type T = Integer
          | context a {
          |  outlet out is type T
          |  inlet in is type T
          |  connector c1 {
          |    from outlet a.out to inlet a.in
          |    option persistent
          |  }
          | }
          |} """.stripMargin,td)
      parseAndValidateDomain(input) { case (domain, _, messages) =>
        domain.isEmpty mustBe false
        domain.contents.size mustBe 2
        messages.isEmpty mustBe false
        messages.hasErrors mustBe false
        messages.filter(_.message.contains("is not needed since both ends")) mustNot be(empty)
        messages.exists(
          _.message.startsWith("The persistence option on Connector 'c1'")
        ) mustBe true
      }
    }

    def pid(name: String): PathIdentifier = PathIdentifier(At(), Seq("domain", "context", name))

    def inlet(name: String, pidName: String): Inlet =
      Inlet(At(), Identifier(At(), name), TypeRef(At(), "type", pid(pidName)))

    def outlet(name: String, pidName: String): Outlet =
      Outlet(At(), Identifier(At(), name), TypeRef(At(), "type", pid(pidName)))

    def root(streamlets: Seq[Streamlet]): Root = {
      Root(
        Seq(
          Domain(
            At(),
            Identifier(At(), "domain"),
            Seq(
              Context(
                At(),
                Identifier(At(), "context"),
                Seq(
                  Type(At(), Identifier(At(), "Int"), Integer(At()))
                ) ++ streamlets
              )
            )
          )
        )
      )
    }

    "validate Streamlet types" in { (td:TestData) =>
      intercept[IllegalArgumentException] {
        Riddl.validate(
          root(
            Seq(
              Streamlet(At(), Identifier(At(), "source"), Source(At()), Seq(inlet("in1", "int"), inlet("in2", "int")))
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
                Seq(
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
                Seq(
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
                Seq(
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
                Seq(
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
                Seq(
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
                Seq(
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
