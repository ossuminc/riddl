---
title: "Repository"
draft: false
---

A RIDDL repository is an abstraction for anything that can retain 
information (e.g. [messages]({{< relref message.md >}})) for retrieval at a
later time. This might be a relational database, NoSQL database, a data lake, 
an API, or something not yet invented. There is no specific technology implied
other than the retention and retrieval of information. You should think of 
repositories more like a message-oriented version of the 
[Java Repository Pattern](https://java-design-patterns.com/patterns/repository/#explanation)
than any particular kind of database. 

## Schemas
Repositories have traditionally had data schemas as part of their design 
definition; but RIDDL regards schemas as a technical detail of the 
implementation and leaves them unspecified. Repositories are only defined in 
terms of their message handling.

## Handling Messages
A repository has a [handler]({{< relref handler.md >}}) that processes 
messages with respect to the repository's stored information.

[Query messages]({{< relref "message.md#query" >}}) sent to the repository 
are requests for retrieval of some information. The handler should define 
how the processing of that query should proceed and yield a 
[Result message]({{< relref "message.md#result" >}}).

[Command messages]({{< relref "message.md#query" >}}) sent to the 
repository are updates to the repository. The handler should define how the 
update works and may optionally yield an
[Event message]({{< relref "message.md#result" >}}) but generally that is 
handled at a higher level of abstraction. 

## Occurs In
* [Context]({{< relref "context.md" >}})

## Contains
* [Types]({{< relref "type.md" >}})
* [Messages]({{< relref "message.md" >}})
* [Handler]({{< relref handler.md >}})

