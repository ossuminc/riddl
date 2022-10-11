---
title: "Dependencies"
draft: false
weight: 80
---

## Dependencies

The `riddl` code base targets Java 17 and Scala 2.13.7 with -XSource:3 in 
preparation for Scala 3.0 code conversion. Moving to Scala 3 requires all 
dependencies to make the same transition:
* fastparse uses macros and is waiting for bugs in scala 3 to be fixed
* pureconfig is nearly ready for scala 3



