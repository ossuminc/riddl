---
title: "Definitional Hierarchy"
weight: 40
---
## Introduction

Riddl strictly utilizes a hierarchy of nested definitions. This hierarchy defines the
basic structure of any Riddl specification. The list below shows this hieararchical model and
also serves as a handy way to navigate the kinds of definitions Riddl supports.

Here is how RIDDL nesting can be structured:

## Hierarchy
The hierarchy of container and leaf definitions is shown in the diagram below:

{{<mermaid>}} classDiagram
Root <|-- Include
Root <|-- Domain
Domain <|-- Context
Context <|-- Adaptor
Context <|-- Entity
Entity <|-- Action
Entity <|-- Consumer
Entity <|-- Feature
Entity <|-- Function
Entity <|-- Invariant
Entity <|-- Producer
Entity <|-- State
Entity <|-- Type
Context <|-- Projection
Context <|-- Type
Domain <|-- Topic
Topic <|-- Event
Topic <|-- Command
Topic <|-- query
Topic <|-- Result
Domain <|-- Type
Domain <|-- Interactions (looking for a home!)
{{</mermaid>}}
