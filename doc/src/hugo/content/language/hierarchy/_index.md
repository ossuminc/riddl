---
title: "Definitional Hierarchy"
weight: 20
---

Riddl strictly utilizes a hierarchy of nested definitions. This hierarchy 
defines the basic structure of any Riddl specification. The list below shows 
this hierarchical model and also serves as a handy way to navigate to the 
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
                - [Handler](./domain/context/entity/handler)
                - [Function](../common/functions)
                - [Feature](./domain/context/entity/feature)
                - [Invariant](./domain/context/entity/invariant)
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
