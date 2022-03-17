---
title: "Handler"
type: "page"
weight: 50
draft: "false"
---

A _handler_ in an `entity` specifies how an entity handles its input messages.  
Handlers are specified with the `handler` keyword and enclose a set of `on` clauses that specify
what to do with a given event when that handler is active. There are four kinds of `on` clauses
distinguished by the kind of message they handle (_command_, _event_, _query_, and _reaction_) as
detailed in the following sections

## Command Handler
A command handler specifies which persistent event is generated for a given 
command.  For example:

```riddl
type JustDoIt = command { id: Id(AnEntity), encouragement: String }
type JustDidIt = event { id: Id(AnEntity), encouragement: String }
on command JustDoIt yields JustDidIt
```

## Event Handler
An event handler specifies how an event modifies the state of the entity. 
```riddl
state State is { name: String }

type event NameWasUpdated { id: Id(AnEntity), newName: String }

on event NameWasUpdated { replace State.name with NameWasUpdated.newName }
 
```
## Reaction Handler
A reaction handler is used to specify how an entity converts a foreign event 
(from another entity in the same bounded context) into a command for altering 
its own state. This is also
how we avoid corruption of the entity's ubiquitous language by converting 
another bounded context's concept into the handler's entity's concept. 
```riddl
domain Path is { 
  domain To is { 
    context Context is {
      type ThingThatHappened is event { id: Id(Entity), whatHappened: String }
    }
  }
}

domain Foo { 
  context Bar { 
    entity Example {
      type ExternalThingHappened = event { 
        id: Id(Example), 
        whatHappened: String 
      }
      on event Path.To.Context.ThingThatHappened yields ExternalThingHappened
    }
  }
}    
```
Notes:
* `Path.To.Context.ThingThatHappend` is known as a
  [path identifier](../../../../hierarchy#Path_Identifiers).

## Query Handler

A query handler associates a query message to the result message that the query returns along with
the SQL statement that yields the result set.

```riddl
type JustGetIt = query { id: Id(AnEntity) }
type JustGotIt = response { id: Id(AnEntity), count: Integer }
on query JustGetIt return JustGotIt from "SELECT id, count FROM AnEntity WHERE id = %id"
```

## Defining Multiple Handlers

An entity can make use of multiple handlers so that the behavior of and entity can be changed. There
can be only one handler active at any moment, but an entity can change which handler is active in
response to any message. This ability permits a set of handlers to model a finite state machine
where each handler is a state and each on clause is a transition. When a handler is active, any
messages received that are not explicitly defined by the handler will simply be ignored. 
