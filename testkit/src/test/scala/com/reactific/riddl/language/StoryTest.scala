package com.reactific.riddl.language

import com.reactific.riddl.language.AST.Domain
import com.reactific.riddl.language.AST.LiteralString
import com.reactific.riddl.language.parsing.RiddlParserInput
import com.reactific.riddl.testkit.ValidatingTest

class StoryTest extends ValidatingTest {

  "Story" should { // TODO: validate designs
    "parse and validate a case-less example " in {
      val rpi = RiddlParserInput(
        """domain foo is {
          |story WritingABook is {
          |  actor Author is "human"
          |  capability is "edit on the screen"
          |  benefit is "revise content more easily"
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
          messages mustBe empty
          val story = domain.stories.head
          story.id.format mustBe "WritingABook"
          story.userStory mustNot be(empty)
          story.userStory.get.actor.id.value mustBe "Author"
          story.userStory.get.capability mustBe
            LiteralString((4, 17, rpi), "edit on the screen")
          story.userStory.get.benefit mustBe
            LiteralString((5, 14, rpi), "revise content more easily")
          story.shownBy mustNot be(empty)
          story.shownBy.head.toString mustBe
            "http://example.com:80/path/to/WritingABook"
          story.cases mustNot be(empty)
        // FIXME: story.cases... mustBe "..."
      }
    }

    "parse and validate a full example" in {
      val rpi = RiddlParserInput(
        """domain foo is {
          |story EstablishOrganization is {
          |  actor Owner is "a person"
          |  capability is "establish an organization"
          |  benefit is "I can conduct business as that organization"
          |  author reid is {
          |    name: "Reid Spencer"
          |    email: "reid.spencer@ossum.biz"
          |  } briefly "nada" described as "nada"
          |  term Organization briefly "An organizational unit, incorporated or not. "
          |  case primary is {
          |    title: "Creating An Organization"
          |    scope domain ImprovingApp briefly
          |      "This domain is the smallest encapsulating domain that contains everything referenced by uses"
          |    uses {
          |     context OrganizationContext briefly "This bounded context contains components used in the story",
          |     entity Organization briefly "This is what gets created",
          |     projection OrganizationViews briefly "This is how you find them"
          |    }
          |    interaction is {
          |      step 1 from actor Owner to context OrganizationContext
          |        briefly "initial invocation",
          |      step 2 from context OrganizationContext to entity Organization
          |        briefly "send creation message",
          |      step 3 from entity Organization to projection OrganizationViews
          |        briefly "add new organization",
          |      step 4 from entity Organization to actor Owner
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
          msgs mustBe empty
          succeed
      }
    }
  }
}
