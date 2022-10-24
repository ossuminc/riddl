---
title: "Context"
draft: false
---

A `context` definition in RIDDL represents the notion of a 
[*bounded context*](https://www.martinfowler.com/bliki/BoundedContext.html) 
from 
[Domain Driven Design (DDD)](https://martinfowler.com/bliki/DomainDrivenDesign.html).
A bounded context is an isolated portion of some knowledge domain. 
Consequently, in RIDDL we defined a [`context`]({{< relref "context.md" >}}) 
inside a [`domain`]({{< relref "domain.md" >}}). 
DDD uses *bounded contexts* to divide complexity in large knowledge domains
into manageable portions. Since large knowledge domains are difficult for a 
single human to comprehend in its entirety, DDD uses bounded contexts as a 
primary structuring tool. Bounded contexts must be able to be fully comprehended
by any individual person. RIDDL utilizes both of these concepts the way DDD 
intends.  

Since bounded contexts *are also* subdomains, we distinguish them from 
*subdomains* with a few characteristics as discussed below.

As the name suggests, a `context` has a finite and precise definitional boundary.
That means that it can be implemented easily because there is no vagueness.
A bounded context defines its boundary via a 
[*ubiquitous language*](https://martinfowler.com/bliki/UbiquitousLanguage.html#:~:text=Ubiquitous%20Language%20is%20the%20term,language%20between%20developers%20and%20users.)
that facilitates a common comprehension of the context between humans. Indeed, 
this is one of its primary reasons for its existence: to assist in eliminating
the confusion and miscommunication of imprecisely defined concepts that human
languages tend to produce, even within a single language. For example, consider
the English word "order" in various contexts:

* _restaurant_ - a list of food items to be made and delivered to a table
* _backoffice_ - a list of things to be received from a shipper
* _politics_ - a state of peace, freedom from unruly behavior, and respect for law
* _mathematics_ - a sequence or arrangement of successive things.
* _sociology_ - a group of people united in a formal way
* _society_ - a rank, class, or special group in a community or society
* _architecture_ -  a type of column and entablature forming the unit of a style
* _economics_ - a written direction to pay money to someone
* _military_ - a directive or command from a superior to a subordinate

And that's just the confusion resulting from **one** common business word!

A context's *ubiquitous language* arises from the named definitions it contains. 
The names are specific terms used with precision by the subject-matter
(knowledge domain) expert. Other practitioners (designers, developers, testers) 
must use these terms so that the language truly is ubiquitous and everyone is 
on the same page. 

When modelling a system with RIDDL, the *ubiquitous language* boils down to
the names of the definitions that RIDDL permits inside a `context` definition, as
shown in the list below. You can correctly think of a `context`s ubiquitous
language as the interface to that knowledge domain. It is very analogous to
an API (Application Programming Interface) as the interface to a program. The API

To further your understanding, watch this 
[34-minute video](https://www.youtube.com/watch?v=am-HXycfalo) by
{{< relref "entity.md" >}}
[Eric Evans]({{< relref "../../introduction/who-made-riddl-possible.md#eric-evans" >}}) 
from DDD Europe 2020 conference


## Occurs In
* [Domains]({{< relref "domain.md" >}})

## Contains
* [Adaptors]({{< relref "adaptor.md" >}})
* [Authors]({{< relref "author.md" >}})
* [Entities]({{< relref "entity.md" >}})
* [Functions]({{< relref "function.md" >}})
* [Handlers]({{< relref "handler.md" >}})
* [Includes]({{< relref "include.md" >}})
* [Processors]({{< relref "processor.md" >}})
* [Projections]({{< relref "projection.md" >}})
* [Sagas]({{< relref "saga.md" >}})
* [Terms]({{< relref "term.md" >}})
* [Types]({{< relref "type.md" >}})
