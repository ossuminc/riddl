---
title: "States"
draft: false
---

A State is the storage of an [entity](entity). It is defined as a set of 
[fields](field) with a [handler](handler) that defines how messages cause 
changes to the value of those fields. 

An [entity](entity) can have multiple state definitions with the implication 
that this entity would be considered a 
[Finite State Machine](https://en.wikipedia.org/wiki/Finite-state_machine). 
However, it would only be such if the entity used the 
[finite state machine option]({{< relref "entity.md#finite-state-machine" >}})

## Occurs In
* [Entities]({{< relref "entity.md" >}})

## Contains
* [Fields]({{< relref "field.md" >}})
