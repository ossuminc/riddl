---
title: "Sagas"
draft: false
---

A Saga is a distributed persistent transaction that uses the
[Saga Pattern](https://microservices.io/patterns/data/saga.html). Sagas are 
used to coordinate state changes across multiple components (typically 
entities) in a system. Every change (action) has a *compensating action* to 
undo the action. This permits an organized rollback if one component cannot 
proceed with the transaction.

## Occurs In
* [Contexts]({{< relref "context.md" >}})

## Contains
* [SagaStep]({{< relref "sagastep.md">}})
