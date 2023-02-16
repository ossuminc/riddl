package com.reactific.riddl.language

/** Test cases for the StreamValidator */
class StreamValidatorTest extends ValidatingTest {

  "StreamValidator" must {
    "error on pipe type mismatch" in {
      val input = """domain uno {
                    | type Typ1 = Integer
                    | type Typ2 = Real
                    | context a {
                    |  inlet in is type Typ1
                    |  outlet out is type Typ2
                    |  pipe p1 {
                    |    transmit T from outlet a.out to inlet a.in
                    |  }
                    | }
                    | context b {
                    | }
                    |} """.stripMargin
      parseAndValidateDomain(input) { case (domain, _, messages) =>
        domain.isEmpty mustBe false
        messages.isEmpty mustBe false
        messages.hasErrors mustBe true
        messages.filter(_.message contains "Type mismatch: expected") mustNot
          be(empty)
      }
    }
    "warn about needed persistence option" in {
      val input = """domain uno {
                    | type T = Integer
                    | context a {
                    |  outlet out is type T
                    |  pipe p1 {
                    |    transmit T from outlet uno.a.out to inlet uno.b.in
                    |  }
                    | }
                    | context b {
                    |   inlet in is type T
                    | }
                    |} """.stripMargin
      parseAndValidateDomain(input) { case (domain, _, messages) =>
        domain.isEmpty mustBe false
        messages.isEmpty mustBe false
        messages.hasErrors mustBe false
        messages.filter(_.message contains "pipe is not connected") mustNot
          be(empty)
      }
    }
    "warn about useless persistence option" in {
      val input = """domain uno {
                    | type T = Integer
                    | context a {
                    |  outlet out is type T
                    |  inlet in is type T
                    |  pipe p1 {
                    |    options(persistent)
                    |    transmit T from outlet a.out to inlet a.in
                    |  }
                    | }
                    |} """.stripMargin
      parseAndValidateDomain(input) { case (domain, _, messages) =>
        domain.isEmpty mustBe false
        messages.isEmpty mustBe false
        messages.hasErrors mustBe false
        messages.filter(_.message contains "is not needed") mustNot be(empty)
      }
    }
    "error on unattached inlets" in {
      val input = """domain solo {
                    | type T = Integer
                    | context a {
                    |  inlet in is type T
                    |  inlet wasted is type T
                    |  pipe p1 {
                    |    options(persistent)
                    |    transmit T to inlet a.in
                    |  }
                    | }
                    |} """.stripMargin
      parseAndValidateDomain(input) { case (domain, _, messages) =>
        domain.isEmpty mustBe false
        messages.isEmpty mustBe false
        messages.hasErrors mustBe true
        messages.find(_.message contains "is not attached") mustNot be(empty)
      }
    }
    "warn about unpublished pipes" in {
      val input = """domain solo {
                    | type T = Integer
                    | context a {
                    |  inlet in is type T
                    |  pipe p1 {
                    |    transmit T to inlet a.in
                    |  }
                    | }
                    |} """.stripMargin
      parseAndValidateDomain(input) { case (domain, _, messages) =>
        domain.isEmpty mustBe false
        messages.isEmpty mustBe false
        messages.hasErrors mustBe false
        val expected = "Pipe 'p1' has no publishers"
        messages.find(_.message contains expected) mustNot be(empty)
      }
    }

    "warn about subscribing to attached pipe end" in {
      val input = """domain solo {
                    | type T = Integer
                    | context a {
                    |  outlet out is type T
                    |  inlet in is type T
                    |  pipe p1 {
                    |    transmit T from outlet a.out to inlet a.in
                    |  }
                    |  handler handy is {
                    |    on init {
                    |      then {
                    |        subscribe to pipe a.p1 for T
                    |      }
                    |    }
                    |  }
                    | }
                    |} """.stripMargin
      parseAndValidateDomain(input) { case (domain, _, messages) =>
        domain.isEmpty mustBe false
        messages.isEmpty mustBe false
        messages.hasErrors mustBe true
        val expected = "Subscribing to Pipe 'p1' with attached inlet a.in"
        messages.find(_.message contains expected) mustNot be(empty)
      }
    }

    "warn about unsubscribed pipes" in {
      val input = """domain solo {
                    | type T = Integer
                    | context a {
                    |  outlet out is type T
                    |  pipe p1 {
                    |    transmit T from outlet a.out
                    |  }
                    | }
                    |} """.stripMargin
      parseAndValidateDomain(input) { case (domain, _, messages) =>
        domain.isEmpty mustBe false
        messages.isEmpty mustBe false
        messages.hasErrors mustBe false
        val expected = "Pipe 'p1' has no subscribers"
        messages.find(_.message contains expected) mustNot be(empty)
      }
    }

    "warn about publishing to attached pipe end" in {
      val input = """domain solo {
                    | query Foo is { v: Integer }
                    | context a {
                    |  outlet out is type Foo
                    |  inlet in is type foo
                    |  pipe p1 {
                    |    transmit Foo from outlet a.out to inlet a.in
                    |  }
                    |  handler handy is {
                    |    on term {
                    |      then {
                    |        publish query Foo(v=23) to pipe a.p1
                    |      }
                    |    }
                    |  }
                    | }
                    |} """.stripMargin
      parseAndValidateDomain(input) { case (domain, _, messages) =>
        domain.isEmpty mustBe false
        messages.isEmpty mustBe false
        messages.hasErrors mustBe true
        val expected = "Publishing to Pipe 'p1' with attached outlet a.out"
        messages.find(_.message contains expected) mustNot be(empty)
      }
    }
  }
}
