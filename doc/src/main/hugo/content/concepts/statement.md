---
title: "Statement"
draft: false
---

A Statement is an action that can be taken in response to a message. Statements
form the body of an [on clause]({{<relref "onclause.md">}}) which is what
[handlers]({{<relref "handler.md">}}) are composed of. There are many
kinds of statements, described in the table below.

|     Name     | Syntax Example                                               | Description                                                  |
|:------------:|:-------------------------------------------------------------|:-------------------------------------------------------------|
|    Become    | `become entity E to handler H`                               | Instructs an entity to change to a new handler               |
|    Call      | `call function F(args)`                                      | Call a function to get a result                              |
|    Error     | `error "message"`                                            | Produce an error with a message                              |
|   ForEach    | `foreach item in collection { ... }`                         | Invoke statements on each item of a multi-valued field       |
|     Let      | `let x = "condition"`                                        | Bind a name to a condition for use in when statements        |
|    Match     | `match "scenario" { case "cond1" { } default { } }`          | Select from multiple conditions/cases                        |
|    Morph     | `morph entity E to state S with record R`                    | Morph the state of an entity to a new state                  |
|   Prompt     | `prompt "description of action"`                             | A textually described action to be implemented               |
|    Return    | `return value`                                               | Return a value from a function                               |
|    Send      | `send event E to outlet O`                                   | Send a message to an outlet (asynchronous)                   |
|     Set      | `set field S.f to "value"`                                   | Set a field value                                            |
|    Tell      | `tell command C to entity E`                                 | Send a message to an entity directly                         |
|    When      | `when cond then ... [else ...] end`                          | Execute statements conditionally with optional else          |

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
 
