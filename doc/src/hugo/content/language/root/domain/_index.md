---
title: "Domain"
type: "page"
weight: 40
draft: "false"
---

A domain is the top definitional level in RIDDL. We use the word *domain* in the
sense of a *knowledge domain*; like an entire business, or some portion of it. 
It has nothing to do with Internet domain names. A domain is an arbitrary 
boundary around some subset of concepts in the universe. As with Domain Driven 
Design, RIDDL uses the concept of a domain to group together a set of 
related concepts.

Unlike other definitions in RIDDL, domains can contain other domains. Because 
of this, we can organize any large, complex knowledge domain or field of study,
into a hierarchical ontology. For example, consider these nested domain 
definitions:
```riddl
domain Automotive {
  domain Cars { ??? }
  domain Trucks { ??? }
  domain Repairs { ??? }
}
```
In this example, the `Cars` domain is nested inside the more general 
`Automotive` domain. We would say that `Cars` is a *subdomain* of
`Automotive`.  Additionally, there are two other *subdomains* of `Automotive`:
`Trucks` and `Repairs`.

{{< toc-tree >}}