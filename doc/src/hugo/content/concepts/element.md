---
title: "Application Element"
draft: "false"
---

*Elements* are the definitions that define the actor interface for an
[application]({{< relref application.md >}}). Every element is associated 
with a data [type]({{< relref type.md >}}) for either input or output. 
Actors using an application are either sending information

## Element Types
There is one RIDDL definition for each of the four typical categories of 
User Interface elements[^1] as shown in the table below

[^1]: See [Critical UI Elements of Remarkable Interfaces](https://www.peppersquare.com/blog/4-critical-ui-elements-of-remarkable-interfaces/) 


| UI Element | RIDDL    | Description                                  |
|------------|----------|----------------------------------------------|
| Input      | Give     | input of data items to fill an aggregate     |
| Input      | Select   | select item(s) from a list                   |
| Output     | View     | presents a data value for consideration      |
| Navigation | Activate | cause the application to change its context  |
| Container  | Group    | groups elements together                     |

# Group
A group is simply a named container of the five elements shown in the table 
above. An application is simply defined as a list of groups. The other four 
element kinds must appear within a group. Groups can appear within a 
containing group and in this way the application input and output can be
specified hierarchically. A UI designer is free to arrange the contained 
elements in any fashion, but presumably in a way that is consistent with
their overall UI design theme.

# Give
A give definition defines an input control as a 
[message]({{< relref message.md >}}), typically of an
[aggregate]({{< relref "type.md#aggregation" >}}) form.  This could be 
given as:
* submission of a typical htML form the user could fill in,
* a voice response providing information via an [IVR](https://wikipedia.com/en/IVR) system 
* a thought interpreted by a NeuralLink
* picking items from presented lists by looking and blinking
* or any other system by which a human may provide information to a machine 

The nature of the interface for a give is up to the UI Designer, we just model 
here that some data type that is put in to the application

# Select
A select definition, like [Give](#give), is also an input control, except it
yields a list of the items selected from a list. 
Consequently, the data type for a Select must be a
[message]({{< relref message.md >}}) that has zero-or-more or one-or-more 
[cardinality]({{< relref "type.md#cardinality" >}}).

# View
A view definition is concerned with providing information to the user whether
that is text, graphic, video, audio, haptic or olfactory. 
Again, the form of
the information presentation is up to the UI designer, we just model that some
data type is provided by the application

# Activate
An Activate definition instructs the application to change context to a 
different group of elements.

## Occurs In
* [Applications]({{< relref "application.md" >}})

## Contains
* [Elements]({{< relref "element.md" >}})
* [Handlers]({{< relref handler.md >}})

