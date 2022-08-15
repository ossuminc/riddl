---
title: "APIs"
type: "page"
weight: 20
draft: "false"
---

An `api` definition represents a stateless application programming interface.
RIDDL supports this definition in domains and bounded contexts. This permits 
higher level functional gateways to summarize the behavior of entire 
subdomains and bounded contexts.  

## Always Stateless
APIs are always stateless.  Any state that needs to be saved when an 
API function is invoked should be done by sending commands to an entity from 
an API function.

## Functions
APIs are collections of functions. 
TBD
