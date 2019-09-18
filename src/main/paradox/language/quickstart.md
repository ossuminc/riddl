# Quick Start
Some people learn faster by example than by reading 
the documentation. For those of you like that, this is
your example. 

```idddl

# IDDDL is about defining domains so ..
domain example.domain {

  # Domains are composed of bounded contexts so ...
  context {
    # Contexts can contain many kinds of definitions
    
    # 8 pre-defined types, shown as re-names
    type str = String             # Define str as a String
    type num = Number             # Define num as a Number   
    type boo = Boolean            # Define boo as a Boolean
    type ident  = Id              # Define ident as an Id
    type dat = Date               # Define dat as a Date
    type tim = Time               # Define tim as a Time 
    type stamp = TimeStamp        # Define stamp as a TimeStamp
    type url = URL       
    
    # Enumerations have a value chosen from a list of identifiers
    type enum = any [ Apple Pear Peach Persimmon ]

    # Alternations select one type from a list of types
    type alt = select enum | stamp | url 

    # Aggregations combine several types and give each a field name identifier
    type agg = combine {
      key: Number,
      id: Id,
      time: TimeStamp
    }

    # Types can have cardinality requirements similar to regular expressions
    type oneOrMore = agg+
    type zeroOrMore = agg*
    type optional = agg?

    # Commands, Events, Queries and Results are defined in terms of some
    # type, previously defined. Commands yield events. Queries yield results.

    command DoThisThing: SomeType yields ThingWasDone
    event ThingWasDone: SomeType
    query FindThisThing: SomeType yields SomeResult
    result ThisQueryResult: SomeType

    # Entities are the main objects that contexts define. They can be
    # persistent (long lived) or aggregate (they consume commands and queries) 
    aggregate persistent entity Ribs: SomeType 
      consumes [ACommand, AQuery]
  }
}
``` 
