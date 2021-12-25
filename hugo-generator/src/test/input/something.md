---
title: "Something"
draft: false
---

#### _Entity_

## Description
Entities are the main objects that contexts define. They can be 
persistent (long-lived) or aggregate (they consume commands and queries).

## Options
The following flags/options were specified for this entity:

  * _aggregate_ - description of what aggregate means
  * _persistent_ - description of what persistent means

## Types
The following types are defined within this entity:

  * [somethingDate](types/somethingDate) (_alias_)

## States
The following states were defined within this entity:

#### someState
##### Fields:

  * **field** (_type:_ [**SomeType**](/everything/types/sometype))

##### Invariants:

  * (none)

## Functions
The following functions are defined for this entity:

  * **whenUnderTheInfluence**
    * _Description:_ (none)
    * _requires:_ **Nothing**
    * _yields:_ **Boolean**

## Handlers
The following command handlers are defined for this entity:

  * **foo**
    * _Description:_ (none)
    * _command:_ **Something**
    * _yields:_ **Nothing**

The following event handlers are defined for this entity:

  * (none)
