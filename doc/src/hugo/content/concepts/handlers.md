---
title: "Handlers"
draft: false
---

A *handler* is a definition of how to handle messages sent to a context or an
entity. There are four kinds of handlers depending on its parent definition
([context](context) or [entity](entity)) and whether it is defined with 
applicability, as shown in this table. 

| Occurs In | Applicability | Kind Of Handler       |   
|-----------|---------------|-----------------------|
| Context   | None          | API Handler           |
| Context   | Projection    | Projection Handler    |
| Entity    | None          | Default State Handler |
| Entity    | State         | State Handler         |

## API Handler
## Projection Handler
## Default State Handler
## State Handler

## Occurs In
* [Entities](entities) - 
* [Contexts](contexts) -

## Contains
* [On Clauses](onclauses) - a specification of how to handle an event