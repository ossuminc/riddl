---
title: "Definitional Hierarchy"
weight: 20
---

Riddl strictly utilizes a hierarchy of nested definitions. This hierarchy 
defines the basic structure of any Riddl specification. The list below shows 
this hierarchical model and also serves as a handy way to navigate to the 
kinds of definitions that Riddl supports.

- [Root](../root) 
    - [Include](../includes)
    - [Domain](../domain)
        - [Domain](../domain) - yes, recursively
        - [Context](../domain/context)
            - [Adaptor](../domain/context/adaptor)
            - [Entity](../domain/context/entity)
                - [Action](../domain/context/entity/action)
                - [Handler](../domain/context/entity/handler)
                - [Function](../domain/context/entity/function)
                - [Feature](../domain/context/entity/feature)
                - [Invariant](../domain/context/entity/invariant)
                - [Producer](../domain/context/entity/producer)
                - [State](../domain/context/entity/state)
                - [Type](../type)
            - [Projection](../domain/context/projection)
            - [Type](../types)
        - [Topic](../domain/topic)
            - [Event](../domain/topic/event)
            - [Command](../domain/topic/command)
            - [Query](../domain/topic/query)
            - [Result](../domain/topic/result)
        - [Type](../types)
        - [Interaction](../interactions.md)
