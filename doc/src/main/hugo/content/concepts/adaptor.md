---
title: "Adaptor"
draft: false
---

An adaptor's purpose is to _adapt_ one [Context]({{< relref "context.md" >}})
to another [Context]({{< relref "context.md" >}}).  In Domain-Driven Design, 
this concept is known as an _anti-corruption layer_ that keeps the 
ubiquitous language of one context "corrupting" the language of another 
context.  The authors of RIDDL didn't like that term for a variety of reasons
so we have renamed the concept as _adaptor_ in RIDDL. Same idea, different name.

## Message Translation
Adaptors do their work at the level of messages sent between 
[Contexts]({{< relref "context.md" >}}). This is done using one or 
more [Handlers]({{< relref handler.md >}}). Each handler specifies 
how messages are translated into other messages and forwarded to the target 
[context]({{< relref "context.md" >}}).   

## Target Context
Adaptors are only definable within a containing 
[Context]({{< relref context.md >}}) which provides one participant of the 
translation. The other [Context]({{< relref "context.md" >}}), known as the 
*target* context, is specified within the definition of the adaptor. 

## Adaptation Directionality
Adaptors only translate in one direction, between the containing context and 
the target context. However, multiple Adaptors can be defined 
to achieve bidirectional adaptation between
[Contexts]({{< relref "context.md" >}}). 
The directionality of an Adaptor is specified in the definition of the adaptor.
This leads to two kinds of adaptors: inbound and outbound.

## Inbound Adaptors
Inbound adaptors provide an adaptation that occurs from the 
[Context]({{< relref "context.md" >}}) referenced in the adaptor to the
[Context]({{< relref "context.md" >}}) containing the adaptor. 

## Outbound Adaptors
Outbound adaptors provide an adaptation that occurs from the
[Context]({{< relref "context.md" >}}) containing the adaptor to the
[Context]({{< relref "context.md" >}}) referenced in the adaptor.

## Occurs In
* [Contexts]({{< relref "context.md" >}})

## Contains
* [Authors]({{< relref "author.md" >}})
* [Handlers]({{< relref "handler.md" >}})
* [Terms]({{< relref "term.md" >}})
