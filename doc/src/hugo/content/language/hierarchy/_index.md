---
title: "Definitional Hierarchy"
type: "page"
draft: "false"
weight: 20
---

RIDDL utilizes a hierarchy of nested definitions. This hierarchy 
defines the basic structure of any Riddl specification. The list below shows 
this hierarchical model and also serves as a handy way to navigate the various 
kinds of definitions that Riddl supports.

- [Root](./root) 
    - [Include](../common/includes)
    - [Domain](./domain)
        - [Domain](./domain) - yes, recursively
        - [Context](./domain/context)
            - [Adaptor](./domain/context/adaptor)
            - [API](./domain/context/api)
            - [Entity](./domain/context/entity)
                - [Action](./domain/context/entity/action)
                - [Function](../common/functions)
                - [Feature](./domain/context/entity/features)
                - [Handler](./domain/context/entity/handler)
                - [Invariant](./domain/context/entity/invariants)
                - [Producer](./domain/context/entity/producer)
                - [State](./domain/context/entity/state)
                - [Type](../common/types)
            - [Projection](./domain/context/projections)
            - [Saga](./domain/context/saga)
            - [Type](../common/types)
        - [Topic](./domain/topic)
            - [Event](./domain/topic/event)
            - [Command](./domain/topic/command)
            - [Query](./domain/topic/query)
            - [Result](./domain/topic/result)
        - [Type](../common/types)
        - [Interaction](../common/interactions.md)

## Path Identifiers
In several places in RIDDL, you may need to reference a definition in 
another definition. Such references are called Path Identifiers. They work a 
lot like a Unix file system with files (leaves) and directories (branches).

Please consider the following example as you read the sections below

```riddl
domain A {
  domain B {
    context C {
      type Simple = String(,30)
    }
    type BSimple = A.B.C.Simple // full path  
    context D {
      type DSimple = .E.ESimple // partial path
      entity E {
        type ESimple = ...C.Simple // partial path
      }
    }
  }
}
```

### Path Identifier Syntax
Path identifiers are composed of only the names of the definitions and the 
period character like the one at the end of this sentence -->.  Separating 
the names by periods allows us to distinguish the names of the enclosing 
definitions that contain the definition of interest. 
#### Full Path Identifier
A full path starts from the root of the hierarchy and mentions each 
definition name until the sought leaf definition is reached.  Here's the full 
path identifier to the `Simple` type definition in the example above: 
`A.B.C.Simple` which is also used in the definition of the `BSimple` type

#### Partial Path Identifiers
Path identifiers can be partial too. All partial path identifiers start with 
a period. A single period indicates the current container definition in the 
hierarchy, two periods indicates the container's container, three periods 
indicates the container's container's container, and etc. 

In the example, the definitions of both `DSimple` and `ESimple` use partial 
paths to name the type.  For `Dsimple` the partial path, `.E.ESimple` is broken 
down like this:
* `.` - start with the current container (`D`)
* `E` - go to the `E` container in `D`
* `.` - current container (`E`)
* `ESimple` - select the type named `ESimple`
For the `ESimple` example, the path is broken down like this:
* `.` - start with the current container(`E`)
* `.` - go to its parent container (`D`)
* `.` - go to its parent container (`B`)
* `C` - go to its child container (`C`)
* `.` - current container `C`
* `Simple` - select the type named `Simple`
