# Hierarchy of Definitions

Riddl utilizes a hierarchy of nested definitions. This hierarchy defines the 
basic structure of any Riddl specification. The list below also serves as a
 handy way to navigate the kinds of definitions Riddl supports

# The hierarchy of containers and leaves
Here is how RIDDL nesting can be structured:

Root Container - top level

  * @ref:[Domain](domains.md) - the root definition of any riddl document
    * @ref:[Domain](domains.md) - domains can be nested to form sub-domains
    * @ref:[Type](types.md) - types may be defined in domains
    * @ref:[Topic](domain/topics.md) - topics are defined in domains
      * @ref:[Command](domain/topic/commands.md) - commands are defined in topics 
      * @ref:[Event](domain/topic/events.md) - events are defined in topics
      * @ref:[Query](domain/topic/queries.md) - queries are defined in topics
      * @ref:[Result](domain/topic/results.md) - results are defined in topics
    * @ref:[Context](domain/contexts.md) - contexts are defined in domains
      * @ref:[Type](types.md) - types may be defined in contexts
      * @ref:[Entity](domain/context/entities.md) - entities are defined in
       contexts
        * @ref:[Action](domain/context/entity/actions.md) - actions are part of an entity
        * @ref:[State](domain/context/entity/state.md) - state is part of an entity 
        * @ref:[Function](domain/context/entity/functions.md) - functions are part of an entity
        * @ref:[Feature](domain/context/entity/features.md) - features are part of an entity
        * @ref:[Consumer](domain/context/entity/consumers.md) - consumers are part of an entity
        * @ref:[Invariant](domain/context/entity/invariants.md) - invariants are part of an entity
      * @ref:[Interaction](interactions.md) - interactions are part of contexts
      * @ref:[Adaptor](domain/context/adaptors.md) - adaptors are part of contexts
    * @ref:[Interaction](interactions.md) - interactions may be defined in domains

@@@ index

* [Action](domain/context/entity/actions.md)
* [Adaptor](domain/context/adaptors.md)
* [Command](domain/topic/commands.md)
* [Consumer](domain/context/entity/consumers.md)
* [Context](domain/contexts.md)
* [Domain](domains.md) 
* [Entity](domain/context/entities.md)
* [Event](domain/topic/events.md)
* [Feature](domain/context/entity/features.md)
* [Function](domain/context/entity/functions.md)
* [Include](includes.md)
* [Invariant](domain/context/entity/invariants.md)
* [Interaction](interactions.md)
* [Projections](domain/context/projections.md)
* [Query](domain/topic/queries.md)
* [Results](domain/topic/results.md)
* [State](domain/context/entity/state.md)
* [Type](types.md) 
* [Topic](domain/topics.md)

@@@
