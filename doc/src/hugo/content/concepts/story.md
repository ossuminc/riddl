---
title: "Stories"
draft: false
---

A story in RIDDL is a definition that defines a user story. This is the same 
concept to that 
[Kent Beck]({{< relref "../introduction/who-made-riddl-possible.md#kent-beck">}}) 
[introduced in 1997](https://en.wikipedia.org/wiki/User_story#History). In 
RIDDL, a story gets a little more involved than the 
[usual formulations](https://en.wikipedia.org/wiki/User_story#Common_templates) 
of a user story:
> As a {role}, I would like {capability}, so that {benefit}

or
 
> In order to {receive benefit} as a {role}, I can {goal/desire}

include these three ideas too:
* An `role` specification giving the role played by the narrator of the story
* A `capability` specification giving the capability used by the narrator
* A `benefit` specification providing the reason why the narrator wants to use the `capability`

Additionally, a RIDDL Story definition has these two specifications:
* Some [designs](design) expressed as a C4 Dynamic Diagram using RIDDL 
  components.
* Some [examples](example)expressed as Gherkin statements that defines done.


## Occurs In
* [Domains]({{< relref "domain.md" >}})

## Contains
* [Examples]({{< relref "example.md" >}})
* [Design]({{< relref "design.md" >}})
