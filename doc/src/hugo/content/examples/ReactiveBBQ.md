---
title: "Reactive BBQ"
type: "page"
draft: "false"
weight: 30
---


{{% code file=examples/rbbq/ReactiveBBQ.riddl language=riddl %}}

Everything in RIDDL revolves around creating domains and subdomains. These
are logical groupings of definitions that *belong* together, presumably
because they mimic and organizations structure or some other logical, real
world groupings. Domains can be nested.

At this top level of definition we can see that a single `domain` 
named `ReactiveBBQ` represents the entire enterprise. The details of that 
top level `domain` is abstracted away via three `include` statements within 
its body, one for each of the subdomains: 
* [`Restaurant`](restaurant)
* [`Back Office`](backoffice)
* [`Corporate`](corporate)


