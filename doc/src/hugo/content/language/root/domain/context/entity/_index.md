---
title: "Entities"
type: "page"
weight: 10
draft: "false"
---


## Example 

Here is an outline of the definition of an entity

```riddl
entity Example is {
  options(...))          // Optional aspects of the entity and code gen hints
  invariant i is { ... } // Logical assertions that must always be true 
  state s is { ... }     // Information retained by the entity
  function f is { ... }  // Functions to eliminate redundancy in handlers 
  handler h is { ... }   // How the entity handles messages sent to it
}
```

## Details
The links below provide more details on the various sub-definitions of an entity:

{{< toc-tree >}}
