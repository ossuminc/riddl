---
title: "Statement"
draft: false
---

A Statement is an action that can be taken in response to a message. Statements 
form the body of an [on clause]({{<relref "onclause.md">}}) which is what 
[handlers]({{relref "handlers.md">}}] are composed of. There are many 
kinds of statements, described in the table below.

|     Name     | Description                                                  |
|:------------:|:-------------------------------------------------------------|
|  Arbitrary   | A textually described arbitrary statement                    |
|     Ask      | Send a message to an entity, asynchronously process result   |
|    Become    | Instructs an entity change to a new handler                  |
|    Error     | Produce an error with a message                              |
|   ForEach    | Invoke statements on each item of a multi-valued field       |
|  IfThenElse  | Evaluate a condition and choose execute a statement set      |
| FunctionCall | Call a function to get a result                              |
|    Morph     | Morph the state of an entity to a new state                  |
|   Publish    | Publish a message to a pipe                                  |
|    Reply     | Provide the reply message to the entity that invoked a query |
|    Return    | Return a value from a function                               |
|     Tell     | Send a message to an entity directly, do not wait for result |

## Level of Detail

Statements are aimed at writing pseudocode in a structured but abstract
way. RIDDL does not intend the system model to contain the code that will be
used in its implementation. It also expects the reader to be intelligent and
fill in the (obvious) gaps that are left missing. This leads to these objectives
for writing an [onclause's]({{<img relref "onclause.md" >}}):

* Converting the specification to executable code should be done by a human or
  a sufficiently capable AI/ML
* The statements are designed to capture interactions between the definitions
  of the model, and are specifically not Turing complete.
* Statements should capture significant references to data and
* There is no need to model computational expresses exhaustively when a simple
  natural language statement will do.

## Applicability

Not all statements may be used in every On Clause. It depends on the kind of
definition the statement is attached to. Statements occur in the
[onclause]({{< relref "onclause.md" >}}) of the
[handlers]({{< relref "handler.md" >}}) of all the
[vital]({{< relref "vital.md" >}}) definitions.
Note that the `Become` and `Morph` statements are only applicable to
statements in an Entity. The others are generally applicable.

## Occurs In
* [On Clause]({{< relref "onclause.md" >}})


## Contains

Some statements contain conditionals, values and path identifiers to reference
things in the system models. None of these are definitions. 
 
