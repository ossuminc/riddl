---
title: "Messages"
draft: false
---

Messages are just [aggregate type]({{< relref "type.md#aggregation" >}})
definitions that have special significance in RIDDL.  Messages make up the
_lingua franca_ of the API of a [`context`]({{< relref "context.md" >}}) or 
[`entity`]({{< relref "entity" >}}).
That is, these are the fundamental building blocks of a 
[message-driven system](https://developer.lightbend.com/docs/akka-platform-guide/concepts/message-driven-event-driven.html)

## Differences Between Kinds of Messages
The four kinds of messages are shown in the table below and derived from 
[Bertrand Meyer's](https://www.linkedin.com/in/bertrandmeyer/) notion of
[command/query separation](https://en.wikipedia.org/wiki/Command%E2%80%93query_separation)
which states, in the context of object-oriented programming (Eiffel) that:
> every method should either be a command that performs an action, or a query
> that returns data to the caller, but not both. In other words, asking a
> question should not change the answer.[^1]

Consequently, RIDDL adheres to this principal and employs the notion in message
definitions since RIDDL is message-oriented not object-oriented. 

[^1]: Meyer, Bertrand. "Eiffel: a language for software engineering". p. 22

|  Kind   | Request? | Response? | Cancellable? |     Relationship      |
|:-------:|:--------:|:---------:|:------------:|:---------------------:|
| Command |   Yes    |    No     |     Yes      |      Independent      |
|  Event  |  Maybe   |    Yes    |      No      | Consequent Of Command |
|  Query  |   Yes    |    No     |     Yes      |      Independent      |
| Result  |    No    |    Yes    |      No      | Consequent Of Result  |

The truth table above helps you understand the relationship between the kind of
message and how it is handled by an entity. This does not apply to messages

## How Messages Are Handled By Entities

|  Kind   | In Regard To Handling By  Entity |   
|:-------:|:--------------------------------:|
| Command |  Intent To Modify Entity State   |
|  Event  |     Entity State Was Changed     |
|  Query  |   Intent To Read Entity state    |
| Result  |       Consequent Of Query        |

## How Messages Are Handled By Projections
|  Kind   | In Regard To Handling By Projection |
|:-------:|:-----------------------------------:|
| Command |  Intent To Update Projection State  |
|  Event  | The Projection's State Was Modified |
|  Query  |   Intent to Read Projection State   |
| Result  | Result Of Reading Projection State  |

## How Messages Are Handled By Contexts
|  Kind   |  In Regard To Handling By Projection  |
|:-------:|:-------------------------------------:|
| Command | Intent To Take Some Stateless Action  |
|  Event  | Notification That a Command Completed |
|  Query  |    Intent to Read From The Context    |
| Result  |    Result Of Reading From Context     |

## How Messages Are Handled By Adaptors
|  Kind   | In Regard To Handling By Projection |
|:-------:|:-----------------------------------:|
| Command |   Intent To Translate For Context   |
|  Event  |   Intent To Translate For Context   |
|  Query  |   Intent to Translate For Context   |
| Result  |   Intent to Translate For Context   |



## Occurs In

* [Domains]({{< relref "domain.md" >}})
* [Plants]({{< relref "plant.md" >}})


## Contains
Nothing
