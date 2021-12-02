---
title: "Language"
draft: true
---

# Overview
This section defines the RIDDL syntax.  RIDDL is aimed not at software
developers but business analysts, domain experts, and domain engineers. It
tries hard to be readable in English and not overly technical while still
retaining the ability to be precise, concise and specific. The principles
of Domain Driven Design are upheld while some of the details have been 
changed or adapted specifically for implementation purposes (since code must
be generated from the RIDDL definitions). Thus, RIDDL is opinionated and 
specific. It does not attempt to solve every kind of computing problem 
nor even every kind of knowledge domain.  

# Language Consistency
Some things in RIDDL are consistent throughout the language. We believe this makes
learning the language easier since there are no exceptions to fundamental constructs.
The sections below define the consistent language features. 

## Everything Is A Definition
The language is declarative. You don't say how to do something, you specify the end
result you want to see. The language aims to capture a detailed and concise definition
of the abstractions a complex system will require. It does not specify how those
abstractions should be built. That is for an engineer to implement.

## Every Definition Can Be Documented
Every thing you can define can have a `described by` suffix which lets you document
the definition using markdown.

## Definitional Hierarchy

Riddl strictly utilizes a hierarchy of nested definitions. This hierarchy defines the
basic structure of any Riddl specification. The list below shows this hieararchical model and
also serves as a handy way to navigate the kinds of definitions Riddl supports.

Here is how RIDDL nesting can be structured:

* [Root](root.md)

  * [Include](includes.md)

** [Domain](domains.md)

*** [Include](includes.md)

*** [Domain](domains.md) (recursive!)

*** [Context](domain/contexts.md

**** [Adaptor](domain/context/adaptors.md)
**** [Entity](domain/context/entities.md)
***** [Action](domain/context/entity/actions.md)
***** [Consumer](domain/context/entity/consumers.md)
***** [Feature](domain/context/entity/features.md)
***** [Function](domain/context/entity/functions.md)
***** [Invariant](domain/context/entity/invariants.md)
***** [State](domain/context/entity/state.md)
***** [Type](types.md)
**** [Projections](domain/context/projections.md)
*** [Type](types.md)
*** [Topic](domain/topics.md)
**** [Event](domain/topic/events.md)
**** [Command](domain/topic/commands.md)
**** [Query](domain/topic/queries.md)
**** [Results](domain/topic/results.md)
**** [Type](types.md)
*** [Type](types.md)
* [Interaction](interactions.md) (looking for a home!)
