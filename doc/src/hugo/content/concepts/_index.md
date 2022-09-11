---
title: "Concepts"
draft: false
weight: 5
---

In this section we will explore the concepts and ideas that RIDDL uses. This is
not about the RIDDL language or syntax, but about the concepts that the
language uses and how they relate to each other.

## Common Attributes

Everything you can define in RIDDL is a definition. All definitions share common
attributes:

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

## Attributes of Vital Definitions
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

RIDDL uses a hierarchy of nested definitions as its primary structure. This 
is done simply by having an attribute that lists the contents of any 
definition:

* _contents_: The contained definitions that define the container. Not all 
  definitions can contain other ones so sometimes this is empty.

Consequently, a hierarchical structure is used below as well. However, to 
make this hierarchy shorter and easier to comprehend, we've taken some 
short-cuts :

1. All the common attributes and the Vital Definition attributes 
are not shown in the hierarchy but implied by the above sections.
2. We only descend as far as an [Example]({{< relref "example.md" >}}) 
   definition. Whenever you see one, you should infer this hierarchy:
  * [Examples]({{< relref "example.md" >}})
    * [Actions]({{< relref "action.md" >}})
      * [Expressions]({{< relref "expression.md" >}})
3. We only descend as far as a [Type]({{< relref "type.md" >}}) definition. 
   Whenever you see one, you should infer this hierarchy: 
  * [Types]({{< relref "type.md" >}})
    * [Fields]({{< relref "field.md" >}})

With those clarifying simplifications, here's the hierarchy:
* [Root]({{< relref "root.md" >}})
  * [Domains]({{< relref "domain.md" >}})
    * [Types]({{< relref "type.md" >}})
    * [Contexts]({{< relref "context.md" >}})
      * [Types]({{< relref "type.md" >}})
      * [Entities]({{< relref "entity.md" >}})
        * [Types]({{< relref "type.md" >}})
        * [Functions]({{< relref "function.md" >}})
          * [Examples]({{< relref "example.md" >}})
        * [States]({{< relref "state.md" >}})
          * [Types]({{< relref "type.md" >}})
          * [Fields]({{< relref "field.md" >}})
          * [Handlers]({{< relref "handler.md" >}})
            * [On Clauses]({{< relref "onclause.md" >}})
              * [Examples]({{< relref "example.md" >}})
        * [Invariants]({{< relref "invariant.md" >}})
          * [Expressions]({{< relref "expression.md" >}})
        * [Handlers]({{< relref "handler.md" >}})
          * [On Clauses]({{< relref "onclause.md" >}})
            * [Examples]({{< relref "example.md" >}})
      * [Handlers]({{< relref "handler.md" >}})
        * [On Clauses]({{< relref "onclause.md" >}})
          * [Examples]({{< relref "example.md" >}})
      * [Projections]({{< relref "projection.md" >}})
        * [Types]({{< relref "type.md" >}})
        * [Fields]({{< relref "field.md" >}})
        * [Handlers]({{< relref "handler.md" >}})
          * [On Clauses]({{< relref "onclause.md" >}})
            * [Examples]({{< relref "example.md" >}})
      * [Sagas]({{< relref "saga.md" >}})
        * [Types]({{< relref "type.md" >}})
        * [SagaStep]({{< relref "sagastep.md" >}})
          * [Examples]({{< relref "example.md" >}})
      * [Adaptors]({{< relref "adaptor.md" >}})
        * [Types]({{< relref "type.md" >}})
        * [Adaptations]({{< relref "adaptation.md" >}})
          * [Examples]({{< relref "example.md" >}})
      * [Processors]({{< relref "processor.md" >}})
        * [Types]({{< relref "type.md" >}})
        * [Inlet]({{< relref "inlet.md" >}}) 
        * [Outlet]({{< relref "outlet.md" >}})
        * [Examples]({{< relref "example.md" >}})
      * [Functions]({{< relref "function.md" >}})
        * [Examples]({{< relref "example.md" >}})
    * [Stories]({{< relref "story.md" >}})
      * [Examples]({{< relref "example.md" >}})
    * [Plants]({{< relref "plant.md" >}})
      * [Processors]({{< relref "processor.md" >}})
        * [Types]({{< relref "type.md" >}})
        * [Inlet]({{< relref "inlet.md" >}})
        * [Outlet]({{< relref "outlet.md" >}})
        * [Examples]({{< relref "example.md" >}})
        * [InletJoints]({{< relref "joint.md" >}})
        * [OutletJoints]({{< relref "joint.md" >}})
        * [Pipes]({{< relref "pipe.md" >}})

