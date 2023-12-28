---
title: "Relating To Riddl -- Domain Expert's Guide"
description: "How a domain expert should think of RIDDL"
date: 2022-08-06T10:50:32-07:00
draft: false
weight: 10
---

Understanding RIDDL is a complex domain on its own. RIDDL
uses a variety of very abstract concepts that are adept at
describing the design and architecture of computing systems. 
This section helps you understand 

It is up to the [authors]({{< relref "../authors" >}})
working with you to translate your expertise into a 
system model that uses RIDDL. You could do that even if they
weren't using RIDDL. However, since you're reading this, 
we'll assume they are using RIDDL. Consequently, it
will be beneficial to you and your project to be informed
of the essential concepts in RIDDL. The subsections that
follow will provide that for you.

{{< toc >}}

### Message Passing Systems
The essential tenet of a large distributed system is the
passing of messages between system components. Unlike 
RPCs, APIs or function calls, messages are, essentially, 
the codification of invocations of behavior on the 
component to which they are sent. Even a user clicking
a button implicitly delivers a "you have been clicked"
message to the button. RIDDL models these things directly. 

### Processor
A processor in RIDDL, is the definition of an abstract
component 

### Message

### Effects (Statements)

### Domain

### Context

## Concepts
While you could spend a few days wading through the 
scores of entries in the [Concepts]({{< relref "../../concepts" >}}) 
section, you don't really need all that complexity. There are
only just a few basic ideas you need to understand:



