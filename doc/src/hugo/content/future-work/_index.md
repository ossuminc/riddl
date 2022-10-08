---
title: "Future Work"
description: "Plans for things to do with RIDDL"
draft: "false"
weight: 90

---

This section provides some insight into our plans to extend RIDDL or use it in
novel ways:

* Akka source generation
* Kubernetes deployment descriptors
* Protobuffers message and service definitions
* Fast data message streaming infrastructure


* [_Analyses_](analyses) - Produce analytical data structures from the AST for 
  use by translators. 
* [_BAST_](bast) - Binary Abstract Syntax Tree. Generates files that capture the
  abstract syntax tree (AST) in a transportable binary format
* [_Diagrams_](diagrams) - Data flow, sequence, entity, context maps, and other
* [_OpenAPI_](openapi) - OpenAPI (formerly called Swagger) specifications for APIs
  implied in the RIDDL model
* [_Kalix_](kalix) - Kalix protobuffers source generation as input to the Kalix
  code generator.
* [_Akka_](akka) - Akka, protobuffers, Alpakka infrastructure code & skeleton
* [_Others_](others) - plugins can be written to convert the AST into any other
  kind of needed data

## Goals
This project is currently nascent. It doesn't do anything yet, but eventually
we hope it will do all the following things:

## From old README ...
* Generate Kalix Protobuffers
* Generate Swagger (OpenAPI) YAML files containing API documentation for
  REST APIs
* Generate Akka Serverless based microservices to implement bounded contexts
* Generate Akka/HTTP server stubs
* Generate Akka/HTTP client library
* Generate Kafka server stubs
* Generate Kafka client library
* Generate graphQL based on domain model
* Supporting a SaaS system for the generation of the above items working
  something like https://www.websequencediagrams.com/ by allowing direct
  typing and immediate feedback
* Serve as the de facto (or real) standard for defining business domains and
  reactive systems.
* Be designed to be used with event storming
* Designed for a fully reactive implementation with messaging between
  contexts
* Support pluggable code generators for targeting different execution
  environments.
* Potential executors:  Akka Data Pipelines, Akka Serverless, Akka/Scala
* Incorporate the best interface language ideas from CORBA, Reactive
  Architecture, DDD, REST, DCOM, gRPC, etc.
* Support for Read Projections and Read Models (plugins for databases)
* Support for graphQL and gRPC