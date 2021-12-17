---
title: "Pipes"
weight: 20
---

Pipes are conduits for reliably transmitting messages of a particular type
between publishers and consumers attached at the ends of the conduit.
Pipes play a large role in the resiliency of a reactive system. Pipes have
the following characteristics:
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

Pipes can carry any data type that RIDDL can specify and is often used with
an alternation of message types such as the commands and queries that an
[entity](../context/entity) might receive.  Pipes are strongly typed,
however, so whatever

Pipes may have multiple publishers (writers of data to the pipe) and
multiple consumers (readers of data from the pipe). In fact, because of the
_partitioned consumption_ principle, there can be multiple groups of
consumers, each group getting each data item from the pipe.

## Syntax Example
```riddl
pipe WeatherForecast is {
  options rate(1000), paritions(7), 
  content = type 
}
```
