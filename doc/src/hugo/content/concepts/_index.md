---
title: "Concepts"
draft: false
weight: 5
---

In this section we will explore the concepts and ideas that RIDDL uses. This is
not about the RIDDL language syntax, but about the concepts that the
language uses and how they relate to each other.

## Definitions
RIDDL consists only of [definitions]({{< relref definition.md >}}) that 
define the design of the desired system.  

## Definitional Hierarchy

Definitions in RIDDL are arranged in a hierarchy. Definitions that contain other
definitions are known as *containers* or *parents*. Definitions that do not
contain other definitions are known as *leaves*.

This is done simply by having an attribute that lists the contents of any 
definition:

* _contents_: The contained definitions that define the container. Not all 
  definitions can contain other ones so sometimes this is empty.

### Simplifications
The valid hierarchy structure is shown below, but to make this hierarchy 
easier to comprehend, we've taken some short-cuts :

1. All the [common attributes]({{< relref "definition.md#common-attributes">}}) 
   have been omitted for brevity but are implied on each line of the 
   hierarchy.
2. We only descend as far as a [Type]({{< relref "type.md" >}}) definition. 
   Whenever you see one, you should infer this hierarchy: 
  * [Types]({{< relref "type.md" >}})
    * [Fields]({{< relref "field.md" >}})

### Hierarchy
With those clarifying simplifications, here's the hierarchy:
* [Root]({{< relref "root.md" >}})
  * [Domain]({{< relref "domain.md" >}})
    * [Type]({{< relref "type.md" >}})
    * [Application]({{< relref "application.md" >}})
      * [Type]({{< relref "type.md" >}})
      * [Group]({{< relref "element.md#group" >}})
        * [Output]({{< relref "output.md" >}})
        * [Input]({{< relref "input.md" >}})
      * [Handler]({{< relref handler.md >}})
    * [Epic]({{< relref "epic.md" >}})
      * [Case]({{< relref "case.md" >}})
        * [Statement]({{< relref "statement.md" >}})
    * [Context]({{< relref "context.md" >}})
      * [Type]({{< relref "type.md" >}})
      * [Entity]({{< relref "entity.md" >}})
        * [Type]({{< relref "type.md" >}})
        * [Function]({{< relref "function.md" >}})
          * [Statement]({{< relref "statement.md" >}})
        * [State]({{< relref "state.md" >}})
          * [Type]({{< relref "type.md" >}})
          * [Field]({{< relref "field.md" >}})
          * [Handler]({{< relref "handler.md" >}})
            * [On Clause]({{< relref "onclause.md" >}})
              * [Statement]({{< relref "statement.md" >}})
        * [Invariant]({{< relref "invariant.md" >}})
          * [Conditional]({{< relref "conditional.md" >}})
        * [Handler]({{< relref "handler.md" >}})
          * [On Clause]({{< relref "onclause.md" >}})
            * [Statement]({{< relref "statement.md" >}})
      * [Handler]({{< relref "handler.md" >}})
        * [On Clause]({{< relref "onclause.md" >}})
          * [Statement]({{< relref "statement.md" >}})
      * [Projection]({{< relref "projection.md" >}})
        * [Type]({{< relref "type.md" >}})
        * [Field]({{< relref "field.md" >}})
        * [Handler]({{< relref "handler.md" >}})
          * [On Clause]({{< relref "onclause.md" >}})
            * [Statement]({{< relref "statement.md" >}})
      * [Saga]({{< relref "saga.md" >}})
        * [Type]({{< relref "type.md" >}})
        * [SagaStep]({{< relref "sagastep.md" >}})
          * [Statement]({{< relref "statement.md" >}})
      * [Adaptor]({{< relref "adaptor.md" >}})
        * [Type]({{< relref "type.md" >}})
        * [Handler]({{< relref "handler.md" >}})
          * [On Clause]({{< relref "onclause.md" >}})
            * [Statement]({{< relref "statement.md" >}})
      * [Processor]({{< relref "processor.md" >}})
        * [Type]({{< relref "type.md" >}})
        * [Inlet]({{< relref "inlet.md" >}}) 
        * [Outlet]({{< relref "outlet.md" >}})
        * [Statement]({{< relref "statement.md" >}})
      * [Function]({{< relref "function.md" >}})
        * [Statement]({{< relref "statement.md" >}})
    * [Plant]({{< relref "plant.md" >}})
      * [Processor]({{< relref "processor.md" >}})
        * [Type]({{< relref "type.md" >}})
        * [Inlet]({{< relref "inlet.md" >}})
        * [Outlet]({{< relref "outlet.md" >}})
        * [Statement]({{< relref "statement.md" >}})
      * [InletJoint]({{< relref "joint.md" >}})
      * [OutletJoint]({{< relref "joint.md" >}})
      * [Pipe]({{< relref "pipe.md" >}})

## Next
When you're done exploring all the concepts, check out our 
[guides]({{< relref "../guides/" >}}) next.

## Full Index

{{< toc-tree >}}
