---
title: Handler
type: page
weight: 50
---

A _handler_ in an `entity` specifies how an entity handles its input.  
Handlers are specified with the `on` keyword. There are four kinds of handlers 
distinguished by the kind of message they handle (_command_, _event_, _query_,
and _reaction_) as detailed in the following sections 

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
type event NameWasUpdated { id: Id(AnEntity), newName: String }

on event NameWasUpdated { replace name with newName }
 
```
## Reaction Handler
A reaction handler is used to specify how an entity converts a foreign event 
(from another entity) into a command for altering its own state. This is also
how we avoid corruption of the entity's ubiquitous language by converting 
another bounded context's concept into the handler's entity's concept. 
```riddl
domain Path is { domain To is { context Context is {
  type ThingThatHappened is event { id: Id(Entity), whatHappened: String }
}}}

domain Foo { context Bar { entity Example {
    type ExternalThingHappened = event { id: Id(Example), whatHappened: String }
    on event Path.To.Context.ThingThatHappened yields ExternalThingHappened
}}}    
```
Notes:
* `Path.To.Context.ThingThatHappend` is known as a path identifier

## Query Handler

## Example
## syntax
Handlers are introduced into an entity with the `on` keyword.
```riddl
entity AnEntity is {
    type JustDoIt = command { id: Id(AnEntity), encouragement: String }
    type JustDidIt = event { id: Id(AnEntity), encouragement: String }
    on command JustDoIt yields JustDidIt
    
    on event JustDidIt update state EntityState
    
    on event OtherEntity.SomeEvent react with JustDoIt
    
    type JustGetIt = query { id: Id(AnEntity) }
    type JustGotIt = response { id: Id(AnEntity), count: Integer }
    on query JustGetIt return JustGotIt
}
```
