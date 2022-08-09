---
title: "Entities"
type: "page"
weight: 10
draft: "false"
---

An entity in RIDDL is the same as it is in DDD which defines it this way:
{{% hint nada  %}}
**Entity Definitions**
> _An object primarily defined by its identity is called an Entity._ 

and

> _Many objects are not fundamentally defined by their attributes, but_ 
> _rather by a thread of **continuity** and **identity**._
{{% /hint %}}
 
There are three main aspects to this definition of entity:
* Entities are objects (containing both state and function) which means they can
  represent physical objects, mental constructs, concepts, etc. It also means
  they can be implemented as objects, actors, or collections of functions that
  share exclusive access to some data
* Entities have an identity;  they are identified by some unique value
  that no other entity of the same type may have.
* Entities are continuous; they have a lifecycle, evolving from creation, 
  through their useful lifespan, to destruction. 

An entity is the fundamental processor of work in a reactive system and in a RIDDL specification.  
Entities are most often implemented in software as an actor or a OOP class.

### Identity
Entities have a unique immutable persistent identifier, much like people have names except our 
personal names are not unique. The unique identifier is used to locate the entity in a computing 
system and for other computing purposes. These immutable and unique identifiers convey 
equivalence. That is when two values of an identifier are the same, then by definition, they 
must refer to the same entity.  Changing the state of the entity does not break this equivalence. 
type `Id`, which provides the means to reference the entity in its context or
between contexts. an Entity's immutable identity conveys equivalence.
Individual pieces of attribute of entity can change.

Entities can also contain business Logic. Actors in Akka, Entities in Lagom.
Contrary to Value Objects, 

# Consumption
Entities consume commands and queries and produce events and results,
correspondingly. They also hold state, whether persistent or not. Entities use
event sourcing to keep track of the entire history of changes to the entity's
state. This is important because two instances of the same Entity may have different 
attribute values, but because both instances have the same identity value, they represent 
the same Entity. Which, then, is correct? The answer is neither. Both would need to 
reference the Event Log for the Entity to get the most current and correct state of
this Entity. Thus, entities are the single source of truth for a particular id.
They are the same entity.
![Entities](../../../../../../static/images/entities.png "Entities")




For example:
```riddl
entity Printer is {
  options(value, available, kind("device"))
  // ...
}
```

### Aspects
Entities also have several contained definitions which specify various aspects of the entity. 
These aspects are detailed on the following pages: 

* [_Options_](options) - Various options that provide high level optional aspects of the entity.
* [_States_](state) - The information retained by the entity through its lifecycle
* [_Handlers_](handler) - A specification of how the entity processes 
  commands, events, queries, and reactions to events from other entities in the same bounded 
  context.
* [_Functions_](function) - Auxiliary function definitions to implement the
  frequently used business logic referenced from the Handler definitions
* [_Invariants_](invariants) - Logical assertions that must always be true 
  through an entity's lifecycle to enforce business rules
* [_Features_](features) - High level descriptions of features as an outline for both developing 
  the system and to test the entity functionality. 
* [_Adaptors_]() - An adaptor converts events from another bounded context into
  commands to this entity. This is how an entity reacts to its external environment while the 
  handler provides a way to react to events within the internal environment (its bounded context).

