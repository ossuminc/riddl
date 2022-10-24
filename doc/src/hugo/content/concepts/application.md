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
implementation of an application. RIDDL offers an abstract notion of an 
application that can be implemented as any of the following:
* Mobile Application On Any Platform
* Web Application
* Native Operating System Application (graphical or command line)
* Interactive Voice Recognition
* Virtual Reality with Haptics
* and other things yet to be invented. 

Applications are composed of [elements]({{< relref element.md >}}]]) that 
further maintain the abstract design of actor interfaces. Elements are 
always designed with regard to a data structure 
([type]({{< relref type.md >}})). For more details on that read the 
[elements]({{< relref element.md >}}) page.

## Occurs In
* [Domain]({{< relref "domain.md" >}})

## Contains
* [Type]({{< relref "type.md" >}})
* [Element]({{< relref "element.md" >}})
