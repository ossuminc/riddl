---
title: "Includes"
type: "page"
draft: "false"
weight: 40 
---

An `include` statement is not a RIDDL definition but an instruction to the 
compiler to lexically replace the include statement with the content of 
another file. The name of the file to include is the statement's only 
parameter, like this:
```riddl
include "other-file.riddl"
```

{{% notice note %}}
The include statement is only permitted where major definitions are expected,
specifically at root level, and in the bodies of `domain`, `context`, `entity`, 
and `pipe` definitions.
{{% /notice %}}
