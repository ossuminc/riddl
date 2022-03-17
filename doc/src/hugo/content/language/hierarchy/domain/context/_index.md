---
title: "Bounded Contexts"
type: "page"
weight: 10 
draft: "false"
---
## Introduction
DDD defines the notion of a **bounded context** which is a portion of the domain
in which the terminology is well-defined. Contexts in RIDDL represent exactly
the same concept and are basically a container of various definitions that make up
the bounded context.  Contexts are introduced with the `context` keyword and have a 
name. They form the leaves of the definitional hierarchy so they must occur within 
domain definitions.  

## Syntax
```riddl
context BBQ is { ??? }
```
## Contained Definitions
Contexts may contain all the definitions of a DDD bounded context, as defined in the 
sections that follow.

### Type
Types are used in bounded contexts to define messages, function input and output, 
the state of entities, etc. For more on type definitions see [types](../../../common/types)

### Adaptor

### API

### Entity

### Saga

### Projection

