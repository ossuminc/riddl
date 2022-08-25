---
title: "Handlers"
draft: false
---

A *handler* is a definition of how to handle messages sent to a context or an
entity. There are four kinds of handlers depending on its parent definition
([context](contexts) or [entity](entities)) and whether it is defined with 
applicability, as shown in this table. 

| Occurs In | Applicability | Kind Of Handler        |   
|-----------|---------------|------------------------|
| Context   | None          | API Handler            |
| Context   | Projection    | Projection Handler     |
| Entity    | None          | Default Handler        |
| Entity    | State         | Specific State Handler |

## Context Handlers 
### API Handler
### Projection Handler
## Entity Handlers
Entities consume commands, queries, and events. A *handler* specifies what 
the entity should do for those messages. For handlers defined in an entity, 
there are two types, as defined in the sections below.

### Specific State Handler
A *specific state handler* is one that 
### Default Handler
A default state handler is the "catch all" for an entity. When there is no 
[Specific State Handler](#Specific State Handler) that provides processing 
for a message, the default handler is used. If the message is not processed 
by the handler, then the entity's processing for that message is 

## Occurs In
* [Entities](entities) - 
* [Contexts](contexts) -

## Contains
* [On Clauses](onclauses) - a specification of how to handle an event
