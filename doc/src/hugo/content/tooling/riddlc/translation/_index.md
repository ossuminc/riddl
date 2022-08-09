---
title: "Translation"
description: "Translating RIDDL input to other forms"
type: "page"
draft: "false"
weight: 50
---

The RIDDL compiler, `riddlc` is able to translate RIDDL into a variety of other
document types, after the input passes the [compilation phases](../compilation).  

The various kinds of output from `riddlc` are described in the following 
sections:

* [_BAST_](bast) - Binary Abstract Syntax Tree. Generates files that capture the 
  abstract syntax tree (AST) in a transportable binary format
* [_Diagrams_](diagrams) - Data flow, sequence, entity, context maps, and other
* [_Hugo_](hugo) - Hugo source input for producing an HTML website
  diagrams are all automatically deduced from the RIDDL model
* [_OpenAPI_](openapi) - OpenAPI (formerly called Swagger) specifications for APIs 
  implied in the RIDDL model
* [_Kalix_](kalix) - Kalix protobuffers source generation as input to the Kalix
  code generator.
* [_Akka_](akka) - Akka, protobuffers, Alpakka infrastructure code & skeleton
* [_Others_](others) - plugins can be written to convert the AST into any other
  kind of needed data

{{% hint ok %}}
It is recommended that you become familiar with the `riddlc` [command line and configuration options](options) as these control the kind of output generated.
{{% /hint %}}
