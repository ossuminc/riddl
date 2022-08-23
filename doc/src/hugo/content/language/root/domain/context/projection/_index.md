---
title: "Projections"
type: "page"
weight: 20
draft: "false"
---

A `projection` can be defined in a [context](../_index.md) to improve
query performance across a large number of entities by providing 
a read-only view of persistent state across a set of entities.

## Necessity
Projections are necessary since persistent entities use event sourcing which 
is not a query-friendly data layout since it is merely a sequential event log
that indicates what changed. To query this event log one would have to 
reconstruct the current state from each entity's set of events which could
be exceedingly time-consuming. 

Queries involving only a single entity never need a projection since the
query can be satisfied quite simply by examination of a single entity. When a 
query must search multiple entities then projections are needed to satisfy
that query quickly. Otherwise, a complete scan of all the entities and the 
reconstruction of each of them would be needed.

Because of the foregoing, projections may only be defined in a 
[context](../../context/_index.md) or a [domain](../../../domain/_index.md). 
This requirement limits the set of entities over which the projection is
defined to the set reachable as descendents of that context or domain.

## Definition
```riddl
context Example {
  entity A { state a { a: Integer, A: String } }
  entity B { state b { b: String, B: Integer, ARef: Id(A) } } 
  projection "A-and-B" {
    for entity A {
      A: String
      c: Date
    }
    for entity B {
      B: Integer
      ARef: Id(A)
    }
    query GetRecentB {
      requires { earliestDate: Date }
       fetch(B)
       from(A,B)
       where(A.c >= @earliestDate) 
    }
  }
}
```

A projection definition 
Projections are constructed by handling events. 

Here's the projection process. Usually, events are logged as they are kept appended at the end of
the log file. Logs are string and text. To retrieving meaningful information out of logs, logs are
transformed into a more query-friendly format and stored in queriable repository or DB.

![CQRS/ES](../../../../../../static/images/cqrs-es.png "CQRS/ES")
