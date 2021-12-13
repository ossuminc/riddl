---
title: "Adaptors"
type: page
weight: 20
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
and there are several more. To disambiguate these definitions we use bounded contexts in DDD, 
and RIDDL, that precisely define the meaning of a term in that context. But, what happens when 
two bounded contexts use the same term for different purposes? That's where Adaptors come in. 

An adaptor can only be defined as part of the definition of a bounded context. It can specify 
how the messages received from, or sent to, another bounded context should be handled. 
Consequently, there are two types of adaptors: inbound and outbound. For example, like this
```riddl
context Foo is {
  type FooEventName is event { ??? }
  type FooCommandName is command { ??? } 
}
context Bar is {
    type BarCommandName is command { ??? }
    type BarEventName is event { ??? }
    inbound adaptor FromFooContext is {
      adapt ..Foo.FooEventName as BarCommandName
    }
    outbound adaptor ToFooContext is {
      adapt BarEventName as ..Foo.FooCommandName mapping {
        barName => fooName
      }
    }
}
```


