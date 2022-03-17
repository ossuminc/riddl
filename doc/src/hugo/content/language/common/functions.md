---
title: "Functions"
type: "page"
weight: 50
draft: "false"
---

## Introduction
Functions are pieces of processing that can be attached to `entity`, `consumer`, 
and `api` definitions.  A function has a set of inputs (its requirements) and a 
set of output (what it yields). Functions need not be pure and often 
have side effects through the state changes of an entity.   

## Example
Here's an example of a function, named `riddle`, that requires a 
`Subject(String)` type and returns a `Riddle` (String) type. Presumably, 
it generates a riddle in any subject. 
```riddl
type Subject = String
type Riddle = String
function riddle is { requires {s: Subject}  yields { r: Riddle } }  
```
## Applicability
Functions have applicability across several RIDDL definitions:
* [Context APIs](../hierarchy/domain/context/api) - APIs are collections of 
  functions
* [Entity Handler](../hierarchy/domain/context/entity/handler) - As 
  utilities 
  in an entity 
  [handler](Encapsulate repeating logic from a 
  consumer's `on` 
  clause)
* Allow internal entity processing to be specified as part of an interaction
* etc.

Further Content: TBD

