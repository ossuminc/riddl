---
title: "{{ replace .Name "-" " " | title }}"
date: {{ .Date }}
draft: false
---

# {{domainName}}
#### _Domain_

## Description
{{contextDescription}}

## Types
The following types are defined across bounded-contexts within this domain:
{{domainTypes}}
* [Type1](types/type1) (_enumeration_)
* [Type2](types/type2) (_alias_)
* [Type3](types/type3) (_identifier_)
* [Type4](types/type4) (_record_)

## Bounded Contexts
The following bounded-contexts are defined within this domain:
{{domainBoundedContexts}}
* [Context1](contexts/context1) - This is a really long description to see how rendering a full context description will look at the domain level. 
* [Context2](contexts/context2) - This section contains all the bounded contexts in this domain.