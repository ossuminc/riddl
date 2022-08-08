---
title: "Translation"
description: "Translating RIDDL input to other forms"
type: "page"
draft: "false"
weight: 50
---

The RIDDL compiler, `riddlc` is able to translate RIDDL into a variety of other
document types, after the input passes the [compilation phases](../compilation).  

{{% hint ok %}}
It is recommended that before you delve into the types of output, become 
familiar with the `riddlc` [command line and configuration options](options)
{{% /hint %}}

The various kinds of output from `riddlc` are described in the following sections:

* [_BAST_](bast) - Binary Abstract Syntax Tree
* [_Akka_](akka) - Akka, protobuffers, Alpakka infrastructure code & skeleton
* [_Hugo_](hugo) - Hugo source input for producing HTML web site
* [_OpenAPI_](openapi) - OpenAPI (nee Swagger) specifications 

# Translation
A RIDDL AST, having been successfully analyzed for structure and validity, is
ready to be translated into another form, which is the point of all this
bother in the first place.

RIDDL supports translation to:
* [Hugo Websites](https://gohugo.io/) - Complete documentation of the RIDDL
  model with structural diagrams to facilitate rapid comprehension.
* [Kalix](https://kalix.io/) - Protobuffers input to the Kalix code generator
* [Akka](https://akka.io/) - An implementation of the model.
* Anything else you like since RIDDL code generators are extensible. 
