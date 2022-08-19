---
title: "Streaming"
type: "page"
draft: "false"
weight: 40
---

RIDDL supports the definition of complex data streaming models. There are two basic definitions
involved in setting up a pipeline:
a `pipe`, and a a `processor`. There are several kinds of pipes and processors, and they can be
combined to form entire systems of data processing known as a `plant`. These metaphors come from
chemical processing  
concepts which you can visualize as something like this:

![Visualization Of Pipeline](chemical-plant.jpg)

* [Pipes](pipe) - Pipes are conduits for reliably transmitting messages of a 
  particular type of data from publishers to consumers of that data type 
  that are connected to the pipe.
* [Processors](processor) - While pipes reliably transport data from its 
  producers to its consumers, processors are the producers, consumers, and 
  transformers of data. Notably, [entities](../context/entity) are 
  processors of pipes too.
* [Plants](plant) - Plants combine pipes and processors together with 
  support for rich semantics so that arbitrarily complex streams can be 
  modeled in RIDDL.

