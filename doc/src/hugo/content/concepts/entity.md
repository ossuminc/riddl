---
title: "Entity"
draft: false
resources:
  - name: "entities"
    src: "/images/entities.png"
    title: "Entity Equivalence"
    params:
      credits: "[Lightbend](https://lightbend.com/)"
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
* Entities are in the software implementation of the model are objects, 
  containing both state and function. They can both _be_ and _do_.  
  This means they can represent any physical object, a concept, etc. 
* Entities have an identity;  they are identified by some unique value
  that no other entity of the same type may have.
* Entities are continuous; they have a lifecycle, evolving from creation,
  through their useful lifespan, to destruction.

An entity is the fundamental processor of work in a reactive system and in a
RIDDL model. Entities are most often implemented in software using one of 
these techniques:
* using the actor model where actors process messages and encapsulate state.
* object-oriented programming which also encapsulate states and defines 
  functions to manipulate that state. 
* functional programming where a collection of functions process messages or 
  function calls using exclusive access to some data.  

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

<div style="text-align: center">

![Entity Equivalence](/images/entities.png "Entity Equivalence")

</div>

<!-- 
The following puts out nothing with geekdoc 0.35.2, unfortunately :(

{{< img name="entities" size="origin" lazy="false" >}}

-->

Two instances of the same Entity may have different attribute values, but
because both instances have the same identity value, they represent the same
Entity. The identifier "John Smith" is used in two entities that differ in their
state. By definition, this means they refer to the same entity.  However, when
you compare "John Smith" with "Jane Smith", they are not the same entity, even
if all their other attributes are the same.

## Options
Entities can have various optional characteristics as shown in the sections 
below

### event sourced
*Arguments*: None
*Implication*: The entity should use event sourcing to store its state as a 
log of change events

### value
* *Arguments*: None
* *Implication*: The entity should store only its latest value without using 
  event sourcing.

### transient
* *Arguments*: None
* *Implication*: The entity should not persist its state at all and is only  
  available in transient memory. All entity values will be lost when the 
  service is stopped.

### aggregate
* *Arguments*: None
* *Implication*: The entity is an aggregate root entity through which all 
  commands and queries are sent on behalf of the aggregated entities.

### consistent
* *Arguments*: None
* *Implication*: The entity's implementation should favor consistency over  
  availability in [Erik Brewer's]({{< relref "../introduction/who-made-riddl-possible.md#eric-brewer" >}})
  [CAP theorem](https://en.wikipedia.org/wiki/CAP_theorem).

### available
* *Arguments*: None
* *Implication*: The entity's implementation should favor availability over 
  consistence in [Erik Brewer's]({{< relref "../introduction/who-made-riddl-possible.md#eric-brewer" >}})
  [CAP theorem](https://en.wikipedia.org/wiki/CAP_theorem). 

### finite state machine
* *Arguments*: None
* *Implications*: The entity is intended to implement a finite state machine.

### message queue
* *Arguments*: None
* *Implications*: The entity should allow receipt of commands and queries via a
  message queue.

### kind
* *Arguments:* one string indicating the kind of entity
* *Implications*: The general kind of entity being defined. This option takes a
  single value which provides the kind of entity.  Examples of useful kinds 
  are "device", "actor", "concept", "machine". This entity option may be used
  by downstream AST processors, especially code generators. Downstream processors may
  require additional entity kind values.

## Occurs In
* [Contexts]({{< relref "context.md" >}})

## Contains

* [Authors]({{< relref "author.md" >}}) - define who the authors of the
  entity are
* [Function]({{< relref "function.md" >}}) - named definitions of processing
* [Handler]({{< relref "handler.md" >}}) - how to handle messages sent to an
  entity
* [Includes]({{< relref "include.md" >}}) - inclusion of entity content from a 
  file
* [Invariants]({{< relref "invariant.md" >}}) - logical expressions that must
  always hold true
* [Options]({{< relref "option.md" >}}) - define translation options for the
  entity
* [State]({{< relref "state.md" >}}) - the data an entity holds
* [Types]({{< relref "type.md" >}}) - the definition of a type of information
* [Terms]({{< relref "term.md" >}}) - the definition of a term related to 
  the entity
