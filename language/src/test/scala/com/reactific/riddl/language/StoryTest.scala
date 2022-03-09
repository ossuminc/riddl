package com.reactific.riddl.language

import com.reactific.riddl.language.AST.{Domain, LiteralString}

class StoryTest extends ValidatingTest {

  "Story" should {
    "parse and validate a full example " in {
      val input = """domain foo is {
                    |story WritingABook is {
                    |  role is "Author"
                    |  capability is "edit on the screen"
                    |  benefit is "revise content more easily"
                    |  shown by { http://example.com:80/path/to/WritingABook }
                    |  implemented by { Path.To.Context }
                    |  accepted by {
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
                    |  }
                    |} described as "A simple authoring story"
                    |} described as "a parsing convenience"
                    |""".stripMargin
      parseAndValidate[Domain](input) { case (domain, messages) =>
        domain.stories mustNot be(empty)
        messages mustBe empty
        val story = domain.stories.head
        story.id.format mustBe "WritingABook"
        story.role.s mustBe "Author"
        story.capability mustBe LiteralString(4 -> 17, "edit on the screen")
        story.benefit mustBe LiteralString(5 -> 14, "revise content more easily")
        story.shownBy mustNot be(empty)
        story.shownBy.head.toString mustBe "http://example.com:80/path/to/WritingABook"
        story.implementedBy mustNot be(empty)
        story.implementedBy.head.format mustBe "Path.To.Context"
      }
    }
  }
}
