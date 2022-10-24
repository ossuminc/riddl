---
title: "What is RIDDL Based On?"
date: 2022-02-24T14:15:51-07:00
draft: false
weight: 30
---

The RIDDL specification language borrows concepts from:
* [Domain Driven Design (DDD)](https://en.wikipedia.org/wiki/Domain-driven_design)
* [Reactive System Architecture (RSA)](https://www.reactivemanifesto.org/)
* [Unified Modeling Language (UML)](https://en.wikipedia.org/wiki/Unified_Modeling_Language) 
* [Agile User Stories](https://en.wikipedia.org/wiki/User_story)
* [Behavior Driven Development (BDD)](https://en.wikipedia.org/wiki/Behavior-driven_development)
* [C4 Model Of Software Architecture](https://c4model.com)

RIDDL aims to capture business concepts and architectural details in a way that
is consumable by business professionals yet can also be directly
translated into various technical and non-technical artifacts, including: 
* a documentation web-site 
* various architectural diagrams (context maps, sequence diagrams, and so on)
* design input to code generators (e.g. Kalix, protobuffers)
* Kubernetes deployment descriptors
* code scaffolding that implements the design captured in the RIDDL specification 
* and more; please see the 
  [future projects section]({{< relref "../future-work" >}})

Using these outputs, delivery teams are well-equipped to quickly begin
the task of implementation. Regeneration of the model in subsequent 
iterations of the design are accommodated and continue to provide value through
the evolution of the design without interrupting the implementation.

RIDDL gets its name from an acronym that stands for "Reactive Interface to Domain
Definition Language". 

## Domain Driven Design (DDD)
RIDDL is based on concepts from 
[DDD](https://en.wikipedia.org/wiki/Domain-driven_design). This allows domain 
experts and technical teams to work at a higher level of abstraction by 
co-creating a ubiquitous language for the target domain enabling them to 
develop a system specification that is familiar and comprehensible by business 
and technical leaders alike.

For best comprehension of the RIDDL language, it is best to be familiar with
DDD concepts. For a four-minute overview 
[watch this video](https://elearn.domainlanguage.com/). 
For a more in depth understanding we recommend reading Vaughn Vernon's more 
concise book
[Domain Driven Design Distilled](https://www.amazon.com/Domain-Driven-Design-Distilled-Vaughn-Vernon/dp/0134434420/),
or Eric Evans' original tome [Domain Driven Design: Tackling Complexity in the Heart of Software](https://www.amazon.com/Domain-Driven-Design-Tackling-Complexity-Software/dp/0321125215/)

## Reactive System Architecture (RSA)
The [Reactive Manifesto](https://www.reactivemanifesto.org/) was authored in
2014 by Jonas Bonér, David Farley, Roland Kunh, and Martin Thompson. As the
computing landscape evolved and companies began to operate at "internet scale"
it became evident that the old ways of constructing systems were not adequate.
We needed an approach to system architecture back then that was fundamentally
different in order to meet user expectations.

The central thesis of reactive architectures is that the overriding objective
in any system must be responsiveness. Users are conditioned to expect systems
that perform well and are generally available. If these conditions are not met,
users tend to go elsewhere to get what they want. That, of course, is clearly
unacceptable for any business endeavor. To maintain responsive to users, a 
system must deal with various responsiveness challenges:
* system or component failure (resiliency)
* increasing work load (scalability)
* high operational cost (efficiency)
* slow responses (performance)
A reactive system aims to be responsive in the face of all of these challenges.  

Without going into too much detail here, among the key means of achieving 
responsiveness is to decompose the concerns of a domain into well isolated 
blocks of functionality (DDD), and then, establishing clear non-blocking, 
asynchronous, message-driven interfaces between them. Together, the concepts 
of, Responsiveness, Elasticity, Resiliency, and Message-Driven form the basis
of a Reactive Architecture.

To get more information on Reactive Architecture please refer to the excellent
6 part course by Lightbend. You can find the first course in that series
[here](https://academy.lightbend.com/courses/course-v1:lightbend+LRA-IntroToReactive+v1/about).

{{< figure src="images/ReactiveArchitectureOverview.svg" >}}

## Unified Modeling Language (UML)
One of the key insights brought forward by UML is that it is far easier for 
humans to comprehend the intended design of a system by communicating these 
ideas with pictures. UML is a language of very precise graphical symbols that
communicate different concerns of a system design.

This idea has been further leveraged by other design artifacts and activities.
For example, a very common DDD exercise is called Event Storming. In this 
exercise a group of experts brainstorms about the things that happen, concrete
events, within a system. Event Storming uses a very low fidelity tool to capture 
the details of the exercise. In this exercise, traditionally events are captured
first on orange sticky notes. The commands that generate those notes are then
captured on blue stickies. And the actors that initiate those commands are
captured on yellow stickies. And so on. More information on event storming can
be found [here](https://en.wikipedia.org/wiki/Event_storming) and 
[here](https://www.lucidchart.com/blog/ddd-event-storming)

RIDDL uses many of the design artifacts detailed under the UML specification 
to help communicate the design and intent of a system. For example, Sequence
Diagrams are used extensively to document the interactions between bounded
contexts in a system. A State Machine Diagram may be used to document the 
lifecycle of an entity or actor in a system, and so on. However, RIDDL's
diagram output is not limited to UML diagrams. Peter Chen's 1971 invention of 
the entity relationship diagram is very well adapted to the concept of entity
in DDD. DDD also has its own diagrams:
* the System Context Diagram provides a depiction of the actors, internal 
  systems, external systems, and how they interact (use cases).
* the Context Map is a high level diagram that portrays the general 
  relationships between bounded contexts.
* Business Use Case Diagram - same idea as the UML version but simpler

More on that can be found [here](https://medium.com/nick-tune-tech-strategy-blog/domain-driven-architecture-diagrams-139a75acb578)

## Agile User Stories
Agile user stories are used to capture the requirements of various components
within a system. In RIDDL we capture these user stories as a Feature. 

As a [persona], I [want to], [so that]...

In other words, it provides WHO (persona), WHAT (want to), and WHY (so that). 


## Behavior Driven Design (BDD)

<blockquote>
Behavior-driven development was pioneered by Daniel Terhorst-North. It grew 
from a response to test-driven development (TDD), as a way to help programmers
on new agile teams “get straight to the good stuff” of knowing how to approach
testing and coding, and minimize misunderstandings. BDD has evolved into both
analysis and automated testing at the acceptance level.
<a href="https://cucumber.io/docs/bdd/history/">Cucumber Documentation</a></a>
</blockquote>

BDD provides a simple specification language named Gherkin which is used heavily
in RIDDL. Even if you are not familiar with the Gherkin language, it is simple 
enough and intuitive enough to be grasped quickly. 

Gherkin scenarios follow a simple structural pattern, like this:
* **SCENARIO**: *\<scenario description\>*
  * **GIVEN** *\<a precondition\>*
  * **WHEN** *\<an event occurs\>*
  * **THEN** *\<take an action\>*

RIDDL uses this structure to define the processing that should occur when 
defining how an event should be handled, when a function is invoked.
Most of it is free-form natural language. Consequently, RIDDL is not a precise
programming language. 

 
agile circles or considered BDD as a tool for testing, you have likely
encountered Gherkin language. It follows the familiar, Given - When - Then
format of capturing a user story. 