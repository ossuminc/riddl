---
title: "Processors"
draft: false
---

A processor is a component of any [vital definition]({{< relref "vital" >}}). 
Processors perform some transformation, typically, of the data flowing in from
its[inlet(s)]({{< relref inlet >}}) and produces some, potentially different, 
data to its [outlet(s)]({{> relref outlet >}}).


## Inlets
An (inlet)[{{< relref inlet >}}] provides the name and data type for an 
input to the processor. There can be multiple inlets to the processor or none. 
A processor with no inlets defined is called a _source_ since it originates data
by itself. 

## Outlets

An (outlet)[{{< relref outlet >}}] provides the name and data type for an 
output from the processor. There can be multiple outlets defined by the 
processor or none. A processor with no outlets is called a _sink_ since it 
terminates data flow.

## Kinds Of Processors

RIDDL supports six kinds of processors. The kind of processor depends solely
on the number of inlets and outlets that are defined by the processor, as 
shown in the table:

| # Inlets | # Outlets | Kind   | Description                                                 |
|----------|-----------|--------|-------------------------------------------------------------|
| 0        | any       | Source | Sources originate their data, and publish it to an outlet   |
| any      | 0         | Sink   | Sinks terminate their data, and consume it from their inlet |
| 1        | 1         | Flow   | Flows transform their data from inlet to outlet             |
| 1        | any       | Split  | Splits their data from one source to multiple outlets       |
| any      | 1         | Merge  | Merges their data from multiple intles to a single outlet   |
| any      | any       | Multi  | Any other combination is a many-to-many flow                |


## Occurs In
* [Contexts]({{< relref "domain.md" >}})
* [Plants]({{< relref "plant.md" >}})

# Contains
* [Inlets]({{< relref "inlet.md" >}})
* [Outlets]({{< relref "outlet.md" >}})
* [Examples]({{< relref "example.md" >}})