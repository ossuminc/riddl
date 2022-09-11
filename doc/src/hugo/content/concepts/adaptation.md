---
title: "Adaptation"
draft: false
---

An adaptation is a single component of an 
[adaptor]({{< relref "adaptor.md" >}}) that defines a specific kind of 
translation or adaptation to undertake. There are several kinds of 
adaptations that can be defined, all pertain to message handling. 

|      Name      | Description                                               |
|:--------------:|:----------------------------------------------------------|
|  EventCommand  | Transformation of an incoming Event to a Command          |
| CommandCommand | Transformation of an incoming Command to another Command  |
|  EventAction   | Transformation of an incoming Event to a set of Actions   |
| CommandAction  | Transformation of an incoming Command to a set of Actions |


## Occurs In
* [Adaptors]({{< relref "adaptor.md" >}})

## Contains
* [Examples]({{< relref "example.md" >}})
