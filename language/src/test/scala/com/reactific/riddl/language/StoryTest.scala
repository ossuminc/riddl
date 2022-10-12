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

  "Story" should { // TODO: validate designs
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
          messages mustBe empty
          val story = domain.stories.head
          story.id.format mustBe "WritingABook"
          story.userStory mustNot be(empty)
          story.userStory.actor.id.value mustBe Seq("foo", "Author")
          story.userStory.capability mustBe
            LiteralString((4, 26, rpi), "edit on the screen")
          story.userStory.benefit mustBe
            LiteralString((5, 3, rpi), "revise content more easily")
          story.shownBy mustNot be(empty)
          story.shownBy.head.toString mustBe
            "http://example.com:80/path/to/WritingABook"
          story.cases mustNot be(empty)
        // FIXME: story.cases... mustBe "..."
      }
    }

    "parse and validate a full example" in {
      val rpi = RiddlParserInput(
        """domain ImprovingApp is {
          |context OrganizationContext {
          |  entity Organization is { ??? } described as "nada"
          |  projection OrganizationViews is { fields { a: Integer} term xyz brief "y" } described as "nada"
          |} described as "nada"
          |actor Owner is "a person"
          |story EstablishOrganization is {
          |  actor ^^Owner wants "to establish an organization" so that
          |  "I can conduct business as that organization"
          |  author reid is {
          |    name: "Reid Spencer"
          |    email: "reid.spencer@ossum.biz"
          |  } briefly "nada" described as "nada"
          |  term 'conduct business' briefly "Any legal business activity supported by the terms of use."
          |  case primary is {
          |    title: "Creating An Organization"
          |    scope domain ImprovingApp briefly
          |      "This domain is the smallest encapsulating domain that contains everything referenced by interactions"
          |    interaction is {
          |      step from actor ^^Owner "creates an Organization" to context OrganizationContext
          |        briefly "initial invocation",
          |      step from context OrganizationContext "send creation message" to entity Organization
          |        briefly "send creation message",
          |      step from entity Organization "add new organization" to projection OrganizationViews
          |        briefly "add new organization",
          |      step from entity Organization "organizationAdded" to actor ^^Owner
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
  }
}
