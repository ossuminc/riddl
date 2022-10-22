---
title: "Example"
draft: false
---

The concept for a RIDDL Examples is very similar to
[Dan North]({{< relref "../introduction/who-made-riddl-possible.md#dan-north">}})'s
[Gherkin Examples](https://cucumber.io/docs/gherkin/). Gherkin has been used 
for many years to specify test cases that service a functional 
specifications as well. Gherkin is simple enough for anyone to understand 
them.  

In RIDDL this idea is used to specify the functionality for any of the 
[vital definitions]({{< relref "vital.md" >}}). An Example is structured 
like this:
* GIVEN [condition]({{< relref "expression#condition" >}}) (optional)
* WHEN [condition]({{< relref "expression#condition" >}}) (optional)
* THEN [action]({{< relref "action" >}})
* ELSE [action]({{< relref "action" >}}) (optional)

The intent here is to express how in some circumstance (GIVEN), when a 
particular thing happens (WHEN), take some action (THEN), otherwise in that 
circumstance if that thing does not happen take some other actions (ELSE). 

Since three of those things are optional, it often just comes down to THEN. 

## Occurs In
All [vital definitions]({{< relref "vital.md" >}})

## Contains
* [Conditional Expressions]({{< relref "expression.md" >}})
* [Actions]({{< relref "action.md" >}})
