---
title: "Explanation"
type: page
weight: 20
---

## The Code
Here's the entire listing of the code for the Reactive BBQ
example for your perusal. The rest of this section will pull
this code apart and explain what it means.

@@snip [rbbq.idddl](/language/src/test/input/rbbq.riddl) { #everything }

## Domains
Everything in RIDDL revolves around creating domains and sub-domains. These
are logical groupings of definitions that *belong* together, presumably
because they mimic and organizations structure or some other logical, real
world groupings. Domains can be nested. 

@@snip [rbbq.idddl](/language/src/test/input/rbbq.riddl) { #domains }
