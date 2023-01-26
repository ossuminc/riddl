/*
 * Copyright 2019 Ossum, Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.reactific.riddl.language

import com.reactific.riddl.language.AST.Domain
import com.reactific.riddl.language.AST.LiteralString
import com.reactific.riddl.language.parsing.RiddlParserInput

class StoryTest extends ValidatingTest {

  "Story" should {
    "parse and validate a case-less example " in {
      val rpi = RiddlParserInput(
        """domain foo is {
          |  actor Author is "human writer"
          |story WritingABook is {
          |  actor foo.Author wants "edit on the screen" so that
          |  "revise content more easily"
          |  shown by { http://example.com:80/path/to/WritingABook }
          |  case perfection is { ???  }
          |  /////////////
          |    example one {
          |      given "I need to write a book"
          |      when "I am writing the book"
          |      then "I can easily type words on the screen instead of using a pen"
          |    } described by "nothing"
          |    example two {
          |      given "I need to edit a previously written book"
          |      when "I am revising the book"
          |      then "I can erase and re-type words on the screen"
          |    } described as "nothing"
          |
          |} described as "A simple authoring story"
          |} described as "a parsing convenience"
          |""".stripMargin
      )
      parseAndValidateDomain(rpi) {
        case (
              domain: Domain,
              rpi: RiddlParserInput,
              messages: Messages.Messages
            ) =>
          domain.stories mustNot be(empty)
          messages.isIgnorable mustBe true
          val story = domain.stories.head
          story.id.format mustBe "WritingABook"
          story.userStory mustNot be(empty)
          val us = story.userStory.get
          us mustNot be(empty)
          us.actor.pathId.value mustBe Seq("foo", "Author")
          us.capability mustBe LiteralString((4, 26, rpi), "edit on the screen")
          us.benefit mustBe
            LiteralString((5, 3, rpi), "revise content more easily")
          story.shownBy mustNot be(empty)
          story.shownBy.head.toString mustBe
            "http://example.com:80/path/to/WritingABook"
          story.cases mustNot be(empty)
          val sc = story.cases.head
          sc.id.value mustBe "perfection"
          story.examples mustNot be(empty)
      }
    }

    "parse and validate a full example" in {
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
          |  entity Organization is { ??? } described as "nada"
          |
          |  projection OrganizationViews is {
          |   record SimpleView { a: Integer}
          |   handler Simple { ??? }
          |   term xyz
          |   brief "y"
          |  } described as "nada"
          |} described as "nada"
          |
          |application Improving_app is {
          |  group OrganizationPage is {
          |    input accept is {
          |      acquires command
          |        ImprovingApp.OrganizationContext.CreateOrganization
          |    }
          |    output show is {
          |      presents result
          |        ImprovingApp.OrganizationContext.OrganizationInfo
          |    }
          |  }
          |}
          |
          |author reid is {
          |  name: "Reid Spencer"
          |  email: "reid.spencer@ossum.biz"
          |} briefly "nada" described as "nada"
          |
          |actor Owner is "a person"
          |
          |story EstablishOrganization by author reid is {
          |  actor ^^Owner wants "to establish an organization" so that
          |  "they can conduct business as that organization"
          |  term 'conduct business' briefly
          |  "Any legal business activity supported by the terms of use."
          |
          |  case primary is {
          |    optional {
          |      step from actor ^^Owner "creates an Organization" to
          |        input ImprovingApp.Improving_app.OrganizationPage.accept
          |        briefly "create org",
          |      step from output ImprovingApp.Improving_app.OrganizationPage.show
          |        "presented" to actor ^^Owner
          |        briefly "organization added"
          |    }
          |  }
          |} briefly "A story about establishing an organization in Improving.app"
          |  described as "TBD"
          |} briefly "A placeholder" described by "Not important"
          |""".stripMargin
      )
      parseAndValidateDomain(rpi) {
        case (domain: Domain, _: RiddlParserInput, msgs: Messages.Messages) =>
          domain mustNot be(empty)
          domain.stories mustNot be(empty)
          if (msgs.nonEmpty) { info(msgs.format) }
          msgs.isIgnorable mustBe true
          succeed
      }
    }
    "handle parallel group" in {
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
          |  entity Organization is { ??? } described as "nada"
          |
          |  projection OrganizationViews is {
          |   record SimpleView { a: Integer}
          |   handler Simple { ??? }
          |   term xyz
          |   brief "y"
          |  } described as "nada"
          |} described as "nada"
          |
          |application Improving_app is {
          |  group OrganizationPage is {
          |    input accept is {
          |      acquires command
          |        ImprovingApp.OrganizationContext.CreateOrganization
          |    }
          |    output show is {
          |      presents result
          |        ImprovingApp.OrganizationContext.OrganizationInfo
          |    }
          |  }
          |}
          |
          |author reid is {
          |  name: "Reid Spencer"
          |  email: "reid.spencer@ossum.biz"
          |} briefly "nada" described as "nada"
          |
          |actor Owner is "a person"
          |
          |story EstablishOrganization by author reid is {
          |  actor ^^Owner wants "to establish an organization" so that
          |  "they can conduct business as that organization"
          |  term 'conduct business' briefly
          |  "Any legal business activity supported by the terms of use."
          |
          |  case primary is {
          |    parallel {
          |      step from actor ^^Owner "creates an Organization" to
          |        input ImprovingApp.Improving_app.OrganizationPage.accept
          |        briefly "create org",
          |      step from output ImprovingApp.Improving_app.OrganizationPage.show
          |        "presented" to actor ^^Owner
          |        briefly "organization added"
          |     }
          |  }
          |} briefly "A story about establishing an organization in Improving.app"
          |  described as "TBD"
          |} briefly "A placeholder" described by "Not important"
          |""".stripMargin
      )
      parseAndValidateDomain(rpi) {
        case (domain: Domain, _: RiddlParserInput, msgs: Messages.Messages) =>
          domain mustNot be(empty)
          domain.stories mustNot be(empty)
          if (msgs.nonEmpty) { info(msgs.format) }
          msgs.isIgnorable mustBe true
          succeed
      }
    }
    "handle optional group" in {
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
          |  entity Organization is { ??? } described as "nada"
          |
          |  projection OrganizationViews is {
          |   record SimpleView { a: Integer}
          |   handler Simple { ??? }
          |   term xyz
          |   brief "y"
          |  } described as "nada"
          |} described as "nada"
          |
          |application Improving_app is {
          |  group OrganizationPage is {
          |    input accept is {
          |      acquires command
          |        ImprovingApp.OrganizationContext.CreateOrganization
          |    }
          |    output show is {
          |      presents result
          |        ImprovingApp.OrganizationContext.OrganizationInfo
          |    }
          |  }
          |}
          |
          |actor Owner is "a person"
          |
          |story EstablishOrganization is {
          |  actor ^^Owner wants "to establish an organization" so that
          |  "they can conduct business as that organization"
          |  term 'conduct business' briefly
          |  "Any legal business activity supported by the terms of use."
          |
          |  case primary is {
          |    step from actor ^^Owner "creates an Organization" to
          |      input ImprovingApp.Improving_app.OrganizationPage.accept
          |      briefly "create org",
          |    step for actor ^^Owner "contemplates his navel"
          |      briefly "self-processing",
          |    step from output ImprovingApp.Improving_app.OrganizationPage.show
          |      "presented" to actor ^^Owner
          |      briefly "organization added"
          |  }
          |} briefly "A story about establishing an organization in Improving.app"
          |  described as "TBD"
          |} briefly "A placeholder" described by "Not important"
          |""".stripMargin
      )
      parseAndValidateDomain(rpi) {
        case (domain: Domain, _: RiddlParserInput, msgs: Messages.Messages) =>
          domain mustNot be(empty)
          domain.stories mustNot be(empty)
          if (msgs.nonEmpty) { info(msgs.format) }
          msgs.isIgnorable mustBe true
          succeed
      }
    }
  }
}
