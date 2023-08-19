---
title: "Action"
draft: false
---

A Statement is an action that can be taken in response to a message. Statements 
form the body of an [on clause]({{<relref "onclause.md">}}) which is what 
[handlers]({{relref "handlers.md">}}] are composed of. There are many 
kinds of statements, described in the table below.

|     Name     | Description                                                    |
|:------------:|:---------------------------------------------------------------|
|  Arbitrary   | A textually described arbitrary statement                      |
|     Ask      | Send a message to an entity, asynchronously process result     |
|    Become    | Instructs an entity change to a new handler                    |
|    Error     | Produce an error with a message                                |
|   ForEach    | Invoke actions on each item of a multi-valued field            |
| FunctionCall | Call a function to get a result                                |
|    Morph     | Morph the state of an entity to a new state                    |
|   Publish    | Publish a message to a pipe                                    |
|    Reply     | Provide the reply message to the entity that invoked a query   |
|    Return    | Return a value from a function                                 |
|     Tell     | Send a message to an entity directly, do not wait for result   |

## Applicability
Not all statements may be used in every On Clause. It depends on the kind of 
definition the statement is attached to. Statements  occur in
[handlers]({{< relref "handler.md" >}}) and they in turn occur in
[contexts]({{< relref "context.md" >}}),
[entities]({{< relref "entity.md" >}}),
[functions]({{< relref "function.md" >}}),
[projections]({{< relref "projection.md" >}}), and
[states]({{< relref "state.md" >}}). The table below shows which definitions 
are applicable to each kind of statement.


|     Name     | Context | Entity | Function | Projection | State |
|:------------:|:-------:|:------:|:--------:|:----------:|:-----:|
|  Arbitrary   |    ✓    |   ✓    |    ✓     |     ✓      |   ✓   |
|     Ask      |    ✓    |   ✓    |    ✓     |     ✓      |   ✓   |
|    Become    |    ⚠    |   ✓    |    ⚠     |     ⚠      |   ✓   |
|    Error     |    ✓    |   ✓    |    ✓     |     ✓      |   ✓   |
|  ForEach     |    ✓    |   ✓    |    ✓     |     ✓      |   ✓   |
| FunctionCall |    ✓    |   ✓    |    ✓     |     ✓      |   ✓   |
|    Morph     |    ⚠    |   ✓    |    ⚠     |     ⚠      |   ✓   |
|   Publish    |    ✓    |   ✓    |    ✓     |     ✓      |   ✓   |
|    Reply     |    ⚠    |   ✓    |    ⚠     |     ✓      |   ✓   |
|    Return    |    ✓    |   ⚠    |    ✓     |     ✓      |   ⚠   |
|     Tell     |    ✓    |   ✓    |    ✓     |     ✓      |   ✓   |


## Occurs In
* [On Clause]({{< relref "onclause.md" >}})


## Contains
Nothing. Statements are leaf definitions.
 
