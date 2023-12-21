---
title: "Epics"
draft: false
---

An epic in RIDDL is a definition that defines a set of interactions between 
a [User]({{< relref "user.md" >}}) and teh system. The epic begins with
an overall [UserStory ]({{< relref "user-story.md" >}}) which is the same
idea [Kent Beck]({{< relref "../introduction/who-made-riddl-possible.md#kent-beck">}}) 
[introduced in 1997](https://en.wikipedia.org/wiki/User_story#History). In 
RIDDL, a story gets a little more involved than the 
[usual formulations](https://en.wikipedia.org/wiki/User_story#Common_templates) 
of a user story:
> As an _{user}_, I would like _{capability}_, so that _{benefit}_

or
 
> In order to receive _{benefit}_, as an _{user}_, I can _{capability}_

which have these three ideas:
* An `user` that provides the role played by the narrator of the story
* A `capability` that provides the capability used by the narrator
* A `benefit` that provides the reason why the narrator wants to 
  use the `capability`

A RIDDL Epic also provides a set of use cases that relate the story to
other RIDDL components through the interactions taken in each
[`use case`]({{< relref "use-case.md" >}}. Each use case specifies a set of
`interactions` that define and labels the interactions between other RIDDL
definitions such as
[elements]({{< relref "element.md" >}}),
[entities]({{< relref "entity.md" >}}), and
[projectors]({{< relref "projector.md" >}}).
Cases can also outline user acceptance testing.

User Stories and use cases are designed to produce sequence diagrams. 
This allows the intended interaction of some user (human or not) 
with the system being modeled in RIDDL to support a detailed definition of a
[user story](https://en.wikipedia.org/wiki/User_story).

## Occurs In
* [Domains]({{< relref "domain.md" >}})

## Contains
* [User Stories]({{< relref "user-story.md" >}})
* [Use Cases]({{< relref "use-case.md" >}})
