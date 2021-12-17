---
title: "Pipes"
weight: 20
---

Pipes are uni-directional conduits for reliably transmitting data of a 
particular type between the publishers and consumers attached at the ends of 
the pipe.

## Syntax Example
```riddl
pipe WeatherForecast is {
  options rate(1000), paritions(7), 
  transmits type Forecast
}
```
In the foregoing, a pipe named `WeatherForecast` is defined to transmit the 
data type named `Forecast` and with two options:
* _rate_ - an expected sustained rate of 1000 data points per second
* _partitions_ - a minimum number of partitions on the data of 7

## Data Transmission Type
Pipes can transmit any data type that RIDDL can specify. There is only one 
data type that flows in a pipe.  The transmission type is often used with
an alternation of message types such as the commands and queries that an
[entity](../context/entity) might receive.

## Pipe Guarantees
Pipes play a large role in the resiliency of a reactive system 
because of the following characteristics:
* _persistent_ - The messages flowing through the pipe are persisted to
  stable, durable storage, so they cannot be lost even in the event of
  system failure or shutdown.
* _at least once_ - Messages are delivered to consumers of the pipe at least
  once.
* _processing commit_ - Pipes support the notion of a _processing commit_ which
  helps a consuming application know which messages have been fully
  processed and which may need further processing. This is helpful when the
  processor is starting up to know where it left off
* _partitioned consumption_ - For scale purposes, it must be able to
  partition the consumption so that multiple instances of a consuming
  service can process the messages in parallel.

## Publishers & Consumers
Attached to the ends of pipes are publishers and consumers. These are 
[processors](processor.md) of data and may originate, terminate or flow data 
through them, connecting two pipes together. 

{{<mermaid align="left">}}
graph LR;
Producers --> P{{Pipe}} --> Consumers

Source --> P1{{Pipe 1}} --> Flow --> P2{{Pipe 2}} --> Sink
{{< /mermaid >}}

Pipes may have multiple publishers (writers of data to the pipe) and
multiple consumers (readers of data from the pipe). In fact, because of the
_partitioned consumption_ principle, there can be multiple groups of
consumers, each group getting each data item from the pipe.

