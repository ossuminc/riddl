---
title: "Application Element"
draft: "false"
---

*Elements* are the definitions that define the actor interface for an
[application]({{< relref application.md >}}). Every element is associated 
with a data [type]({{< relref type.md >}}) for either input or output. 
Actors using an application are either sending information

## Element Kinds
There are several kinds of elements, broken into three types: input, output,
and grouping. Here are the input types of elements: 
* *select* - input of a choice from a list of items
* *provide* - input of data items to fill an 
  [aggregate type]({{< relref "type.md#aggregation" >}})

Here is the output type of element:
* *present* - present data type to actor

Here are the grouping type of elements:
* *group* - groups together a set of elements so they can be referenced 
  collectively

## Occurs In
* [Applications]({{< relref "application.md" >}})

## Contains
* [Elements]({{< relref "element.md" >}})
* [Examples]({{< relref "example.md" >}})

