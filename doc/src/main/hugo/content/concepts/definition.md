---
title: "Definition"
draft: false
---

RIDDL is a declarative language, in that it declares the definition of 
certain concrete concepts in a hierarchical fashion. The notion of a 
definition is abstract since there are many types of definitions, 
all described in this
[concepts section]({{< relref _index.md >}}). Succinctly, a definition is 
anything that has a unique name that we call its *identifier*.  

## Common Attributes
All definitions have some common attributes:

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
attributes that, like the [Common Attributes](#common-attributes),
are informational rather than definitional. 
