---
title: "Adaptor"
draft: false
---

An adaptor's purpose is to _adapt_ one [context]({{< relref "context.md" >}})
to another. In Domain-Driven Design, this concept is known as an
_anti-corruption layer_.  We didn't like that term for a variety of reasons 
so we have renamed the concept as _adaptor_ in RIDDL. Same idea, different name.

Adaptors simply bundle together one or more 
[handlers]({{< relref handler.md >}}) that specify how to do
the translation. Messages sent to the containing bounded context 
are first translated by the adaptor.  

## Occurs In
* [Contexts]({{< relref "context.md" >}})

## Contains
* [Authors]({{< relref "author.md" >}})
* [Handlers]({{< relref "handler.md" >}})
* [Terms]({{< relref "term.md" >}})
