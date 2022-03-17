---
title: "Domains"
type: "page"
weight: 40
draft: "false"
---

A domain is the top definitional level in RIDDL. Domains in RIDDL are
domains of knowledge, for example an entire business, or some portion of it.
The language uses the concept of a domain to group together a set of related
concepts. Within domains  you find `types`, `contexts`, `topics`, and
`interactions`.

Domains can declare themselves as the subdomain of another domain. For
example, a Car is a subdomain of the more general Automotive
domain. In RIDDL you would write it like so:
```riddl
domain Automotive {
  domain Cars { ??? }
  domain Trucks { ??? }
  domain Repairs { ??? }
}
```
This indicates that in the knowledge domain named Automotive there are three
subdomains of interest: Cars, Trucks and Repairs.
