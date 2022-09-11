---
title: "Adaptor"
draft: false
---

An adaptor's purpose is to _adapt_ one [context]({{< relref "context.md" >}})
to another. In Domain-Driven Design, this concept is known as an
_anti-corruption layer_.  We didn't like that term for a variety of reasons 
so we have renamed the concept as _adaptor_ in RIDDL. Same idea, different name.

Adaptors simply bundle together a set of 
[adaptations]({{< relref "adaptation.md" >}}). The actions they take are 
aimed at the [context]({{< relref "context.md" >}}) that contains the 
adaptor or any [entity]({{< relref "entity.md" >}}) or 
[projection]({{< relref "projection.md" >}}) within that context. 


## Occurs In
* [Contexts]({{< relref "context.md" >}})

## Contains
* [Adaptations]({{< relref "adaptation.md" >}})
