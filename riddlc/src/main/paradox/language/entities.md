# Entities

## Introduction
An entity is the fundamental processor of work in a reactive system. They are
often implemented in software using the actor model.  
Entities are consumers and producers of messages (commands, events, queries, 
and results). They also hold state, whether persistent or not. Entities use 
event sourcing to keep track of the entire history of changes to the entity's
state.  Entities have a globally unique immutable persistent identifier, 
type `Id`, which provides the means to reference the entity in its context or
between contexts.  

@@@ index

