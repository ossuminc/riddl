---
title: "State"
type: "page"
draft: "false"
weight: 10
---

The state definitions of an entity define the structure of the information that the entity 
retains as its current state.  

## Syntax Example
The state of an entity is defined with the `state` keyword within the content of an `entity` 
definition, like this:
```riddl
entity Car {
  type Propulsion = any of { ICE, Electric, Steam, Diesel, EMDrive }
  state Static is {
    wheels: Integer
    doors: Integer
    rightHandDrive: Boolean
    propulsion: Propulsion
  }
}
```
One of the primary purposes of an entity is to represent the characteristics
of the entity with state information. We call it _state_ because it
represents the current state of the entity at any given point in time. 

The above example definition associates a `state`, named `Static` with the 
`entity` named `Car` having these fields in its aggregate data type:
* `wheels` - an Integer value providing the number of wheels on the car.
* `doors` - an Integer value providing the number of doors on the car.
* `rightHandDrive` - a Boolean value indicating if it is right hand drive.
* `propulsino` - a `Propulsion` enum value presumably representing the kind 
  of propulsion the vehicle uses.

## Multiple State Definitions
It is entirely possible to specify multiple named state definitions for a
single entity. This is provided in the language to support finite state
machines which are frequently used with entities. The state of an entity can
be modelled as simply transitions of states as each [handler](ref(handler.md))
processes a command. Multiple state values are useful when the state of the
entity has different modes of operation where different sets of state values
are needed.

