---
title: "Domain"
draft: false
---

A domain is the top-most definitional level in RIDDL. We use the word 
*domain* in the sense of a *knowledge domain*; like an entire business, a 
field of study, or some portion of these. It has nothing to do with Internet 
domain names. A domain is an arbitrary boundary around some subset of concepts
in the universe. As with Domain Driven Design, RIDDL uses the concept of a
domain to group together a set of related concepts.

Domains in RIDDL are inert. That is they only serve to contain definitions 
that do things, but they don't do things themselves.

Domains can recursively contain other nested domains so that a hierarchy of 
domains and subdomains is established.  Because of this, we can organize any
large, complex knowledge domain or field of study, into an
[hierarchical ontology](https://en.wikipedia.org/wiki/Ontology#Flat_vs_polycategorical_vs_hierarchical).

For example, if you were modelling the domain of *Two Wheeled Vehicles* you
might devise a domain hierarchy like this:
* Two Wheeled Vehicle
    - Motorized
      - ICE powered motorcycles
      - Scooter
      - Electric Bicycles
      - Segway 
  * UnMotorized
    * Bicycles
    * Oxcart
    * Handtruck
    * Human With Training Wheels

## Occurs In

* [Root]({{< relref "root.md" >}})
* [Domains]({{< relref "domain.md" >}}) {{< icon "rotate-left" >}} - domains
  can be nested in a super-domain.

## Contains

Within a domain, you can define these things:

* [Actors]({{< relref "user.md" >}}) - someone or thing that uses the domain
* [Applications]({{< relref application.md >}}) - an user interface  
* [Authors]({{< relref "author.md" >}}) - who defined the domain
* [Contexts]({{< relref "context.md" >}}) - a precisely defined bounded context within the domain
* [Domains]({{< relref "domain.md" >}}) {{< icon "rotate-left" >}} - domains 
  can have nested domains (subdomains)
* [Epics]({{< relref "epic.md" >}}) - a story about external entities
  interacting with the domain
* [Includes]({{< relref "include.md" >}}) - inclusion of entity content from a
  file
* [Options]({{< relref "option.md" >}}) - optional declarations about a 
  definition
* [Terms]({{< relref "term.md" >}}) - definition of a term relevant to the
  domain
* [Types]({{< relref "type.md" >}}) - information definitions used throughout
  the domain
