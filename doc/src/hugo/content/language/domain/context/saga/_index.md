---
title: "Saga"
type: page
weight: 23
---

## Introduction
A `Saga` defines a way for an external application to invoke a distributed 
transaction via the 
[Saga Pattern](https://microservices.io/patterns/data/saga.html). Sagas are 
necessary in distributed services that use the 
[Database per Service Pattern](), like RIDDL. The Saga Pattern definition 
describes the context in which Sagas are used:
> You have applied the Database per Service pattern. Each service has its own
> database. Some business transactions, however, span multiple service so you
> need a mechanism to implement transactions that span services. For example,
> let’s imagine that you are building an e-commerce store where customers have
> a credit limit. The application must ensure that a new order will not exceed
> the customer’s credit limit. Since Orders and Customers are in different
> databases owned by different services the application cannot simply use
> a local ACID transaction.

The goal of a saga is to define a function across multiple entities that 
must atomically succeed or fail with no state change. So, a saga defines a set
of commands to send to incur changes on entities, and a set of commands to 
undo those changes in case it cannot be atomically completed.

Sagas are very like functions but they are 

## Example
```riddl
saga AllOrNothing is {
  action "foo" is <command> to entity thingie reverted by <type>
  action "bar" is <type> to entity thunkie reverted by <type>   
 }
```
defines an api named MyNewAPI that can only be used as a saga. 
