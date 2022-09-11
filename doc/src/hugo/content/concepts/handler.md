---
title: "Handlers"
draft: false
---

A *handler* is a definition of how to handle 
[messages]({{< relref "message.md" >}}). A *handler* contains a set of
[on clauses]({{< relref "onclause.md" >}}) that specify what to do for 
the various kinds of messages.  

There are several kinds of handlers depending on the definition type of the 
parent definition. A quick summary is shown in this table:

| Occurs In  | Handler Focus                                        |   
|------------|------------------------------------------------------|
| Adaptor    | Adapt messages for a bounded context                 |
| Context    | Implements API of the stateless context              |
| Entity     | Handler to use on new entity before any morph action |
| Projection | Handle updates and queries on the projection         |
| State      | Handle messages while entity is in that state        |

More details are provided in the sections below. 

## Adaptor Handlers
## Context Handlers 
## Entity Handlers
## Projection Handler
## State Handler
A *state handler* is one that processes messages while an entity is in that 
state. 

### Default Handler
A default state handler is the "catch all" for an entity. When the entity is 
new, or otherwise not in a specific state, this default handler is used to
process each message. If the message is not processed by the handler, then 
the entity's processing for that message is null (message ignored). 

## Occurs In
* [Adaptors]({{< relref "adaptor.md" >}})
* [Contexts]({{< relref "context.md" >}})
* [Entities]({{< relref "entity.md" >}}) 
* [Projections]({{< relref "projection.md" >}})
* [State]({{< relref "state.md" >}})

## Contains
* [On Clauses]({{< relref "onclause.md" >}}) - a specification of how to 
  handle an event
