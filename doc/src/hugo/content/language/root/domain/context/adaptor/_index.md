---
title: "Adaptors"
type: "page"
weight: 20 
draft: "false"
---
Adaptors are translators between contexts and (sub-)domains. 
DDD calls these "anti-corruption layers" which we find awkward, hence we
renamed them as _adaptors_.  However, the name DDD chose is apt.  Adaptors aim to 
solve a language problem that often exists in large domain modelling exercises: 
conflation and overloading of terms.

For example, consider the word "order" in various contexts:
* _military_ - a directive or command from a superior to a subordinate
* _restaurant_ - a list of items to be purchased and delivered to a table
* _mathematics_ - A sequence or arrangement of successive things.
* _sociology_ - A group of people united in a formal way
* _architecture_ -  A type of column and entablature forming the unit of a style

And there are several more. To disambiguate these definitions we use bounded contexts in DDD, 
and RIDDL, that precisely define the meaning of a term in that context. But, what happens when 
two bounded contexts use the same term for different purposes? That's where Adaptors come in. 

## Use Cases
There are several use cases in which the need for an Adaptor occurs as in the following subsections

### Non-DDD External Systems
An adaptor can adapt a non-DDD external system to a DDD system. The DDD system can then interact
with the non-DDD system using messages as if it was a DDD system by simply interacting with the 
corresponding Adaptor.  
[See this stackoverflow article for more on this.](https://stackoverflow.com/questions/909264/ddd-anti-corruption-layer-how-to)

### Reactions
Some entities need to react to the occurrence of events from other bounded contexts. These 
adaptations are often handled directly by an entity handler but sometimes the model needs to
specify reactions at the bounded context level where they can be converted into commands on 
the appropriate entities. 

### Versioning
Bounded contexts and their entities undergo version changes and those changes can affect the 
structure and composition of the messages used.  To support smooth transitions between versions, 
the software needs to support older versions of messages seamlessly. This is handled in
RIDDL by adapting the old messages to the new messages, even if that means ignoring them.

## Defining Adaptors
An adaptor can only be defined as part of the definition of a bounded context. It can specify 
how to handle the messages received from another bounded context, or even from a 
[pipe](../../streaming/pipe). 
For example, we adapt context `Bar` below to the event from context `Foo`
```riddl
context Foo is {
  type FooEventName is event { ??? }
}
context Bar is {
    type BarCommandName is command { ??? }
    adaptor FromFooContext for Foo is {
      adapt FooEventName to BarCommandName as {
        given "entity"
      }
    }
}
```


