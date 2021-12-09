---
title: "Entities"
type: page
weight: 10
---

# Introduction
An entity in RIDDL is the same as it is in DDD which defines it this way:
{{% panel theme="success" header="Entity Definition" %}}
> _An object primarily defined by its identity is called an Entity._ 

and

> _Many objects are not fundamentally defined by their attributes, but_ 
> _rather by a thread of **continuity** and **identity**._

{{% /panel %}}
 
There are three main aspects to this definition of entity:
* Entities are objects (containing both state and function) which means they can
  represent physical objects, mental constructs, concepts, etc. It also means
  they can be implemented as objects, actors, or collections of functions that
  share exclusive access to some data
* Entities have an identity;  they are identified by some unique value
  that no other entity of the same type may have.
* Entities are continuous; they have a lifecycle, evolving from creation, 
  through their useful lifespan, to destruction. 

An entity is the fundamental processor of work in a reactive system. They are
most often implemented in software as an actor or a class.

# Syntax
{{% panel theme="success" header="Entity Definition" %}}
An entity is defined with the `entity` keyword using this syntax:
```ebnf
entity = entity kind, "entity", "is", "{",  
    entity options, entity definitions, "}", description ;

entity kind = [ "device" | "actor" | "concept"] ;

entity options = single option | multi options ;

single-option = "option", "is", entity option kinds;

multi-option = "options", "(", { entity option kinds },  ")";

entity option kinds = "event sourced" | "value" | "aggregate" | "persistent" |
   "consistent" | "available";

entity definition = consumer | feature | function | invariant | typeDef | state;
  
entity definitions =  entity definition { entity definition } ;
```
{{% /panel %}}
For example:
```riddl
device entity Printer is {
  options(value, available)
  // ...
}
```
The optional entity kind prefix is a directive that suggests how the entity 
might handle its state and received messages. In the example above, we 
expect the "Printer" entity to be a physical device. An "actor" entity in
of the same name could be expected to be a person who prints. 

The options available suggest how the entity might handle its state and 
message input:
* _event sourced_ - indicates that event sourcing is used in the persistence 
  of the entity's states, storing a log of change events
* _value_ - indicates that only the current value of the entity's state is 
  persisted, recording no history off the change events 
* _aggregate_ - indicates this entity is an 'aggregate root entity' as 
  defined by DDD.
* _persistent_ - indicates that this entity should persist its state to 
  stable sotrage.
* _consistent_ - indicates that this entity tends towards Consistency as 
  defined by the CAP theorem.
* _available_ - indicates that this entity tends towards Availability as 
  defined by the CAP theorem.

Entities also have several contained definitions which will be described in 
the sections below:
* [_State_](state) - The information retained by the entity through its 
  lifecycle
* [_Handler_]() - A specification of how the entity processes commands and
  queries, generating events and results, correspondingly
* [_Functions_](functions) - Auxiliary functions definitions to implement the
  frequently used business logic referenced from the Handler definition
* [_Invariants_](invariants) - Logical assertions that must always be true 
  through an
  entity's lifecycle.
* [_Adaptor_]() - An adaptor converts the events from another entity into
  commands to this entity. This is how an entity reacts to its environment.

## Contained Definitions
### State
### Handler
### Features
### Invariants
# Consumption 
Entities consume commands and queries and produce events and results, 
correspondingly. They also hold state, whether persistent or not. Entities use 
event sourcing to keep track of the entire history of changes to the entity's
state.  Entities have a globally unique immutable persistent identifier, 
type `Id`, which provides the means to reference the entity in its context or
between contexts.  For example, If two instances of the same object have different attribute values, but same identity value, 
Thus, entities are the single source of truth for a particular id. 
they are the same entity.
![Entities](../../../../../static/images/entities.png "Entities")

Entities can also contain business Logic. Actors in Akka, Entities in Lagom. 
Contrary to Value Objects, an Entity's immutable identity conveys equivalence.
Individual pieces of attribute of entity can change.
