---
title: "Root"
draft: false
---

The *root* concept refers to the container of domains. Domains are the top 
level definition in RIDDL but because RIDDL is hierarchical, and you can have
more than one domain definition at the top level, something has to contain those
top level domains. We call that the *root*. A root is not a definition since it
has no name. You can think of a *root* as the file in which a domain is defined.

## Occurs In
At the file root of the first file `riddlc` reads. 

## Contains
* [Domains]({{< relref "domain.md" >}})
* [Includes]({{< relref "include.md" >}})
