# Hierarchy of Definitions

Riddl utilizes a hierarchy of nested definitions. This hierarchy defines the 
basic structure of any Riddl specification. The list below also serves as a
 handy way to navigate the kinds of definitions Riddl supports

# The hierarchy of containers and leaves
Here is how RIDDL nesting can be structured:

* Root (top level)
  * @ref:[Domain](domains.md) - the root definition of any riddl document
    * @ref:[Domain](domains.md) - domains can be nested to form sub-domains
    * @ref:[Type](types.md) 
    * @ref:[Topic](topics.md)
      * @ref:[Command](commands.md)
      * @ref:[Event](events.md)
      * @ref:[Query](queries.md)
    * @ref:[Context](contexts.md)
      * @ref:[Type](types.md)
      * @ref:[Entity](entities.md)
        * [State](state.md)
        * @ref:[Function](functions.md)
        * @ref:[Feature](features.md)
        * @ref:[Consumer](consumers.md)
        * @ref:[Action](actions.md)
        * @ref:[Invariant](invariants.md)
      * @ref:[Interaction](interactions.md)
      * @ref:[Adaptor](adaptors.md)
    * @ref:[Interaction](interactions.md)
