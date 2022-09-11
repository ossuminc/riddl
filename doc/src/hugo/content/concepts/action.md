---
title: "Action"
draft: false
---

An Action is something a program does. Actions are used in the 
[`then`]({{< relref "example.md#then" >}})
and 
[`but`]({{< relref "example.md#but" >}}) clauses of an 
[example]({{< relref "example.md" >}}). 

There are many kinds of Actions, described in the table below

|     Name     | Description                                                  |
|:------------:|:-------------------------------------------------------------|
|    Append    | Add an item to a field that is a collection                  |
|  Arbitrary   | A textually described arbitrary action                       |
|     Ask      | Send a message to an entity, asynchronously process result   |
|    Become    | Instructs an entity change to a new handler                  |
|   Compound   | Execute a group of nested actions                            |
|    Error     | Produce an error with a message                              |
| FunctionCall | Call a function to get a result                              |
|    Morph     | Morph the state of an entity to a new state                  |
|   Publish    | Publish a message to a pipe                                  |
|    Reply     | Provide the reply message to the entity that invoked a query |
|    Return    | Return a value from a function                               |
|     Set      | Set a field of the current state of an entity                |
|     Tell     | Send a message to an entity, do not wait for result          |
|    Yield     | Place a message on an entity's event pipe                    |

## Applicability
Not all actions can be used in every situation. The table below show the 
exceptions. Examples occur in
[handlers]({{< relref "handler.md" >}}) and they in turn occur in
[contexts]({{< relref "context.md" >}}),
[entities]({{< relref "entity.md" >}}),
[functions]({{< relref "function.md" >}}),
[projections]({{< relref "projection.md" >}}), and
[states]({{< relref "state.md" >}}).


|     Name     | Context | Entity | Function | Projection | State |
|:------------:|:-------:|:------:|:--------:|:----------:|:-----:|
|    Append    |    ⚠    |   ✓    |    ⚠     |     ✓      |   ✓   |
|  Arbitrary   |    ✓    |   ✓    |    ✓     |     ✓      |   ✓   |
|     Ask      |    ✓    |   ✓    |    ✓     |     ✓      |   ✓   |
|    Become    |    ⚠    |   ✓    |    ⚠     |     ✓      |   ✓   |
|   Compound   |    ✓    |   ✓    |    ✓     |     ✓      |   ✓   |
|    Error     |    ✓    |   ✓    |    ✓     |     ✓      |   ✓   |
| FunctionCall |    ✓    |   ✓    |    ✓     |     ✓      |   ✓   |
|    Morph     |    ⚠    |   ✓    |    ⚠     |     ⚠      |   ✓   |
|   Publish    |    ✓    |   ✓    |    ✓     |     ✓      |   ✓   |
|    Reply     |    ⚠    |   ✓    |    ⚠     |     ✓      |   ✓   |
|    Return    |    ✓    |   ⚠    |    ✓     |     ✓      |   ⚠   |
|     Set      |    ⚠    |   ✓    |    ⚠     |     ✓      |   ✓   |
|     Tell     |    ✓    |   ✓    |    ✓     |     ✓      |   ✓   |
|    Yield     |    ⚠    |   ✓    |    ✓     |     ✓      |   ✓   |


## Occurs In
* [Examples]({{< relref "example.md" >}}) - in the `then` and `but` clauses


## Contains
* [Expressions]({{< relref "expression.md" >}}) - to 