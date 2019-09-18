# idddl

## Introduction
IDDDL, the Interface for Domain Driven Design Language, is a tool for
eliminating the boilerplate from reactive systems designed with DDD.   
It uses a DDD inspired specification language to allow developers to 
work at a higher level of specification than they would if they
were coding directly in a programming language. It aims to relieve developers
of the burden of maintaining infrastructural code through evolution of the
domain abstractions.

IDDDL is one of the Ossum tools. Ossum is a collection of tools for making
awesome software systems based on reactive architecture.

For more details, please read the paradox documentation:

* `sbt paradox`
* `open target/paradox/html/main/index.html`

## Goals
This project is currently nascent. It doesn't do anything yet, but eventually
we hope it will do all of the following:

* Generate Swagger (OpenAPI) YAML files containing API documentation for 
 REST APIs
* Generate Lagom based microservices to implement bounded contexts
* Generate Cloudstate.io based microservices to implement bounded contexts
* Generate Akka/HTTP server stubs
* Generate Akka/HTTP client library
* Generate Kafka server stubs
* Generate Kafka client library
* Generate Scala.JS based browser client side
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
* Potential executors: Lagom microservices, Lightbend Pipelines, Cloudstate.io
* Incorporate the best interface language ideas from CORBA, Reactive
 Architecture, DDD, REST, DCOM, gRPC, etc. 
* Support for Read Projections and Read Models (plugins for databases)
* Support for graphQL and gRPC
