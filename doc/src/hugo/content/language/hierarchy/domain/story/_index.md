---
title: "Story"
type: "page"
draft: "false"
weight: 20
---

A story is a domain definition the defines an agile user story. This is the same concept that Kent
Beck popularized in the early 2000s with eXtreme Programming. In RIDDL, a story is composed of the
following things:

* An `role` specification giving the role played by the narrator of the story
* A `capability` specification giving the capability used by the narrator
* A `benefit` specification providing the reason why the narrator wants to use the `capability`
* A `accepted by` specification expressed as Gherkin examples that defines done.
* A `implemented by` specification that links the story to the various RIDDL definitions involved in
  the implementation of this story

### Example

```riddl
story WritingABook is {
  role is "Author"
  capability is "edit on the screen"
  benefit is "revise content more easily"
  accepted by {
    example one {
      given "I need to write a book"
      when "I am writing the book"
      then "I can easily type words on the screen instead of using a pen"
    } described by "nothing"
    example two {
      given "I need to edit a previously written book"
      when "I am revising the book"
      then "I can erase and re-type words on the screen"
    } described as "nothing"
  }
  implemented by { ??? }
} described as "A simple authoring story"
```
