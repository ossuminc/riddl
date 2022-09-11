---
title: "Messages"
draft: false
---

Messages are just [aggregate type]({{< relref "type.md#aggregation" >}})
definitions that have special significance in RIDDL.  Messages make up the
_lingua franca_ of the API of a [`context`](context) or [`entity`](entity).
That is, these are the fundamental building blocks of a 
[message-driven system](https://developer.lightbend.com/docs/akka-platform-guide/concepts/message-driven-event-driven.html)

## Differences Between Kinds of Messages
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
|  Query  |          Intent To Read          |
| Result  |       Consequent Of Query        |

## How Messages Are Handled By Projections
|  Kind   |   In Regard To Handling By Projection    |
|:-------:|:----------------------------------------:|
| Command | Intent To Update The Projection's State  |
|  Event  |   The Projection's State Was Modified    |
|  Query  |  Intent to Read The Projection's State   |
| Result  |   Result Of Reading Projection's State   |

## How Messages Are Handled By Contexts
|  Kind   |  In Regard To Handling By Projection  |
|:-------:|:-------------------------------------:|
| Command |  Not Allowed, Contexts Are Stateless  |
|  Event  |  The Projection's State Was Modified  |
|  Query  | Intent to Read The Projection's State |
| Result  | Result Of Reading Projection's State  |


## Implications Of Messages On Contexts

## Occurs In

* [Domains]({{< relref "domain.md" >}})
* [Plants]({{< relref "plant.md" >}})


## Contains
Nothing
