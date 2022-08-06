---
title: "RIDDL Outputs"
date: 2022-02-25T10:07:32-07:00
draft: true
weight: 30
---

RIDDL sources produce two kinds of output:
* **Specification:** Specification outputs are the "requirements" of the domain model. Domain experts spend most of their time here - adding detail, reviewing for completeness/correctness, and establishing the domain model - which includes defining a ubiquitous language. Most of this work is done by reviewing the web site generated from RIDDL sources. Beyond textual definitions and descriptions of Domains, Contexts, Entities, Plants, Functions, etc. specification outputs may also include wireframe mockups, diagrams of various types that add clarity to the definition and interactions within the domain, for example, Context Maps, Flow Charts, Sequence Diagrams, State Machines, Entity Relationships. 

    When the delivery team begins building, the system specification outputs become their guide posts as they build. Undoubtedly, the implementation team will need to engage with domain experts to get additional information and clarity as they develop. Specification outputs provide the baseline from which these conversations happen. The insights gained should be captured in the RIDDL file and new specification outputs generated to reflect these learnings. 
* **Implementation:** Implementation outputs are intended to accelerate the efforts of the delivery team. As such, these outputs tend to be more technical in nature. Examples of these outputs would include:
    * Scala/Akka code stubs including actor definitions, value objects, message definitions (case classes), and so on.
    * Test cases derived from user stories in the RIDDL spec.
    * [Protobuf (Protocol Buffer)](https://developers.google.com/protocol-buffers) definitions. 
    * [Open API Specifications (AKA Swagger)](https://swagger.io/specification/)
    * Initial build.sbt
    * Initial GIT project and structure
    * Boilerplate CI/CD Definitions for implementation artifacts
    * Boilerplate infrastructure code to deploy both specification outputs and generated system sources