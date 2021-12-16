---
title: "Plants"
weight: 40
---

A RIDDL `plant` is a definition that combines [pipes](pipe) with [processors]
(processor) to specify a model of how data should flow. You may define as 
many plants as needed but each plant is a closed system without

Plants are entirely type safe. That is the kind of data that pipes transmit 
and the kinds of data that processors process must agree. 

The defini
Topics capture the notion of publish & subscribe systems. Messages are
published to a topic, and subscribers to that topic receive those messages, in
the order they were published. Consequently, a topic is a uni-directional
flow of messages from publishers to subscribers.

In RIDDL, topics are a container that define messages. Messages are how
bounded contexts, and services interact. There are four kinds of messages:
Commands, Events, Queries, and Results, as per DDD and reactive manifesto.
Messages form the API for a bounded context.

