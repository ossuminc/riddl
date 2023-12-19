---
title: "Functions"
type: "page"
weight: 50
draft: "false"
---

## Introduction
Functions are pieces of processing that can be defined in various places as a
shorthand for often repeated processing steps. A function has a set of inputs
(its requirements) and a set of output (what it yields). Functions need not be
pure and often have side effects through the state changes of an entity.   

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
* [Context APIs]({{< relref "../root/domain/context" >}}) - Contexts can 
  define functions for use within their API `handler`. 
* [Handler]({{< relref "../root/domain/context/entity/handler" >}}) - As 
  utilities in a 
  [handler]({{< relref "../root/domain/context/entity/handler" >}})
  to encapsulate repeating logic from an `on` clause
* Allow internal entity processing to be specified as part of an interaction
* etc.

{{< hint type=warning title="Warning" >}}
Further Content: TBD
{{< /hint >}}

