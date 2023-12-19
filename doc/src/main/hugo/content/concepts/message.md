---
title: "Messages"
draft: false
---

Messages are a foundational concept in RIDDL because a RIDDL model implies 
an implementation that is a message-driven system per the 
[Reactive Manifesto](https://reactivemanifesto.org). Messages in RIDDL are 
a special case of an [aggregate type]({{< relref "type.md#aggregation" >}}) 
and the _lingua franca_ of many RIDDL definitions. They define the API for:
* [`adaptor`s]({{< relref "adaptor.md" >}})
* [`application`s]({{< relref "adaptor.md" >}})
* [`context`s]({{< relref "context.md" >}})
* [`entity`s]({{< relref "entity.md" >}})
* [`processor`s]({{< relref "processor.md" >}})
* [`projector`s]({{< relref "projector.md" >}})
* and [`repository`s]({{< relref "repository.md" >}})

* That is, these are the fundamental building blocks of a
[message-driven system](https://developer.lightbend.com/docs/akka-platform-guide/concepts/message-driven-event-driven.html)

## Differences Between Kinds of Messages
RIDDL follows
[Bertrand Meyer's](https://www.linkedin.com/in/bertrandmeyer/) notion of
[command/query separation](https://en.wikipedia.org/wiki/Command%E2%80%93query_separation)
which states, in the context of object-oriented programming in Eiffel that:
> every method should either be a command that performs an action, or a query
> that returns data to the caller, but not both. In other words, asking a
> question should not change the answer.[^1]

Consequently, RIDDL adheres to this principal and employs the notion in message
definitions since RIDDL is message-oriented not object-oriented. However, 
RIDDL also includes message types for the responses from commands and 
queries, events and results, respectively. The following subsections provide 
definitions of these four things

[^1]: Meyer, Bertrand. "Eiffel: a language for software engineering". p. 22


### Command
A directive to perform an action, likely resulting in a change to some 
information.

### Event
A recordable fact resulting from a change to some information.

### Query
A request for information from something

### Result
A response to a query containing the information sought. 

### Summary
The various characteristics of the four kinds of messages are shown in the 
table below.

|  Kind   | Request? | Response? | Cancellable? |     Relationship      |
|:-------:|:--------:|:---------:|:------------:|:---------------------:|
| Command |   Yes    |    No     |     Yes      |      Independent      |
|  Event  |  Maybe   |    Yes    |      No      | Consequent Of Command |
|  Query  |   Yes    |    No     |     Yes      |      Independent      |
| Result  |    No    |    Yes    |      No      | Consequent Of Query   |

The truth table above helps you understand the relationship between the kind of
message and how it is handled by a model component. The sections below get 
even more specific.

#### How Messages Are Handled By Adaptors
|  Kind   | In Regard To Handling By Adaptor    |
|:-------:|:-----------------------------------:|
| Command |   Intent To Translate For Context   |
|  Event  |   Intent To Translate For Context   |
|  Query  |   Intent to Translate For Context   |
| Result  |   Intent to Translate For Context   |

#### How Messages Are Handled By Applications
|  Kind   |  In Regard To Handling By Application  |
|:-------:|:--------------------------------------:|
| Command |  Data given from user to application   |
|  Event  |             Not Applicable             |
|  Query  |             Not Applicable             |
| Result  | Data provided to user from application |

#### How Messages Are Handled By Contexts
|  Kind   |  In Regard To Handling By Context     |
|:-------:|:-------------------------------------:|
| Command | Intent To Take Some Stateless Action  |
|  Event  | Notification That a Command Completed |
|  Query  |    Intent to Read From The Context    |
| Result  |    Result Of Reading From Context     |

#### How Messages Are Handled By Entities
|  Kind   | In Regard To Handling By  Entity |   
|:-------:|:--------------------------------:|
| Command |  Intent To Modify Entity State   |
|  Event  |     Entity State Was Changed     |
|  Query  |   Intent To Read Entity state    |
| Result  |       Consequent Of Query        |

#### How Messages Are Handled By Processors
|  Kind   | In Regard To Handling By Processor  |
|:-------:|:-----------------------------------:|
| Command |  Intent To Update Projection State  |
|  Event  | The Projection's State Was Modified |
|  Query  |   Intent to Read Projection State   |
| Result  | Result Of Reading Projection State  |

#### How Messages Are Handled By Projections
|  Kind   | In Regard To Handling By Projection |
|:-------:|:-----------------------------------:|
| Command |  Intent To Update Projection State  |
|  Event  | The Projection's State Was Modified |
|  Query  |   Intent to Read Projection State   |
| Result  | Result Of Reading Projection State  |


## Occurs In
All [Vital Definitions]({{< relref vital.md >}})

## Contains
[Fields]({{< relref field.md >}})
