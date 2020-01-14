# Entities

## Definition 
An entity is the fundamental processor of work in a reactive system. They are
often implemented in software using the actor model or a class in OOP design.  

Entities are consumers and producers of messages (commands, events, queries, 
and results). They also hold state, whether persistent or not. Entities use 
event sourcing to keep track of the entire history of changes to the entity's
state.  Entities have a globally unique immutable persistent identifier, 
type `Id`, which provides the means to reference the entity in its context or
between contexts.  For example, If two instances of the same object have different attribute values, but same identity value, 
Thus, entities are the single source of truth for a particular id. 
they are the same entity.

![Entities](images/entities.png "Entities")

Entities can also contain business Logic. Actors in Akka, Entities in Lagom. 
Contrary to Value Objects, an Entity's immutable identity conveys equivalence.
Individual pieces of attribute of entity can change.


 