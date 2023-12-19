---
title: "Design"
description: "Using RIDDL To Design A Large System"
date: 2023-12-14T10:50:32-07:00
draft: "false"
weight: 20
---

This section helps authors with the task of using RIDDL to design the user
interface for a system. RIDDL provides two kinds of definitions for this:
* _Epic_, and
* _Application_

Both _Epics_ and _Applications_ are composed of more granular things but
at this point it is important to understand how they are combined to
define the user interface.  Epics allow you to specify the interaction
between the user (human or otherwise) of the system, and the system
itself. Applications represent the physical or virtual machinery that
the user can manipulate to achieve some objective with the system.

{{< mermaid class="text-center" >}}
graph LR;
  Disp(Message Dispatcher);
  Box([Actor Mailbox]);
  subgraph Actor
    Code[[Actor Message<br/> Processing Code]];
    State[(Actor's Private<br/>Data)];
  end
  Other[[Other Actor]];
  Disp-->Box;
  Box-->Code;
  Code--changes-->State;
  Code--send message to-->Other;
  Code--creates more-->Other;
  Code--changes behavior of<br/>next message processing-->Code;
{{< /mermaid >}}


In this section we first describe 


support
the definition of a user interface in RIDDL:
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
* _Application_ - An application is the way a user communicates with 
  the system. It uses a collection of components to do that by 
  presenting information and allowing the user to put in other
  information and control what happens to it. RIDDL models an 
  application as a hierarchy of just four kinds of components. The idea
  of an application is
* Its sole purpose is to  display information (output), 
  receive information (control) and take commands to do things (action). 
  Use cases are written in terms of those three 
* A specification of the application components that the
  user can manipulate. There are only three kinds of such components: 
  input, output, and group. 

In this section 

{{< toc-tree >}}
