---
title: "Context"
type: "page"
weight: 10 
draft: "false"
---


A `context` definition in RIDDL represents the notion of a 
[bounded context](../../../../guides/authors/design/context) in DDD.
Contexts are introduced with the `context` keyword and have a 
name, like this:
```riddl
context BBQ is { ??? }
```
IN the above the context is named `BBQ` and its definition is 3 question marks. 
`???` means "unknown" or "to be determined"

## Contained Definitions

### Common
* [Types](../../../common/types)

### Options
A `context` may define options. Options help the translation tools know what to
do with 

### Type
Types are used in bounded contexts to define messages, function input and output,
the state of entities, etc. For more on type definitions 
see [types](../../../common/types)


### Specific
Context definitions are containers and they may contain definitions that are
specific to being defined in a `context`:

{{< toc-tree >}}
