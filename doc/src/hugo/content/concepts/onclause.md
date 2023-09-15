---
title: "On Clauses"
draft: false
---

An On Clause specifies how to handle a particular kind of message or situation
as part of the definition of a [handler]({{< relref "handler.md" >}}. 
An On Clause is associated with a specific message definition and contains 
[statements]({{< relref statement.md >}}) that define the handling of that 
message by the handler's parent. An On Clause is also optionally associated 
with an [entity]({{< relref entity.md >}}) or [pipe]({{< relref pipe.md>}})) 
as the sender of a message.

There are fours kinds of On Clauses:
* _Initialization_ - when the definition is created and initialized
* _Termination_ - when the definition is terminated 
* _Message_ - when the definition receives a specific kind of message
* _Other_ - whenthe definition receives a message not otherwise handled

## Occurs In

* [Handlers]({{< relref "handler.md" >}}) - the handler to which the On 
  clause is applied

## Contains
* [Statement]({{< relref "statement.md" >}}) - specifies what should happen 
  when the event occurs
