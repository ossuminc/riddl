---
title: "Queries"
---

# Introduction

Queries are ways to inspect the state of the system. 
They do not cause changes in the system. 
They do not trigger Commands or Events.
For example, a user queries reservation context to get his/her reservation status.

Some queries may call not only a single service but also multiple services.
In that case, the API gateway may be useful to aggregate the multiple query results.
