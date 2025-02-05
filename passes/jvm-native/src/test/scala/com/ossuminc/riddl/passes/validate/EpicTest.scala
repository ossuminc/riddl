/*
 * Copyright 2019-2025 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.passes.validate

import com.ossuminc.riddl.utils.URL
import com.ossuminc.riddl.language.AST.*
import com.ossuminc.riddl.language.Messages
import com.ossuminc.riddl.language.parsing.RiddlParserInput
import com.ossuminc.riddl.utils.{pc, ec}
import org.scalatest.TestData

class EpicTest extends AbstractValidatingTest {

  "Epic" should {
    "parse and validate a case-less example " in { (td: TestData) =>
      val rpi = RiddlParserInput(
        """domain foo is {
          |  user Author is "human writer"
          |  epic WritingABook is {
          |    user foo.Author wants to "edit on the screen" so that "he can revise content more easily"
          |    shown by { http://example.com:80/path/to/WritingABook }
          |    case perfection is { user foo.Author wants "to open a document" so that "it can be edited" ???  }
          |  } with { described as "A simple authoring story" }
          |} with { described as "a parsing convenience" }
          |""".stripMargin,td
      )
      parseAndValidateDomain(rpi) { case (domain: Domain, rpi: RiddlParserInput, messages: Messages.Messages) =>
        domain.epics mustNot be(empty)
        messages.isOnlyIgnorable mustBe true
        val epic: Epic = domain.epics.head
        epic.id.format mustBe "WritingABook"
        epic.userStory mustNot be(empty)
        val us = epic.userStory
        us mustNot be(empty)
        us.user.pathId.value mustBe Seq("foo", "Author")
        us.capability mustBe LiteralString((4, 30, rpi), "edit on the screen")
        us.benefit mustBe LiteralString((4, 59, rpi), "he can revise content more easily")
        epic.shownBy mustNot be(empty)
        epic.shownBy.head must be(ShownBy((5,5,rpi),List(URL("http://example.com:80/path/to/WritingABook"))))
        epic.cases mustNot be(empty)
        val uc = epic.cases.head
        uc.id.value mustBe "perfection"
      }
    }

    "parse and validate a full example" in { (td: TestData) =>
      val rpi = RiddlParserInput(
        """domain ImprovingApp is {
          |context OrganizationContext {
          |  command CreateOrganization is {
          |    id: Id(OrganizationContext.Organization),
          |    name: String
          |  }
          |  result OrganizationInfo is {
          |    name: String,
          |    createdAt: TimeStamp
          |  }
          |
          |  entity Organization is { ??? } with { description as "nada" }
          |
          |  projector OrganizationViews is {
          |   record SimpleView { a: Integer}
          |   handler Simple { ??? }
          |
          |  } with {
          |    explanation as "nada"
          |    term xyz is "y"
          |  }
          |} with { explained as "nada" }
          |
          |context Improving_app is {
          |  group OrganizationPage is {
          |    input accept acquires command ImprovingApp.OrganizationContext.CreateOrganization
          |    output show presents result ImprovingApp.OrganizationContext.OrganizationInfo
          |  }
          |}
          |
          |author reid is {
          |  name: "Reid Spencer"
          |  email: "reid.spencer@ossum.biz"
          |} with {
          |  briefly "nada" described as "nada"
          |}
          |
          |user Owner is "a person"
          |
          |epic EstablishOrganization is {
          |  user ImprovingApp.Owner wants "to establish an organization" so that
          |  "they can conduct business as that organization"
          |
          |  case primary is {
          |    user ImprovingApp.Owner wants "to incorporate an organization" so that "it can be used"
          |    optional {
          |      step take input ImprovingApp.Improving_app.OrganizationPage.accept
          |        from user ImprovingApp.Owner with { briefly "create org" }
          |      step show output ImprovingApp.Improving_app.OrganizationPage.show
          |        to user ImprovingApp.Owner with { briefly "organization added" }
          |    }
          |  } with {
          |    briefly "A use case about establishing an organization in Improving.app"
          |    described as "TBD"
          |  }
          |} with {
          |  briefly "A placeholder" described by "Not important"
          |  by author ImprovingApp.reid
          |  term 'conduct business' is "Any legal business activity supported by the terms of use."
          |}
          |}
          |""".stripMargin,td
      )
      parseAndValidateDomain(rpi, shouldFailOnErrors = false) {
        case (domain: Domain, _: RiddlParserInput, msgs: Messages.Messages) =>
          val errors = msgs.justErrors
          // info(errors.format)
          if errors.isEmpty then
            domain mustNot be(empty)
            domain.epics mustNot be(empty)
          else fail(errors.format)
      }
    }
    "handle parallel group" in { (td: TestData) =>
      val rpi = RiddlParserInput(
        """domain ImprovingApp is {
          |context OrganizationContext {
          |  command CreateOrganization is {
          |    id: Id(OrganizationContext.Organization),
          |    name: String
          |  }
          |  result OrganizationInfo is {
          |    name: String,
          |    createdAt: TimeStamp
          |  }
          |
          |  entity Organization is { ??? } with { described as "nada" }
          |
          |  projector OrganizationViews is {
          |   record SimpleView { a: Integer}
          |   handler Simple { ??? }
          |  } with {
          |   term xyz "y"
          |   described as "nada"
          |  }
          |} with { described as "nada" }
          |
          |context Improving_app is {
          |  group OrganizationPage is {
          |    input accept acquires command ImprovingApp.OrganizationContext.CreateOrganization
          |    output show presents result ImprovingApp.OrganizationContext.OrganizationInfo
          |  }
          |}
          |
          |author reid is {
          |  name: "Reid Spencer"
          |  email: "reid.spencer@ossum.biz"
          |} with { briefly "nada" described as "nada" }
          |
          |user Owner is "a person"
          |
          |epic EstablishOrganization is {
          |  user ImprovingApp.Owner wants "to establish an organization" so that
          |  "they can conduct business as that organization"
          |  case primary is {
          |    user ImprovingApp.Owner wants "to establish an organization" so that
          |      "they can conduct business as that organization"
          |    parallel {
          |      step take input ImprovingApp.Improving_app.OrganizationPage.accept
          |        from  user ImprovingApp.Owner with {   briefly "create org" }
          |      step show output ImprovingApp.Improving_app.OrganizationPage.show
          |        to user ImprovingApp.Owner with { briefly "organization added" }
          |     }
          |  }
          |} with {
          |  term 'conduct business' is "Any legal business activity supported by the terms of use."
          |  briefly "A story about establishing an organization in Improving.app"
          |  described as "TBD"
          | by author reid
          |}
          |} with { briefly "A placeholder" described by "Not important" }
          |""".stripMargin,td
      )
      parseAndValidateDomain(rpi, shouldFailOnErrors = true) {
        case (domain: Domain, _: RiddlParserInput, msgs: Messages.Messages) =>
          val errors = msgs.justErrors
          if errors.isEmpty then
            domain mustNot be(empty)
            domain.epics mustNot be(empty)
            msgs.hasErrors mustBe false
            succeed
          else
            // info(msgs.format)
            fail("Shouldn't have errors")
      }
    }
    "handle optional group" in { (td: TestData) =>
      val rpi = RiddlParserInput(
        """domain ImprovingApp is {
          |context OrganizationContext {
          |  command CreateOrganization is {
          |    id: Id(OrganizationContext.Organization),
          |    name: String
          |  }
          |  result OrganizationInfo is {
          |    name: String,
          |    createdAt: TimeStamp
          |  }
          |
          |  entity Organization is { ??? } with { described as "nada" }
          |
          |  projector OrganizationViews is {
          |   record SimpleView { a: Integer}
          |   handler Simple { ??? }
          |  } with {
          |   term xyz "y"
          |   described as "nada"
          |  }
          |} with {
          |  described as "nada"
          |}
          |
          |context Improving_app is {
          |  group OrganizationPage is {
          |    input accept acquires command ImprovingApp.OrganizationContext.CreateOrganization
          |    output show presents result ImprovingApp.OrganizationContext.OrganizationInfo
          |  }
          |}
          |
          |user Owner is "a person"
          |
          |epic EstablishOrganization is {
          |  user ImprovingApp.Owner wants "to establish an organization" so that
          |  "they can conduct business as that organization"
          |
          |  case primary is {
          |    user Owner wants "to do it" so that "it is done"
          |    step take input ImprovingApp.Improving_app.OrganizationPage.accept
          |      from user ImprovingApp.Owner with { briefly "create org" }
          |    step for user ImprovingApp.Owner "contemplates his navel"
          |      with { briefly "self-processing" }
          |    step show output ImprovingApp.Improving_app.OrganizationPage.show
          |      to user ImprovingApp.Owner with { briefly "organization added" }
          |  }
          |} with {
          |  briefly "A story about establishing an organization in Improving.app"
          |  described as "TBD"
          |  term 'conduct business' "Any legal business activity supported by the terms of use."
          |}
          |} with { briefly "A placeholder" described by "Not important" }
          |""".stripMargin,td
      )
      parseAndValidateDomain(rpi, shouldFailOnErrors = true) {
        case (domain: Domain, _: RiddlParserInput, msgs: Messages.Messages) =>
          val errors = msgs.justErrors
          if errors.nonEmpty then
            info(errors.format)
            fail("Shouldn't have errors")
          else
            domain mustNot be(empty)
            domain.epics mustNot be(empty)
            if msgs.nonEmpty then {}
            msgs.hasErrors mustBe false
            succeed
      }
    }
  }
}
