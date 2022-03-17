---
title: "Options"
type: "page"
weight: 15
draft: "false"
---

The optional entity kind prefix is a directive that suggests how the entity
might handle its state and received messages. In the example above, we
expect the "Printer" entity to be a physical device. An "actor" entity in
of the same name could be expected to be a person who prints.

The options available suggest how the entity might handle its state and
message input:
* _kind_ - indicates the intended kind of entity in an argument to the kind entity.
* _event sourced_ - indicates that event sourcing is used in the persistence of the entity's states,
  storing a log of change events
* _value_ - indicates that only the current value of the entity's state is persisted, recording no history off the change events
* _aggregate_ - indicates this entity is an 'aggregate root entity' as defined by DDD.
* _persistent_ - indicates that this entity should persist its state to stable storage.
* _consistent_ - indicates that this entity tends towards Consistency as defined by the CAP theorem and therefore uses sharding and the single writer principle to
  ensure a consistent view on state changes 
* _available_ - indicates that this entity tends towards Availability as defined by the CAP theorem and therefore replicates its entity state to multiple locations or
  even across data centers using CRDTs (conflict-free replicated data types).
* _grpc_ - indicates that the entity should be accessible via gRPC api invocations
* _mq_ - indicates that commands and queries may be sent by a message queue such as Kafka, Pulsar, or Google Pub Sub
