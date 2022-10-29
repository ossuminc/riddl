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



# Activate
An Activate definition instructs the application to change context to a 
different group of elements.

## Occurs In
* [Applications]({{< relref "application.md" >}})

## Contains
* [Elements]({{< relref "element.md" >}})
* [Handlers]({{< relref handler.md >}})

