---
title: "Akka"
type: "page"
weight: 60
draft: "false"
---

The goal of the Akka translator is to generate all the infrastructure code 
needed to support the domain(s) defined in the RIDDL input. While this does 
produce a working system, it also relieves developers of much of the 
repetitive, detail oriented infrastructure that is relatively boring 
compared to the business logic of the system. 

This translator recognizes that its output will be co-mingled with the 
business logic output that developers are writing and keeps them separate. 
When an expected developer file does not exist, it will create it, but 
otherwise uses Scala inheritance, or other mechanisms, to indicate the 
portions that ought to be written. 

## Output File Types
The Akka translator doesn't just generate Scala code. It aims to generate 
complete projects that should be familiar to those famliar with Scala and 
Akka projects. This means generating a mixture of file types, as described 
in the following sections
### [sbt build files](https://scala-sbt.org)
build.sbt
project/xxx.scala
project/build.properties
project/plugins.sbt

### [protobuffers definitions](https://developers.google.com/protocol-buffers/docs/proto3)

Specifically processed by akka-grpc (via scalapb via protoc plugins)

### [Scala code](https://scala-lang.org)

## Build Time Dependencies
* sbt 1.6 or later
* sbt-riddl plugin
* akka-grpc sbt plugin
* buildinfo sbt plugin

## Runtime Dependencies
* Akka 2.6.17 (or later) including streams, http, cluster, persistence, ...
* Alpakka 3.x
* Kafka  

## Comparison To Akka Serverless
The Akka Translator has an objective to support both the advanced Akka Scala 
developer and less knowledgeable developers. It does that by using Scala's 
abstraction features but without completely disconnecting from Akka's 
powerful features.  For example:
* Actors are used, but in a fill-in-the-blank way. Advanced programmers can 
  still do more complicated things, neophytes will find it easy.
* Does not use GRPC as the interface like Akka Serveless does. Scala is the 
  interface and there is no side-car aspect in the runtime.

