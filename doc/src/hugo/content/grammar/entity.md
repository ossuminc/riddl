---
title: "Entity"
weight: 50
draft: false
----
{{% code file=grammar/ebnf/entity.ebnf language=ebnf %}}

### State
{{% panel theme="success" header="State Definition" %}}
A state is defined with the `state` keyword in the content of an `entity`
using this syntax:
```ebnf
state = "state", identifier, "is", typeExpression, description
```
For details see the following production descriptions
{{% /panel %}}
