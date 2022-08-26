---
title: "Context Handler"
type: "page"
weight: 50
draft: "false"
---

A `handler` definition in a [context](_index.md) specifies how that 
bounded context should handle messages sent to it. 

{{% hint info %}}
Note that an
[Entity](entity/) can define a handler too, but they are different than
the handlers defined in a context. See [Entity Handler](entity/handler.md) for 
more details.
{{% /hint %}}

There are two kinds of handlers as described in the following sections. They
are differentiated by the existence of a `for` clause in their definition. If
they have `for` clause, then they are [Projection Handlers](#Projection Handler)
; otherwise they are [API Handlers](#API Handler)

## Projection Handler

A `handler` that has a `for projection <path>` clause specifies how events 
should update a projection's data set. There can only be one handler per 
projection defined in the `context`. The `handler` definition for a `projection`
must be defined in the same `context` as the `projection`.

## API Handler

When a `handler` definition does not have a `for projection <path>` clause, it
specifies how to handle the messages sent to the context; in other words it
allows a `context` to represent an arbitrary stateless application programming
interface(API).  This permits higher level functional gateways to summarize 
the behavior of entire subdomains and bounded contexts.

### Always Stateless
APIs are always stateless.  Any state that needs to be saved when an
API function is invoked should be done by sending commands to a persistent
entity from an `on` clause in the handler. 

### Defining Multiple Handlers API Handlers

A context can define multiple API handlers. The will simply be combined to form
the full API. This allows for modula definition of the API. 
