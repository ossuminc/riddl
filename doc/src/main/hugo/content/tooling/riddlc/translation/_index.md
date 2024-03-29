---
title: "Translation"
description: "Translating RIDDL input to other forms"
type: "page"
draft: "false"
weight: 50
---

The RIDDL compiler, `riddlc` is able to translate RIDDL into a variety of other
document types, after the input passes the 
[compilation phases]({{< relref "../compilation.md" >}}).  

The various kinds of output from `riddlc` are described in the following 
sections:

* [_Hugo_]({{< relref "hugo.md" >}}) - Hugo source input for producing an 
  HTML website where diagrams are all automatically deduced from the RIDDL 
  model

{{% hint ok %}}
It is recommended that you become familiar with the `riddlc`command line and 
configuration [options]({{< relref "options" >}}) as these control the kind of 
output generated.
{{% /hint %}}
