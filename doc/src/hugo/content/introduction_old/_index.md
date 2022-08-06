---
title: "Introduction"
summary: "An introduction to the RIDDL language and tools"
type: "page"
date: "2021-12-01T15:34:22-05:00"
draft: "false"
weight: 1
creatordisplayname: "Reid Spencer"
creatoremail: "reid@reactific.com"
---

RIDDL is a specification language for large systems using concepts from domain 
driven design and reactive system architecture. It aims to capture  business level 
concepts in a way that can be directly translated into software that 
implements the scaffolding for those business concepts; leaving programmers 
to augment that scaffolding with the business logic.

### Based On DDD 
RIDDL is based on the concepts from 
[Domain Driven Design (DDD)](https://en.wikipedia.org/wiki/Domain-driven_design). This allows 
domain experts and software developers to work at a higher level of abstraction using a 
ubiquitous language to develop a system specification that is familiar and comprehensible by 
business and technology leaders alike.    

For best comprehension of the language, it is best to be familiar with the
DDD concepts. For a four-minute overview,
[watch this video](https://elearn.domainlanguage.com/). For a more in depth
understanding, we recommend reading Vaughn Vernon's more concise book
**[Domain Driven Design Distilled](https://www.amazon.com/Domain-Driven-Design-Distilled-Vaughn-Vernon-ebook/dp/B01JJSGE5S/ref=sr_1_1)**
or Eric Evans' original tome
**[Domain Driven Design](https://www.amazon.com/Domain-Driven-Design-Tackling-Complexity-Software-ebook/dp/B00794TAUG/ref=pd_sim_351_2/139-5372630-4190805)**.

RIDDL relieves developers of the need to write redundant, boilerplate code for micro-service 
implementations due to its code generation features. The DDD-inspired specification language 
allows domain experts and developers to work at higher levels of abstraction and specification 
than they would if they were coding directly in a programming language. RIDDL aims to relieve developers
of the burden of maintaining infrastructural code through the evolution of the
domain model. It also aims to aid the domain expert with a rigorous  but simple language to use 
for a specification.

### Code Generation
RIDDL has its own compiler, `riddlc`, which can compile specifications written in RIDDL syntax
into many kinds of output:
* `.bast` - generates files that capture the abstract syntax tree (AST) in a transportable binary
  format
* `docs` - generates a Hugo based website for sharing the model with coworkers in their browser
* `diagrams` - data flow, sequence, entity, context maps, and other diagrams all automatically 
  deduced from the code
* `api` - OpenAPI (formerly called Swagger) specifications for APIs implied in the model
* `code` - generates Scala/Akka code for the framework of the system with clean separation for
  the parts that a programmer must implement.
* `others` - plugins can be written to convert the AST into any other kind of data

The compiler also performs AST syntax checking, semantic validation, and statistical summaries.
Together, these tools make a compelling offering for rapidly capturing business models,
validating the semantics of those models, and speeding up the development process to deliver a
system based on the model.

