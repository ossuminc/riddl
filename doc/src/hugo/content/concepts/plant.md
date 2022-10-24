---
title: "Plants"
draft: false
---

RIDDL supports the definition of complex data streaming models. There are three
basic definitions involved in setting up a pipeline:
a `plant`, a `pipe`, and a `processor`. There are several kinds of pipes and
processors, and they can be combined to form entire systems of data processing
known as a `plant`. These metaphors come from chemical processing concepts
which you can visualize as something like this:

![Visualization Of Pipeline](../chemical-plant.jpg)

All you have to do is remember the 3 _P_'s:
* [Pipes]({{< relref "pipe" >}}) - Pipes are conduits for reliably transmitting 
  messages of a
  particular type of data between processors that are connected to the pipe.
* [Processors]({{< relref "processor" >}}) - While pipes reliably transport
  data from its
  producers to its consumers, processors are the producers, consumers, and
  transformers of data. Notably, [entities]({{< relref "entity.md" >}})
  are processors of pipes too.
* [Plants]({{< relref "plant" >}}) - Plants combine pipes and processors
  together with  support for rich semantics so that arbitrarily complex 
  streams can be modeled in RIDDL.


A RIDDL `plant` is a definition that combines [pipes]({{< relref "pipe" >}}) with
[processors]({{< relref "processor" >}}) to specify a model of how data 
should flow. You may define as many plants as needed but each plant is a 
closed system without the ability of the RIDDL model to express the sharing 
of data between the plants. This is done deliberately to prevent
unintentional contamination of data in large models.

## Joints
The purpose of a plant definition is to provide the blueprint for how a set
of pipes, processors, and entities are joined together so that data may flow
end-to-end. This is done by using:
* the names and types of inlets in processors
* the names and types of outlets in processors
* the names and content types of pipes
* the definition of a `joint` to connect pipes and processors

## Type Safety
Plants are entirely type safe. This means the data type that a pipe
transmits must match the data type of the publishing processors (outlets) and
the data types of the consuming processors (inlets). When `riddlc` processes
a plant specification, it ensures that all the inlet and outlet data types
match the data types of the connected pipes.

## Entities as Processors
An [entity]({{< relref "entity" >}}) may also be used as a processor under some
special circumstances:
* _as a source_ - An entity may be used as a source of events if a command handler
  is defined for the entity.
* _as a sink_ - An entity may be used as a sink for events if a reaction handler
  is defined for the entity.
* _as a flow_ - An entity may be used as a flow to convert commands into events

## Bulkheads
TBD


## Occurs In
* [Domains]({{< relref "domain.md" >}})
* [Contexts]({{< relref "context.md" >}})

# Contains
* [Processors]({{< relref "processor.md" >}})
* [Pipes]({{< relref "pipe.md" >}})
* [Joints]({{< relref "joint.md" >}})