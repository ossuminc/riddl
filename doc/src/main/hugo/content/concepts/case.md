---
title: "Story Case"
draft: "false"
---

Stories are specified with multiple `case` definitions that it contains. A 
*case* in this context is a use case, that is, a set of steps that define 
the interaction between components. Multiple *cases* are often needed to 
show both happy and unhappy paths.

A case is composed of a set of steps. Those steps consist of a described 
relationship between two components. These can be general (any component to 
any component) or specific for certain pairs of components. 

The following table shows the pairings recognized:

|  keyword  | From  |   To    | Description                                 |
|:---------:|:-----:|:-------:|:--------------------------------------------|
| arbitrary |  any  |   any   | Arbitrary relationship between components   |
|   tell    |  any  | entity  | Send a message to an entity asynchronously  |
|  publish  |  any  |  pipe   | Publish a message to a pipe                 |
| subscribe |  any  |  pipe   | Subscribe to a pipe                         |
|   saga    |  any  |  saga   | Initiate a saga                             |
|  select   | user | element | Select an item from application element     |
|  provide  | user | element | Provide input data too application          |
|  present  | user | element | Cause an application to present info        |
 

## Occurs In
* [Epic]({{< relref "epic.md" >}})
* [Use Case]({{< relref "./case.md" >}})

## Contains)
* [Statements]({{< relref "statement.md" >}})

