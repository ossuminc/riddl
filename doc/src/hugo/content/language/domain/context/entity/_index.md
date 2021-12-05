---
title: "Entities"
type: page
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


# Definition Syntax
{{% panel theme="success" header="Entity Definition" %}}
An entity is defined with the `entity` keyword using this syntax:
```ebnf
entity = entity kind, "entity", "is", "{",  
    entity options, entity definitions, "}", description ;

entity kind = "device" | "person" | "concept" ;

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
```

Entities have several aspects in RIDDL:
* __Kind__ - A directive that indicates how the entity handles its state
* __State__ - The information retained by the entity through its lifecycle
* __Handler__ - A specification of how the entity processes commands and
  queries, generating events and results, correspondingly
* __Functions__ - Auxilliary functions definitions to implement the
  frequently used business logic referenced from the Handler definition
* __Invariants__ - Logical assertions that must always be true through an
  entity's lifecycle.
* __Adaptor__ - An adaptor converts the events from another entity into
  commands to this entity. This is how an entity reacts to its environment.
* 

Defining an entity involves 


Entities are consumers and producers of messages (commands, events, queries, 
and results). They also hold state, whether persistent or not. Entities use 
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
