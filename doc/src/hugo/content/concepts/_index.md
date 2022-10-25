---
title: "Concepts"
draft: false
weight: 5
---

In this section we will explore the concepts and ideas that RIDDL uses. This is
not about the RIDDL language or syntax, but about the concepts that the
language uses and how they relate to each other.

## Common Attributes

RIDDL is a declarative language that consists of various types of definitions
in a hierarchy. A definition is anything that has a unique name that we call
its *identifier*. All definitions have some common attributes:

* _loc_: The location of the definition in its input file. (line & column)
* _id_: The name, or identifier, of the definition. 
* _briefly_: A string to briefly describe the definition. These are used in 
  the documentation output and the glossary. 
* _description_: A block of 
   [Markdown](https://www.markdownguide.org/getting-started/) that 
  fully describes the definition. All the facilities provided by the  
  [hugo-geekdoc](https://geekdocs.de/) template for hugo are supported.

These attributes merely provide supplemental information about the 
definition but are not part of the definition.  

## Vital Definitions
The [vital definitions]({{< relref "vital.md" >}}) share a set of 
common attributes that, like the [Common Attributes](#common-attributes),
are informational rather than definitional. 

These are the common attributes that 
[vital definitions]({{< relref "vital.md" >}}) share:
* [_includes_]({{< relref "include.md" >}}) - include content from another file
* [_options_]({{< relref "option.md" >}}) - define translation options for the
  definition
* [_authors_]({{< relref "author.md" >}}) - define who the authors of the
  definition are
* [_terms_]({{< relref "term.md" >}}) - define a term as part of the
  ubiquitous language for the definition.

## Definitional Hierarchy

Definitions in RIDDL are arranged in a hierarchy. Definitions that contain other
definitions are known as *containers* or *parents*. Definitions that do not
contain other definitions are known as *leaves* or *children*.

This is done simply by having an attribute that lists the contents of any 
definition:

* _contents_: The contained definitions that define the container. Not all 
  definitions can contain other ones so sometimes this is empty.

### Simplifications
The valid hierarchy structure is shown below, but to make this hierarchy 
shorter and easier to comprehend, we've taken some short-cuts :

1. All the common attributes and the Vital Definition attributes 
are not shown in the hierarchy but implied by the above sections.
2. We only descend as far as an [Example]({{< relref "example.md" >}}) 
   definition; but you should infer this extended hierarchy:
  * [Examples]({{< relref "example.md" >}})
    * [Actions]({{< relref "action.md" >}})
      * [Expressions]({{< relref "expression.md" >}})
3. We only descend as far as a [Type]({{< relref "type.md" >}}) definition. 
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
      * [Element]({{< relref "element.md" >}})
    * [Story]({{< relref "story.md" >}})
      * [Case]({{< relref "case.md" >}})
        * [Example]({{< relref "example.md" >}})
    * [Context]({{< relref "context.md" >}})
      * [Type]({{< relref "type.md" >}})
      * [Entity]({{< relref "entity.md" >}})
        * [Type]({{< relref "type.md" >}})
        * [Function]({{< relref "function.md" >}})
          * [Example]({{< relref "example.md" >}})
        * [State]({{< relref "state.md" >}})
          * [Type]({{< relref "type.md" >}})
          * [Field]({{< relref "field.md" >}})
          * [Handler]({{< relref "handler.md" >}}
            * [On Clause]({{< relref "onclause.md" >}})
              * [Example]({{< relref "example.md" >}})
        * [Invariant]({{< relref "invariant.md" >}})
          * [Expression]({{< relref "expression.md" >}})
        * [Handler]({{< relref "handler.md" >}})
          * [On Clause]({{< relref "onclause.md" >}})
            * [Example]({{< relref "example.md" >}})
      * [Handler]({{< relref "handler.md" >}})
        * [On Clause]({{< relref "onclause.md" >}})
          * [Example]({{< relref "example.md" >}})
      * [Projection]({{< relref "projection.md" >}})
        * [Type]({{< relref "type.md" >}})
        * [Field]({{< relref "field.md" >}})
        * [Handler]({{< relref "handler.md" >}})
          * [On Clause]({{< relref "onclause.md" >}})
            * [Example]({{< relref "example.md" >}})
      * [Saga]({{< relref "saga.md" >}})
        * [Type]({{< relref "type.md" >}})
        * [SagaStep]({{< relref "sagastep.md" >}})
          * [Example]({{< relref "example.md" >}})
      * [Adaptor]({{< relref "adaptor.md" >}})
        * [Type]({{< relref "type.md" >}})
        * [Adaptation]({{< relref "adaptation.md" >}})
          * [Example]({{< relref "example.md" >}})
      * [Processor]({{< relref "processor.md" >}})
        * [Type]({{< relref "type.md" >}})
        * [Inlet]({{< relref "inlet.md" >}}) 
        * [Outlet]({{< relref "outlet.md" >}})
        * [Example]({{< relref "example.md" >}})
      * [Function]({{< relref "function.md" >}})
        * [Example]({{< relref "example.md" >}})
    * [Plant]({{< relref "plant.md" >}})
      * [Processor]({{< relref "processor.md" >}})
        * [Type]({{< relref "type.md" >}})
        * [Inlet]({{< relref "inlet.md" >}})
        * [Outlet]({{< relref "outlet.md" >}})
        * [Example]({{< relref "example.md" >}})
      * [InletJoint]({{< relref "joint.md" >}})
      * [OutletJoint]({{< relref "joint.md" >}})
      * [Pipe]({{< relref "pipe.md" >}})

## Next
When you're done exploring all the concepts, check out our 
[guides]({{< relref "../guides/" >}}) next.

## Full Index

{{< toc-tree >}}
