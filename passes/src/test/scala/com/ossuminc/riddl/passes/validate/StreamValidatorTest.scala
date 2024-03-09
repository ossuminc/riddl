package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.language.{CommonOptions, Messages}

/** Test cases for the StreamValidator */
class StreamValidatorTest extends ValidatingTest {

  "StreamValidator" must {
    "error on connector type mismatch" in {
      val input = """domain uno {
                    | type Typ1 = Integer
                    | type Typ2 = Real
                    | context a {
                    |  inlet in is type uno.Typ1
                    |  outlet out is type uno.Typ2
                    |  connector c1 is { from outlet a.out to inlet a.in }
                    | }
                    |} """.stripMargin
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
    "error on unattached inlets" in {
      val input = """domain solo {
                    | type T = Integer
                    | context a {
                    |  inlet oops is type T
                    |  inlet in is type T
                    |  outlet out is type T
                    |  connector c1 {
                    |    from outlet a.out to inlet a.in
                    |  }
                    | }
                    |} """.stripMargin
      parseAndValidateDomain(input, shouldFailOnErrors = false) { case (domain, _, messages) =>
        domain.isEmpty mustBe false
        messages.isEmpty mustBe false
        messages.hasErrors mustBe false
        messages.exists(
          _.message.startsWith("Inlet 'oops' is not connected")
        ) mustBe true
      }
    }
    "error on unattached outlets" in {
      val input = """domain solo {
                    | type T = Integer
                    | context a {
                    |  outlet out is type T
                    | }
                    |} """.stripMargin
      parseAndValidateDomain(input, shouldFailOnErrors = false) { case (domain, _, messages) =>
        domain.isEmpty mustBe false
        messages.isEmpty mustBe false
        messages.hasErrors mustBe false
        messages.exists(
          _.message == "Outlet 'out' is not connected"
        ) mustBe true
      }
    }

    "warn about needed persistence option" in {
      val input = """domain uno {
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
                |} """.stripMargin
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
    "warn about useless persistence option" in {
      val input = """domain uno {
                | type T = Integer
                | context a {
                |  outlet out is type T
                |  inlet in is type T
                |  connector c1 {
                |    from outlet a.out to inlet a.in
                |    option persistent
                |  }
                | }
                |} """.stripMargin
      parseAndValidateDomain(input) {
        case (domain, _, messages) =>
          domain.isEmpty mustBe false
          domain.contents.size mustBe 2
          messages.isEmpty mustBe false
          messages.hasErrors mustBe false
          messages.filter(_.message contains "is not needed since both ends") mustNot be(empty)
          messages.exists(
            _.message.startsWith("The persistence option on Connector 'c1'")
          ) mustBe true
      }
    }
  }
}
