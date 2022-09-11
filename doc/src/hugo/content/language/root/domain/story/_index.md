---
title: "Story"
type: "page"
draft: "false"
weight: 20
---


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
