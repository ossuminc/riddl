---
title: "Examples"
type: "page"
weight: 30
draft: "false"
---

RIDDL uses Gherkin examples in various places to express a functionality or behavior requirement on
the definition that uses it. Gherkin examples are used in these places:

* [Adaptors]({{< relref "../root/domain/context/adaptor" >}})
* [Handlers]({{< relref "../root/domain/context/entity/handler.md" >}})
* [Functions]({{< relref "./functions.md" >}})
* [Processors]({{< relref "../root/domain/streaming/processor.md" >}})
* [Projections]({{< relref "../root/domain/context/projection" >}})
* [Saga Actions]({{< relref "../root/domain/context/saga" >}})
* [Epic]({{< relref "../root/domain/epic" >}})

## Structure

[Gherkin](https://cucumber.io/docs/gherkin/) is a language developed by
[SmartBear Software](https://smartbear.com/company/about-us/), a vendor of software quality tools,
for the [cucumber](https://cucumber.io/) testing system. RIDDL uses a subset of the language as
SmartBear has defined it. Four constructs are used in RIDDL:

* _GIVEN_ - A description of the scenario, environment, or setting of the example
* _WHEN_ - A condition that must be true for this example to be applicable
* _THEN_ - An action, or set of actions, that are to be performed
* _BUT_ - An action, or set of actions, that are not to be performed

## Example

```riddl
example AllDone is {
  Given "I am out shopping"
  And "I have eggs"
  And "I have milk"
  And "I have butter"
  When "I check my list"
  Then "I don't need anything"
}
```
