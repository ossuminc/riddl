---
title: "Pipelines"
---

RIDDL uses the notion of a pipeline to model data flow and streaming concepts. 
There are two basic constructs in setting up a pipeline: a `pipe` and a 
`streamlet`. Pipes are conduits for reliably transmitting messages of a 
particular type between publishers and consumers attached at the ends of the 
conduit. Streamlets are data processors that transform their input(s) to 
their output(s). the input(s) and output(s) are attached to the ends of 
pipes. Streamlet outputs are producers to a pipe. Streamlet inputs are 
consumers from a pipe. 

## Syntax Example
```riddl
pipeline Plumbing is {
  pipe 
}
```
The above pipeline definition

Topics capture the notion of publish & subscribe systems. Messages are
published to a topic, and subscribers to that topic receive those messages, in
the order they were published. Consequently, a topic is a uni-directional
flow of messages from publishers to subscribers.

In RIDDL, topics are a container that define messages. Messages are how
bounded contexts, and services interact. There are four kinds of messages:
Commands, Events, Queries, and Results, as per DDD and reactive manifesto.
Messages form the API for a bounded context.

