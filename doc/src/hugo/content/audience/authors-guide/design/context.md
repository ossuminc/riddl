---
title: "Context"
description: "Using Contexts To Isolate Language"
date: 2022-02-25T10:50:32-07:00
draft: true
weight: 30
---

## Introduction To Bounded Contexts
DDD defines the notion of a [*bounded context*](https://www.martinfowler.com/bliki/BoundedContext.html)
which is a portion of a domain that has a well-defined and finite boundary, hence
the name, *bounded context*.  DDD uses *bounded contexts* to
divide complexity in large knowledge domains into manageable portions. When
the knowledge domain is large enough to be past a single human's comprehension
in its entirety, bounded context become a primary model structuring principle.

A bounded context defines its boundary via *ubiquitous language* which
facilitates a common comprehension of the bounded (finite) context (conceptual
area) amongst humans. Indeed, this is one of its primary reasons for its 
existence: to assist in eliminating the confusion and miscommunication of 
imprecise conceptualizations that human languages produce. 

For example, consider the word "order" in various contexts:

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

The notion of *ubiquitous language* means concise and specific words used with
precision by the subject matter experts or artisans of a given bounded context.
When modelling a sytem with RIDDL, the *ubiquitous language* boils down to 
just three kinds of definitions:
* named data types 
* named definitions of messages
* how those messages are handled

Bounded contexts are not isolated from other parts of a system model but do 
isolate the content (state, business logic, process) of the bounded context
behind its ubiquitous language. You can correctly think of that ubiquitous
language as the interface (composed of messages) to the bounded context, much
as an API (Application Programming Interface) is the interface to a program.

## Adaptation
When we have language confusion as described above, DDD provides an idea to 
eliminate the confusion called Anti-Corruption Layers (ACL).  RIDDL isn't a big
fan of that term, so we call them Adaptors instead. Why? Because the entire
purpose of an ACL is to adapt one bounded context to another for the purpose 
of not corrupting the ubiquitous languages of those bounded contexts. Plus, it
is a confusion itself, the acronym ACL referring to a ligament in the knee. 

Adaptors are specifications of how to translate messages coming from (or to) a
bounded context. Using adaptors limits the surface area of system design that 
must know about two, or more, bounded contexts at the same time. Adaptors 
translate concepts from one form to another.

For example, going back to the "order" conundrum above, consider what happens
in a restaurant.  Consider what an "order" might look like in various contexts:

| Context    | Aspects of Order Important To Context                |
|------------|------------------------------------------------------|
| Server     | Food and Drink Items, Table #, Seat #, Name          |
| Customer   | Price of items and total cost                        |
| Kitchen    | Food items to prepare, prices not needed             |
| Bar        | Drink items to prepare, prices not needed            |
| Accounting | Total price, Loyalty points awarded, form of payment |

So, an Adaptor for the concept of "Order" from a Server context to a 
Kitchen context might cause the drink items to be removed and the
food prices dropped. 

## RIDDL Contexts

In RIDDL, we use DDD's notion of bounded context when we use a `context` 
definition. A `context` can be used to:
* define terms/words precisely
* define an API through the definition of messages and their handlers
* define a variety of entities
* define adaptors to/from other contexts
* define sagas of interaction between the context and others 

