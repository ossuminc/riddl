---
title: "Epics"
draft: false
---

An epic in RIDDL is a definition that defines a large user story with a set 
of use cases.  This is the same 
concept as the idea 
[Kent Beck]({{< relref "../introduction/who-made-riddl-possible.md#kent-beck">}}) 
[introduced in 1997](https://en.wikipedia.org/wiki/User_story#History). In 
RIDDL, a story gets a little more involved than the 
[usual formulations](https://en.wikipedia.org/wiki/User_story#Common_templates) 
of a user story:
> As an _{actor}_, I would like _{capability}_, so that _{benefit}_

or
 
> In order to receive _{benefit}_, as an _{actor}_, I can _{capability}_

which have these three ideas:
* An `actor` that provides the role played by the narrator of the story
* A `capability` that provides the capability used by the narrator
* A `benefit` that provides the reason why the narrator wants to 
  use the `capability`

A RIDDL Story also provides a set of cases that relate the story to 
other RIDDL components through the steps taken for each [`case`]({{< relref 
"case.md" >}}. Each case specifies a set of `interactions` that define and label
the interactions between other RIDDL definitions such as 
[elements]({{< relref "element.md" >}}), 
[entities]({{< relref "entity.md" >}}), and 
[projections]({{< relref "projection.md" >}}). Cases can also define  
[examples]({{<relref "example" >}}), to outline user acceptance testing.

Stories are designed to produce sequence diagrams. This allows the intended 
interaction of some actor (human or not) with the system being 
designed in RIDDL to support a detailed definition of a
[user story](https://en.wikipedia.org/wiki/User_story).

## Occurs In
* [Domains]({{< relref "domain.md" >}})

## Contains)
* [Cases]({{< relref "case.md" >}})
