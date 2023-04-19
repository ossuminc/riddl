---
title: "What can RIDDL do?"
date: 2022-02-25T10:07:32-07:00
draft: false
weight: 20
---

RIDDL is a software specification language. As a consequence of it being
a language, it has a software tool, `riddlc` (riddl compiler), that can be 
used to do a variety of things. Most of those things involve reading riddl 
input files, checking them, and producing some output file(s). 

## Input

The input to `riddlc` is always a single file with the `.riddl` suffix. 
This is a text file that contains definitions in the RIDDL syntax. While
the input is specified as a single file, that could be just the top level
file of a hierarchically arranged set of files that are included from 
the top level file. 

## Options

The `riddlc` program has many options but generally they control:
* where more options are coming from (e.g. config files)
* what kind of logging output should be generated (terse to verbose)
* what kind of output should be generated

## Output

The `riddlc` program can produce many kinds of outputs. Being extensible, this
list can grow. Currently implemented functionality includes:

* *Syntax Validation* - making sure that the input is syntactically correct
* *Semantic Validation* - making sure that the structure and referential 
  integrity of the input is correct. 
* *Specification Site* - construction of [`hugo`](https://gohugo.io/) input that
  can be translated into a web site to make the specification navigable and 
  readable
* *Diagrams* - generating a variety of diagrams as either PNG or mermaid input
* *Kalix* - generating the necessary Google Protobuffers input for Kalix to use to 
  generate the infrastructure for a software system. 

In the future we expect to produce translators that will also provide:
* *Akka* - generating Akka infrastructure code with a "fill in the blanks"
  approach for system design evolution. 
* *Kuberenetes* - generating Kubernetes deployment descriptors, etc. 

* RIDDL sources produce two kinds of output:
* **Specification:** Specification outputs are the "requirements" of the domain model. Domain experts spend most of their time here - adding detail, reviewing for completeness/correctness, and establishing the domain model - which includes defining a ubiquitous language. Most of this work is done by reviewing the web site generated from RIDDL sources. Beyond textual definitions and descriptions of Domains, Contexts, Entities, Plants, Functions, etc. specification outputs may also include wireframe mockups, diagrams of various types that add clarity to the definition and interactions within the domain, for example, Context Maps, Flow Charts, Sequence Diagrams, State Machines, Entity Relationships. 

    When the delivery team begins building, the system specification outputs become their guide posts as they build. Undoubtedly, the implementation team will need to engage with domain experts to get additional information and clarity as they develop. Specification outputs provide the baseline from which these conversations happen. The insights gained should be captured in the RIDDL file and new specification outputs generated to reflect these learnings. 
* **Implementation:** Implementation outputs are intended to accelerate the efforts of the delivery team. As such, these outputs tend to be more technical in nature. Examples of these outputs would include:
    * Scala/Akka code stubs including user definitions, value objects, message definitions (case classes), and so on.
    * Test cases derived from user stories in the RIDDL spec.
    * [Protobuf (Protocol Buffer)](https://developers.google.com/protocol-buffers) definitions. 
    * [Open API Specifications (AKA Swagger)](https://swagger.io/specification/)
    * Initial build.sbt
    * Initial GIT project and structure
    * Boilerplate CI/CD Definitions for implementation artifacts
    * Boilerplate infrastructure code to deploy both specification outputs and generated system sources
