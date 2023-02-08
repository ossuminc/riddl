---
title: "Application"
draft: false
---

An application in RIDDL represents an interface portion of a system where an 
actor (human or machine) initiates an action on the system. Applications 
only define the net result of the interaction between the actor and the 
application. They are abstract on purpose. That is, there is nothing in RIDDL 
that defines how information is provided to a user nor received from a user. 
This gives free latitude to the user interface 
designer to manage the entire interaction between human and machine. 

There are also no assumptions about the technology used for the 
implementation of an application. RIDDL's notion of an application is general 
and abstract, but they can be implemented as any of the following:
* Mobile Application On Any Platform
* Web Application
* Native Operating System Application (graphical or command line)
* Interactive Voice Recognition
* Virtual Reality with Haptics
* and other things yet to be invented. 

This means a RIDDL application specification can be used as the basis for 
creating multiple implementations of the specification using a variety of 
technologies.     

## Groups
Applications abstractly design a user interface by containing a set of 
[groups]({{< relref "group.md" >}}). Groups can be nested which allows them
to define the structure of a user interface. 

## Handlers
Applications have message 
[handlers]({{< relref handler.md >}}) like many other RIDDL definitions. 
However, application handlers only receive their messages from 
[actors]({{< relref actor.md >}}), unlike other handlers. Typically, the 
handling of messages in handlers will ultimately send further messages to 
other components, like a [context]({{< relref context.md >}}) or
[entity]({{< relref entity.md >}})

## Occurs In
* [Domain]({{< relref "domain.md" >}})

## Contains
* [Type]({{< relref "type.md" >}})
* [Group]({{< relref "element.md" >}})
* [Handler]({{< relref "handler.md" >}})
