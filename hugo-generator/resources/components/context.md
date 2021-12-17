---
title: "{{ replace .Name "-" " " | title }}"
date: {{ .Date }}
draft: false
---

# {{contextName}}
#### _Bounded Context_

## Description
{{contextDescription}}

## Types
The following types are defined within this bounded-context:

{{contextTypes}}
* [Type1](types/type1) (_enumeration_)
* [Type2](types/type2) (_alias_)
* [Type3](types/type3) (_identifier_)
* [Type4](types/type4) (_record_)

## Entities
The following entities are defined within this bounded-context:

{{contextEntities}}
* [Entity1](entities/entity1) - This is a really long description to see how rendering a full context description will look at the domain level. 
* [Entity2](entities/entity2) - This section contains all the bounded contexts in this domain.