# Events

An event as the result of the command is created and forwarded to an event store or
a messaging bus. An event has a unique id in order to prevent duplication.
An event or message can be duplicated in case of system failure.

Ordering an event is also important. Some message broker supports event-ordering. e.g (Kafka)
However, Kafka only supports the ordering in a partition. If you need global ordering, design
idempotency. Simple idempotency can be implemented using a unique message-id. For more details, please refer to Kafka documentation

* [Kafka ordering guarantees](http://kafka.apache.org/documentation.html#intro_guarantees) 