---
title: "Root"
type: "page"
draft: "false"
weight: 30
---


The Root level of the definitional hierarchy is the top most concept in the 
language but there is no syntax to define a "Root". Instead, you just create
definitions in a file. The "Root" concept is identical to the file content. 

At the Root (file) level you can only do two things: 
* include definitions from another file, 
* define a top level domain

For example:
```riddl
include "other-file"

domain foo is {
  ???
}
```

In this example, we have included all the definitions from "other-file" but they
can only be `domain` definitions.  This example also abstractly defines a `domain`
named `foo`. 

Now, you can use the tree below to navigate the definitional hierarchy. We 
recommend you start with [Domain]({{< relref "domain" >}}) and work your way 
down from there.

{{< toc-tree >}}
