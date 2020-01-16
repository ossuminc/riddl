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
    * @ref:[Topic](topics.md) - topics are defined in domains
      * @ref:[Command](commands.md) - commands are defined in topics 
      * @ref:[Event](events.md) - events are defined in topics
      * @ref:[Query](queries.md) - queries are defined in topics
      * @ref:[Result](results.md) - results are defined in topics
    * @ref:[Context](contexts.md) - contexts are defined in domains
      * @ref:[Type](types.md) - types may be defined in contexts
      * @ref:[Entity](entities.md) - entities are defined in contexts
        * @ref:[State](state.md) - state is part of an entity 
        * @ref:[Function](functions.md) - functions are part of an entity
        * @ref:[Feature](features.md) - features are part of an entity
        * @ref:[Consumer](consumers.md) - consumers are part of an entity
        * @ref:[Action](actions.md) - actions are part of an entity
        * @ref:[Invariant](invariants.md) - invariants are part of an entity
      * @ref:[Interaction](interactions.md) - interactions are part of contexts
      * @ref:[Adaptor](adaptors.md) - adaptors are part of contexts
    * @ref:[Interaction](interactions.md) - interactions may be defined in domains

@@@ index

* [Action](actions.md)
* [Adaptor](adaptors.md)
* [Command](commands.md)
* [Consumer](consumers.md)
* [Context](contexts.md)
* [Domain](domains.md) 
* [Entity](entities.md)
* [Event](events.md)
* [Feature](features.md)
* [Function](functions.md)
* [Include](includes.md)
* [Invariant](invariants.md)
* [Interaction](interactions.md)
* [Messages](messages.md)
* [Projections](projections.md)
* [Query](queries.md)
* [Results](results.md)
* [State](state.md)
* [Type](types.md) 
* [Topic](topics.md)

@@@
