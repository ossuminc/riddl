---
title: "Output"
draft: "false"
---

An Output definition is concerned with providing information to the user
(an [actor]({{< relref actor.md >}})) without regard to the form of that 
information when presented to the user. To make this more tangible, an 
Output could be implemented as any of the following:

* the text shown on a web page or mobile application
* the display of an interactive graphic, chart, etc. 
* the presentation of a video or audio recording
* haptic, olfactory or gustatory feedback
* any other way in which a human can receive information from a machine.

The nature of the implementation for an output is up to the UI Designer.
RIDDL's concept of it is based on the net result: the data type received by
the user.

An Output is a named component of an [application]({{< relref application.md>}})
that sends data of a specific [type]({{< relref type.md >}}) from the 
application to its user (an [actor]({{< relref actor.md>}}))  
Each input can define data [types]({{< relref type.md >}}) and declares a
[result message]({{< relref "message.md#result" >}}) as the data sent to the 
user.

## Occurs In
* [Group]({{< relref "group.md" >}})

## Contains
* [Type]({{< relref "type.md" >}})
* [Message]({{< relref "message.md" >}})
