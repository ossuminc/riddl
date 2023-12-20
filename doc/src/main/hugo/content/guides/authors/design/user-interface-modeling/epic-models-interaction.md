---
title: "Epics Model Interactions"
description: "How to use a RIDDL Epic to model user/system interactions"
date: 2023-12-19T10:50:32-07:00
draft: "false"
weight: 30
---

A RIDDL _Epic_ is a definition that helps to model the interactivity
between the user, the application and the rest of the system. 

* _Epic_ - A specification of the related set of use cases that cohesively
  define a feature of the system. The system will likely be composed of
  many epics. The use cases decompose the epic to handle the variety of
  conditions that may occur for the intended feature.
* _Use Case_ - The specification of a single flow of interactions that
  occur between a user (role) and the system components. Use Cases start
  (probably) with a user taking an action on an application. Then further
  interaction between the system components show how the system responds
  to that user's action.
* _User Story_ - A simple summary of a _Use Case_ that quickly tells who,
  what and why is involved in the _Use Case_. Stories use the familiar
  pattern: _{who}_ wants to {what} so that {why}. For example:  
  The quick brown fox (who) **wants to** jump over the lazy dog (what)
  **so that** he can get through the garden without being eaten (why).
* _Interaction_ - One step of a _Use Case_ involving a user or system
  component interacting with another user or component of the system.
  Interactions are the building blocks of a Use Case 
