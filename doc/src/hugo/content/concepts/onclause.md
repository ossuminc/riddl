---
title: "On Clauses"
draft: false
---

An On Clause specifies how to handle a particular kind of message
as part of the definition of a [handler]({{< relref "handler.md" >}}. 
An On Clause is associated with a specific message definition and contains 
an [example]({{< relref example.md >}}) that defines the handling of that 
message by the handler's parent. An On Clause is also optionally associated 
with an [entity]({{< relref entity.md >}}) or [pipe]({{< relref pipe.md>}})) 
as the sender of a message.  


## Occurs In

* [Handlers]({{< relref "handler.md" >}}) - the handler to which the On 
  clause is applied

## Contains
* [Examples]({{< relref "example.md" >}}) - specifies what should happen when 
  the event occurs
