# Hierarchy of Definitions

Riddl utilizes a hierarchy of nested definitions. This hierarchy defines the 
basic structure of any Riddl specification. The list below also serves as a
 handy way to navigate the kinds of definitions Riddl supports

# The hierarchy of containers and leaves
Here is how RIDDL nesting can be structured:
* Root (top level)
  * [Domain](domains.md) - the root definition of any riddl document
    * [Domain](domains.md) - domains can be nested to form sub-domains
    * [Type](types.md) 
    * [Topic](topics.md)
      * Command
      * Event
      * Query
      * Result
    * [Context](contexts.md)
      * [Type](types.md)
      * [Entity](entities.md)
        * State
        * Features
        * Consumers
        * Actions
        * Invariants
      * [Interaction](interactions.md)
      * [Adaptor](adaptors.md)
    * Interaction
      * Action
