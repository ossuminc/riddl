---
title: "Guides"
date: "2021-12-01T15:34:22-05:00"
draft: "false"
weight: 10
---

Before reading any of the guides in this section, please consider reading 
the [conceptual overview of RIDDL]({{< relref "../concepts" >}}) as a
prerequisite.

There are four kinds of readers of this documentation based on the intent of
the reader. Accordingly, the documentation has a guide for each kind of
reader:

<div style="background-color: darkslateblue; text-align: center; padding-left: 3px">
{{< columns >}}
{{< button size="large" relref="domain-experts" >}}Expert's Guide {{< /button >}}
For knowledge domain experts who would provide concepts, structure, and event 
flows to RIDDL.
<--->
{{< button size="large" relref="authors" >}}Author's Guide {{<  /button >}}
For those who will write RIDDL specifications and must know the language 
intricacies for full and accurate specifications.
<--->
{{< button size="large" relref="implementors" >}}Implementor's Guide{{< /button >}}
For those who will implement software systems based on RIDDL specifications and
the output of RIDDL based tools.
<--->
{{< button size="large" relref="developers" >}}Developer's Guide {{< /button >}}
For those who will work on the RIDDL software itself or help others with their
use of it.
{{< /columns >}}
</div>

## Next Steps
To explore the guides, click on one of the buttons above. Further descriptions
of the role-based guides are in the sections below
[Domain Experts](#domain-experts), [Authors](#authors), 
[Implementors](#implementors), and [Developers](#developers)

Alternatively, for those that want to learn incrementally, there is a full
[Tutorial]({{< relref "../tutorial" >}}). Or,you could also explore RIDDL
from its fundamental concepts in the [Concepts]({{< relref "../concepts" >}}) 
section, one concept at a time.



### Domain Experts
Domain experts (DEs) are subject matter experts (SMEs) in some knowledge 
or problem domain. They are the authority on the language used to describe 
both problems and solutions within their domain or field of expertise. These
individuals are recognized authorities, and they influence an organization's
product, service or strategic direction. [Read.]({{< relref "domain-experts" >}}) 

### Authors
Authors are the individuals who create system specifications. Such work is
greatly aided by using a clear, concise, precise, and intuitive language
in which to specify a system. That language is RIDDL. Authors form the
bridge between Domain Experts and Implementors. [Read.]({{< relref "authors">}})

### Implementors
Implementors are the technical experts who implement the system defined in a
RIDDL specification. These people are most often Software, QA, and DevOps
Engineers. Of course, throughout implementation they will be supported by the
Author(s), Domain Experts, and other related staff. Implementors use the output
from the RIDDL tools to aid in their comprehension and coding of a solution
that corresponds to the RIDDL system specification or model.
[Read.]({{< relref "implementors">}})

### Developers
Developers are the technical experts that advance RIDDL's state of the art. They
develop the tooling and documentation that makes up RIDDL. Since RIDDL is an 
open source project, developers can be affiliated with many organizations, 
presumably the organizations that derive utility from RIDDL.
[Read.]({{< relref "developers">}})
