# Entities

Entities are consumers of commands and queries, and producers of events and
results. They are also the holders of state, whether persistent or not. 
Entities have a globally unique immutable persistent identifier, 
type `Id`, which provides the means to reference the entity in its context
or between contexts.  
