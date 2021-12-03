---
title: "Topic"
type: page
---

# Introduction

Topics capture the notion of a publish & subscribe system. Messages are
published to a topic, and subscribers to that topic receive those messages, in
the order they were published. Consequently, a topic is a uni-directional
flow of messages from publishers to subscribers.

In RIDDL, topics are a container that define messages. Messages are how
bounded contexts, and services interact. There are four kinds of messages:
Commands, Events, Queries, and Results, as per DDD and reactive manifesto.
Messages form the API for a bounded context.

![cqrs](../../../../static/images/results.png "Entities")
