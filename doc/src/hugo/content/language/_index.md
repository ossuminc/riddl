---
title: "Language"
type: "page"
draft: "false"
weight: 30
---

This section presents the RIDDL source language syntax, one definition at a
time according to their typical arrangement. 

{{< toc >}}

## Overview
RIDDL is not a 
[Turing complete language](https://en.wikipedia.org/wiki/Turing_completeness). 
However, it is intended to be used to create systems that are Turing complete. 
Consequently, RIDDL is not a programming language, it is a system specification
language, and not only at software developers. 

We expected business managers, business analysts, knowledge domain experts,
system architects, and software developers to all be able to read and comprehend
RIDDL models with only a little training and DDD background. The language 
tries hard to be readable in English and not overly technical while still
retaining the ability to be precise, concise, and specific enough to be 
used for software source code generation.

The language is opinionated in the sense that it is intended for the 
specification of large scale distributed software systems. It does not attempt 
to be useful for every kind of computing problem nor even every kind of 
knowledge domain. Several distributed software architecture patterns have 
been adopted as natural extensions of domain driven design. Data engineering 
and user interface ideas are also included. 


## Prerequisites

We recommend that you first read the [Language Conventions](common/lang-conventions.md) 
(not documentation conventions!) to which RIDDL adheres. These conventions
are aimed at making RIDDL models consistent, simple, and free of special cases
and exceptions.

Next, there are several concepts that are used in a variety of places 
in the language. The [Common](common) section describes these concepts 
in preparation for looking at the major definitions in the language.  


## Definitional Hierarchy 
RIDDL's basic structure is a containment hierarchy of nested definitions. That 
is, definitions are defined by their contained definitions. At the root of
that definitional hierarchy is a single file known as the *root file*. The name of 
this file is given to `riddlc` to start processing a specification. 

So, we can explore and learn the RIDDL language by examining each level of the
definitional hierarchy like peeling the layers of an onion to discover what is
hidden at each level. 

Start with the [Root](root).


