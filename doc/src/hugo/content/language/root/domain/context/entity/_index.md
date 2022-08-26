---
title: "Entities"
type: "page"
weight: 10
draft: "false"
---

An entity in RIDDL is the same as it is in DDD which defines it this way:
{{% hint tip  %}}
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

An entity is the fundamental processor of work in a reactive system and in a 
RIDDL specification. Entities are most often implemented in software as an 
actor or a class in object-oriented programming.

## Identity
Entities have a unique immutable persistent identifier, much like people have names except our 
personal names are not unique. The unique identifier is used to locate the entity in a computing 
system and for other computing purposes. These immutable and unique identifiers convey 
equivalence. That is when two values of an identifier are the same, then by definition, they 
must refer to the same entity.  Changing the state of the entity does not break this equivalence. 
type `Id`, which provides the means to reference the entity in its context or
between contexts. an Entity's immutable identity conveys equivalence.
Individual pieces of attribute of entity can change.

## Equality
Entities hold state, whether that state is persistent or not. However, for 
entities, the most important state value is the unique identifier for that entity. 
Consider this diagram:

![Entities](../../../../../../static/images/entities.png "Entities")

Two instances of the same Entity may have different attribute values, but 
because both instances have the same identity value, they represent the same 
Entity. The identifier "John Smith" is used in two entities that differ in their
state. By definition, this means they refer to the same entity.  However, when
you compare "John Smith" with "Jane Smith", they are not the same entity, even
if all their other attributes are the same.


## Example 

Here is an outline of the definition of an entity

```riddl
entity Example is {
  options(...))          // Optional aspects of the entity and code gen hints
  invariant i is { ... } // Logical assertions that must always be true 
  state s is { ... }     // Information retained by the entity
  function f is { ... }  // Functions to eliminate redundancy in handlers 
  handler h is { ... }   // How the entity handles messages sent to it
}
```

## Details
The links below provide more details on the various sub-definitions of an entity:

{{< toc-tree >}}
