---
title: "Stories"
draft: false
---

A story in RIDDL is a definition that defines a user story. This is the same 
concept as the idea 
[Kent Beck]({{< relref "../introduction/who-made-riddl-possible.md#kent-beck">}}) 
[introduced in 1997](https://en.wikipedia.org/wiki/User_story#History). In 
RIDDL, a story gets a little more involved than the 
[usual formulations](https://en.wikipedia.org/wiki/User_story#Common_templates) 
of a user story:
> As an {actor}, I would like {capability}, so that {benefit}

or
 
> In order to {receive benefit}, as an {actor}, I can {capability}

which have these three ideas:
* An `actor` specification giving the role played by the narrator of the story
* A `capability` specification giving the capability used by the narrator
* A `benefit` specification providing the reason why the narrator wants to 
  use the `capability`

Additionally, a RIDDL Story provides a few more definitional clauses that 
relate the story to other RIDDL components:
* The `scope` clause identifies the domain that bounds the set of definitions 
  used by the story. This helps locate the story in the nested hierarchy of 
  domains. 
* The `uses` clause specifies the components ([contexts](context), 
  [entities](entity), [projections](projection)) that are used in the 
  interactions of the story. 
* The `interactions` define and label the interactions between components. 
* Some [examples](example), expressed as Gherkin statements, define the 
  test cases that should be used to test the story, as a definition of "done".

Stories are designed to produce diagrams like the [C4 Model For Software Architecture](https://c4model.com); specifically the
[Dynamic Diagram](https://c4model.com/#DynamicDiagram), like this:
![C4 Dynamic Diagram](https://static.structurizr.com/workspace/36141/diagrams/SignIn.png)
This allows the intended interaction of some actor with the system being 
designed in RIDDL to support a detailed definition of a
[user story](https://en.wikipedia.org/wiki/User_story).

## Occurs In
* [Domains]({{< relref "domain.md" >}})

## Contains
* [Examples]({{< relref "example.md" >}})
