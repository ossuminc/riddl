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

### Root
At the root (file) level you can only do two things: include definitions
from another file or define a domain
{{<mermaid>}} classDiagram
class Root {}
Root <|-- Include
Root <|-- Domain
{{</mermaid>}}
### Domain
A domain is a container definition that can define  bounded contexts, (sub)
domains, topics and types
{{<mermaid>}} classDiagram
class Domain {}
Domain <|-- Context
Domain <|-- Domain
Domain <|-- Topic
Domain <|-- Type
Domain <|-- Interactions (looking for a home!)
{{</mermaid>}} 
### Context (bounded)
A Context is a container definition in which an adaptor (anti-corruption 
layers), entities, projections, and types can be defined
{{<mermaid>}} classDiagram
class Context {}
Context <|-- Adaptor
Context <|-- Entity
Context <|-- Projection
Context <|-- Type
{{</mermaid>}} 
### Entity
An entity is a container definition in which an entity (thing, object) is 
defined in terms of the messages it consumes and produces, the state (data) 
it holds, the types it uses, invariant expressions, and the features, 
functions and actions that define its behavior.
{{<mermaid>}} classDiagram
class Entity {}
Entity <|-- Action
Entity <|-- Consumer
Entity <|-- Feature
Entity <|-- Function
Entity <|-- Invariant
Entity <|-- Producer
Entity <|-- State
Entity <|-- Type
{{</mermaid>}}
### Topic
A topic is a container definition that represents a communication channel 
through which messages flow. The contained things are the kinds of messages 
that may flow through that topic
{{<mermaid>}} classDiagram
class Topic {}
Topic <|-- Event
Topic <|-- Command
Topic <|-- query
Topic <|-- Result
{{</mermaid>}}
