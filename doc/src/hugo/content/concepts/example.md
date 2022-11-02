---
title: "Example"
draft: false
---

The concept for a RIDDL Examples is very similar to
[Dan North's]({{< relref "../introduction/who-made-riddl-possible.md#dan-north">}})
[Gherkin Examples](https://cucumber.io/docs/gherkin/). Gherkin has been used 
for many years to specify test cases that serve as functional 
specifications as well. Gherkin is simple enough for anyone to understand 
them.  

In RIDDL this idea is used to specify the functionality for any of the 
[vital definitions]({{< relref "vital.md" >}}). An Example is structured 
like this:
* SCENARIO *identifier* - provides the name of the example or scenario 
  (optional)
* GIVEN [condition]({{< relref "expression#condition" >}}) (optional)
* WHEN [condition]({{< relref "expression#condition" >}}) (optional)
* THEN [action]({{< relref "action" >}})
* ELSE [action]({{< relref "action" >}}) (optional)

The intent here is to express how in the context of some circumstance (GIVEN), 
when a particular thing happens (WHEN), take some action (THEN), otherwise, in
that context, if that thing does not happen take some other action (ELSE). 

Since four of those things are optional, it often just comes down to THEN. 

## Occurs In
* [Functions]({{< relref "function.md" >}}),
* [Handlers]({{< relref "handler.md" >}}),
* [SagaSteps]({{< relref "sagastep.md" >}}),
* [Stories]({{< relref "story.md" >}}).


## Contains
* [Actions]({{< relref "action.md" >}})
* [Conditional Expressions]({{< relref "expression.md" >}})
