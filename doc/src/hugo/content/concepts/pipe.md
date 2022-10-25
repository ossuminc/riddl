---
title: "Pipes"
draft: false
---

Pipes are uni-directional conduits for reliably transmitting data of a
particular type. Pipes always make connections between an
[InletJoint]({{< relref "joint#inletjoint" >}}) (the source of data) and an 
[OutletJoint]({{< relref "joint#outletjoint" >}}). These joints are always 
attached to a [processor]({{< relref "processor.md" >}}), 
even possibly the same processor (making a feedback loop). 

## Data Transmission Type
Pipes can transmit any data type that RIDDL can specify. There is only one
data type that flows in a pipe.  The transmission type is often used with
an [alternation]({{< relref "type#alternation" >}} ) of 
[messages]({{< relref "message" >}}) such as the 
commands and queries that an [entity](entity) might receive.

## Pipe Options
Pipes may play a large role in the resiliency of a reactive system, so we 
permit a variety of options to be specified on them. These options are 
only intended as advice to the translators converting the pipe into something 
else. For example, a pipe may or may not need to be persistent. If a pipe has
the burden of persistence removed, it is likely much more performant because 
the latency of storage is not involved.

### `persistent`
The messages flowing through the pipe are persisted to stable, durable storage,
so they cannot be lost even in the event of system failure or shutdown. This
arranges for a kind of 
[bulkhead](https://learn.microsoft.com/en-us/azure/architecture/patterns/bulkhead) 
in the system that retains
published data despite failures on either end of the pipe

### `commitable`
With this option, pipes support the notion of being _commitable_. This means
the consuming processors of a pipe's data may commit to the pipe that they
have completed their work on one or more data items. The pipe then guarantees
that it will never transmit those data items to that processor again. This is
helpful when the processor is starting up to know where it left off from its
previous incarnation.

### `partitions(n)`
For scale purposes, a pipe must be able to partition the data by some data
value that is in each data item (a _key_) and assign the consumption of the
data to corresponding members of a consumer group. This permits multiple
instances of a consuming processor to handle the data in parallel. The `n`
value is the minimum recommended number of partitions which defaults to 5
if not specified

### `lossy`

By default, pipes provide the guarantee that they will deliver each data item
_at least once_. The implementation must then arrange for data items to be 
idempotent so that the effect of running the event two or more times is the
same as running it once. To counteract this assumption a pipe can be use the
`lossy` option which reduces the guarantee to merely _best reasonable effort_,
which could mean loss of data. This may increase throughput and lower overhead
and is warranted in situations where data loss is not catastrophic to the
system. For example, some IoT systems permit this kind of data loss because 
the next transmission is imminent.

## Producers & Consumers

Attached to the ends of pipes are producers and consumers. These are
[processors]({{< relref "processor.md" >}}) of data and may originate, 
terminate or flow data through them,
connecting two pipes together. Producers provide the data, consumers consume
the data. Sometimes we call producers *sources* because they originate the data.
Sometimes we call consumers *sinks* because they terminate the data.

{{< mermaid align="left" >}}
graph LR;
Producers --> P{{Pipe}} --> Consumers
Source --> P1{{Pipe 1}} --> Flow --> P2{{Pipe 2}} --> Sink
{{< /mermaid >}}

Pipes may have multiple publishers (writers of data to the pipe) and multiple
consumers (readers of data from the pipe). In fact, because of the
_partitioned consumption_ principle, there can be multiple groups of consumers,
each group getting each data item from the pipe.

## Subscriptions

{{< hint type=warning icon=gdoc_dangerous title="Not Implemented" >}}
This feature is not implemented as of 0.16.1
{{< /hint >}}

When a pipe has multiple consumers, they are organized into subscriptions. 
Each subscription gets every datum the pipe carries. Consumers attach to a
subscription and there is generally one consumer per partition of the 
subscription. Sometimes subscriptions are known as *consumer groups* as is the
case for Kafka.

## Occurs In

* [Plants]({{< relref "plant.md" >}})


## Contains
Nothing
