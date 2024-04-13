---
title: "Application Models Users' Tool"
description: "A User Interface Application allows users to interact with a system"
date: 2023-12-19T10:50:32-07:00
draft: "false"
weight: 40
---

This page describes the nature of a RIDDL Application definition. 

## What is a User?

The term
[user]({{< relref "../../../../../concepts/user.md">}}) in this 
context is a word of art. It doesn't necessarily mean a human
being. A user is, literally any thing that uses the system. 
This could be a piece of software, a human, an alien, or an 
artificial intelligence. We can define a user in RIDDL very
simply:
```riddl
user Gemini is "an artifical intelligence provided by Google"
```

## What Is A User Interface?
A user interface is just that: an interface to a system that is geared 
toward ease of use by the user. We regard the User Interface to be
a component of the system being modelled. 

## What Is An Application?
A RIDDL _Application_ defines a user interface for the system being
modeled in RIDDL.  Applications are the means by which a user may
communicate with a system, and control it.  In other words, it is 
the system component that adapts the system to the user. A system
may have multiple applications. 

Abstractly, the communication between a User and an Application requires only
four kinds of things:
* Ways to navigate the application's structure (navigate)
* Ways to control the system it represents (control)
* Ways to receive information from the user (input)
* Ways to show information to the user (output)

## 
User interfaces are made of many kinds of components and new ones are
being invented continuously. RIDDL Applications do not intend to model the
look, feel, shape, sensory utilization or any other design attribute of an
actual user interface. RIDDL Applications do not limit themselves to only
the current set of technology currently available as a user interfaces(
web sites, mobile applications, telephones, console panels). 

## RIDDL And The User Experience
RIDDL recognizes that the user experience (UX) is an art and science 
of its own, and divorces itself from that concern. This allows 
Applications to refer to rich user interface models that cover all
the senses of a human, without having to define all that complexity.
Such models are better defined with illustrations, demonstrations, 
wireframes, and etc. To support that separation well, RIDDL 
Applications use the `shown by <url>` syntax to link its components to 
such illustrations and demonstrations.

Consequently, RIDDL Applications concern themselves with the logical and 
functional details necessary to support the user's interaction with the system.
A RIDDL Application is simply the system facade component that permits its 
users to control the system the Application represents, as illustrated here:

{{< mermaid class="text-center" >}}
graph LR
    User( fas:fa-user-tie <br/> User)
    System( fas:fa-microchip <br/> System)
    Application( fas:fa-tablet-alt <br/> Application)
    
    User -- interacts with --> Application
    Application -- controls --> System
{{< /mermaid >}}

The control of both the system and the application is accomplished by sending
and processing messages. Consequently a RIDDL Application definition is merely
composed of inputs and outputs, and groups of those things, as shown here: 

{{< mermaid class="text-center" >}}
erDiagram
    APPLICATION ||--|{ GROUP : contains
    GROUP ||--|{ INPUT: contains
    INPUT ||--|{ INPUT: contains
    GROUP ||--|{ OUTPUT: contains
    OUTPUT ||--|{ OUTPUT: contains
{{< /mermaid >}}

The entity-relationship diagram shows the containment hierarchy for a RIDDL 
Application and its components. All the complexity of modern user interfaces
can be broken down into these few ideas. 

### Inputs
Inputs are manipulated by the user and consequently send messages to 
the application for processing:

{{< mermaid class="text-center" >}}
graph LR
  User( fas:fa-user-tie <br/> User)
  Input( far:fa-keyboard <br/> Input)
  Application( fas:fa-tablet-alt <br/> Application)

    User -- manipulates --> Input
    Input -- sends message to --> Application
{{< /mermaid >}}

### Outputs
Outputs receive messages from the application and present them to the user 
in a form suitable for that user's perception: 

{{< mermaid class="text-center" >}}
graph LR
  User( fas:fa-user-tie <br/> User)
  Output( fas:fa-tv <br/> Output)
  Application( fas:fa-tablet-alt <br/> Application)

  Application -- sends message to --> Output 
  Output -- presents information to --> User
{{< /mermaid >}}

### Navigation

Navigation of the user interface occurs when the user makes an input to the UI that
causes it to change the resource it is presenting. RIDDL Applications model this
by responding to a message in a handler that runs a statement that changes what 
is displayed to the user. This is shown in the following diagram:

{{< mermaid class="text-center" >}}
graph LR
    User( far:fa-user-tie User)
    Menu( far:fa-image Menu)
    UI(fas:fa-tablet-alt Application)
    Page( far:fa-image Page)
    User -- selects --> Menu
    Menu -- sends command to --> UI
    UI -- executes `focus` <br/> statement to <br/> display --> Page
{{< /mermaid >}}

Navigation occurs, as shown above, when
* the user presses or activatees some input control
* that control creates a message and sends it to the application
* in response to that message, the application executes a `focus` statement
* the `focus` statement execution changes what is presented to the user.

### Control

Control of the underlying System occurs when a user's request results in 
a message being sent from the Application to one or more other components in 
the same system. Consequently, the Application fully determines to what 
extent the user may control the system. 

## Applications Are Processors
Because RIDDL applications process 
[messages]({{< relref "../../../../../../concepts/message.md" >}}), they are
considered in RIDDL parlance to be 
[Processors]({{< relref "../../../../../../concepts/processor.md" >}}). This
means their definition involves defining the following concepts:
* [inlets]({{< relref "../../../../../../concepts/inlet.md" >}}) - the place
  that takes messages in for processing
* [outlets]({{< relref "../../../../../../concepts/outlet.md" >}}) - the 
  place that the application sends messages 
* [handlers]({{< relref "../../../../../../concepts/handler.md" >}}) - the
  processor of messages that implements the application's behavior based on
  messages received on its inlets and the effects it causes by sending
  messages to its outlets.

This arrangement abstractly mimics how current user interface software works
since operating systems receive events (inputs) and forward them to the
application for handling. Similarly, applications generate actions (effects)
that can be regarded as messages that yield information updates to users. 
All of the application logic is specified in the handlers.

A RIDDL application can be a model both a [source and a sink](
{{< relref "../../../../../../concepts/processor.md#kind-of-processors" >}})
of events.


Additionally, because RIDDL Applications must arbitrate the control of the
system, they can process messages to and from other system components
(e.g. contextsAs processors,
the application can model both a [source and a sink](
{{< relref "../../../../../../concepts/processor.md#kind-of-processors" >}})
of events. 

## Language Syntax
This part presents a set of very small examples to show how the concepts
described above can be specified in RIDDL.  

### 
To aid readability  

### Input




### Output
TBD

### Navigation

Navigation in RIDDL syntax looks like this:
```riddl
command DoCheckout is { cart: ShoppingCartId, user: UserId }
button CheckOut directs user Shopper with command DoCheckout
```

## Control

System control occurs within the Application's business logic when
an On Clause is .  Control occurs whenever a Command message is sent from
the application to a system component within the Applic




