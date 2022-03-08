---
title: "What is RIDDL?"
date: 2022-02-24T14:15:51-07:00
draft: true
weight: 10
---

RIDDL is a specification language for (potentially large) distributed systems borrowing concepts from [Domain Driven Design (DDD)](https://en.wikipedia.org/wiki/Domain-driven_design), [Reactive System Architecture](https://www.reactivemanifesto.org/), [UML (Unified Modeling Language)](https://en.wikipedia.org/wiki/Unified_Modeling_Language), [agile user stories](https://en.wikipedia.org/wiki/User_story), [Behavior Driven Development (BDD)](https://en.wikipedia.org/wiki/Behavior-driven_development), and other widely adopted software development and design practices. It aims to capture business concepts and architectural details in a way that is consumable by business oriented professionals yet can be directly translated into varied technical and non-technical artifacts, including: 
* a documentation web site 
* various architectural diagrams (context maps, sequence diagrams, and so on) 
* code scaffolding that implments the design captured in the RIDDL specification 
* and many more

Using these outputs, delivery teams are well equipped to quickly get along with the task of implementation and  subsequent iterations on that design.

For the innately curious, RIDDL gets its name from an acronym that stands for "Reactive Interface to Domain Definition Language". Whether we retain the acronym or add an e on the end to be more english friendly is yet to be determined.

## Domain Driven Design (DDD)
RIDDL is based on concepts from [DDD](https://en.wikipedia.org/wiki/Domain-driven_design). This allows domain experts and technical teams to work at a higher level of abstraction by cocreating a ubiquitous language for the target domain enabling them to develop a system specification that is familiar and comprehensible by business and technical leaders alike.

For best comprehension of the RIDDL language, it is best to be familiar with DDD concepts. For a four-minute overview [watch this video](https://elearn.domainlanguage.com/). For a more in depth understanding we recommend reading Vaughn Vernon's more concise book **[Domain Driven Design Distilled](https://www.amazon.com/Domain-Driven-Design-Distilled-Vaughn-Vernon/dp/0134434420/)** or Eric Evans' original tome **[Domain Driven Design: Tackling Complexity in the Heart of Software](https://www.amazon.com/Domain-Driven-Design-Tackling-Complexity-Software/dp/0321125215/)**.

## Reactive Architecture
The [Reactive Manifesto](https://www.reactivemanifesto.org/) was authored in 2014 by Jonas Bonér, David Farley, Roland Kunh, and Martin Thompson. As the computing landscape evolved and companies began to operate at "internet scale" it became evident that the old ways of constructing systems were not adequate. We needed an approach to system architecture that was fundamentally different in order to meet user expectations.

The central thesis of reactive architectures is that the overriding objective in any system must be responsiveness. Users are conditioned to expect systems that perform well and are generally available. If these conditions are not met, users tend to go elsewhere to get what they want. That, of course, is clearly unacceptable. The two leading challenges to maintaining responsiveness for users are to remain responsive in the face of failure (resiliant) and responsive in the face of traffic demands (elastic).  

Without going into too much detail here, among the key means of acheiving responsiveness, resiliance, and elasticity is to decompose the concerns of a domain into well isolated blocks of functionality (DDD), and then, establishing clear non-blocking, asynchronous, message-driven interfaces between them. Together, the concepts of, Responsiveness, Elasticity, Resiliance, and Message-Driven form the basis of a Reactive Architecture.

To get more information on Reactive Architecture please refer to the excellent 6 part course by Lightbend. You can find the first course in that series [here](https://academy.lightbend.com/courses/course-v1:lightbend+LRA-IntroToReactive+v1/about).

{{< figure src="/ReactiveArchitectureOverview.svg" >}}

## UML
One of the key insights brought forward by UML is that it is far easier for humans to comprehend the intended design of a system by communicating these ideas with pictures. UML is a language of very precise graphical symbols that communicate different concerns of a system design.

This idea has been further leveraged by other design artifacts and activities. For example, a very common DDD exercise is called Event Storming. In this exercise a group of experts brainstorm about the things that happen, concrete events, within a system. Event Storming uses a very low fidelity tool to capture the details of the exercise. In this exercise, traditionally events are captured first on orange sticky notes. The commands that generate those notes are then captured on blue stickies. And the actors that initiate those commands are captured on yellow stickies. And so on. More information on event storming can be found [here](https://en.wikipedia.org/wiki/Event_storming).

RIDDL uses many of the design artifacts detailed under the UML specification to help communicate the design and intent of a system. For example, Sequence Diagrams are used extensively to document the interactions between bounded contexts in a system. A State Machine Diagram may be used to document the lifecycle of an entity or actor in a system, and so on.

## Agile User Stories and BDD
Agile user stories are used to capture the requirements of various components within a system. In RIDDL we capture these user stories as a Feature. The syntax is heavily influenced by the Gherkin Language. Even if you are not familiar with the name Gherkin language, if you have traveled in agile circles or considered BDD as a tool for testing, you have likely encountered Gherkin language. It follows the familiar, Given - When - Then format of capturing a user story. 

As a [persona], I [want to], [so that]...