---
title: "State"
type: page
weight: 10
---

The state definitions of an entity define the structure of the information that the entity 
retains as its current state.  

# Syntax
The state of an entity is defined with the `state` keyword within the content of an `entity` 
definition, like this:
```riddl
entity Car {
  type Propulsion = any of { ICE, Electric, Steam, Diesel, EMDrive }
  state Basics is {
    wheels: Integer
    doors: Integer
    rightHandDrive: Boolean
    propulsion: Propulsion
  }
}
```
