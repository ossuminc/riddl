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
flows to RIDDL [authors]({{< relref "authors">}}).
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

The following sections define these roles more clearly:
{{< toc >}}

## Domain Experts
Domain experts (DEs) are subject matter experts (SMEs) in some knowledge 
or problem domain. They are the authority on the language used to describe 
both problems and solutions within their domain or field of expertise. These
individuals are recognized authorities, and they influence an organization's
product, service or strategic direction. 

## Authors

Authors are the individuals who create system specifications. Such work is
greatly aided by using a clear, concise, precise, and intuitive language
in which to specify a system. That language is RIDDL. Authors form the
bridge between Domain Experts and Implementors.


## Implementors
Implementors are the technical experts who implement the system defined in a
RIDDL specification. These people are most often Software, QA, and DevOps
Engineers. Of course, throughout implementation they will be supported by the
Author(s), Domain Experts, and other related staff. Implementors use the output
from the RIDDL tools to aid in their comprehension and coding of a solution
that corresponds to the RIDDL system specification or model.

## Developers
Developers are the technical experts that advance RIDDL's state of the art. They
develop the tooling and documentation that makes up RIDDL. Since RIDDL is an 
open source project, developers can be affiliated with many organizations, 
presumably the organizations that derive utility from RIDDL. 


<!-- FIXME: Reconcile the below with the above.

### Author

### Domain Experts
Domain experts include experts from both business and technical teams. These individuals are recognized authorities and key influencers within the organization. They may or may not be in leadership positions, but they are people who are broadly trusted for their knowledge of business rules/processes and/or systems. These are the [EF Huttons](https://www.youtube.com/watch?v=ByhYlY5WVvQ) of the business. While not comprehensive, these people may be Architects, Analysts, Developers, Managers, QA Engineers, or even Call Center Agents, and line workers from the warehouse.

Domain Experts do not need to be well versed in DDD or Reactive Architecture initially. It is the job of the Author to act as a guide and mentor through these topics. But Domain Experts must be captureContext to change what is for what could be. Reactive Architectures can be very challenging for people to digest. More specifically, quite often techniques used to implement distributed and reactive systems will change user experiences, expectations of consistency and availablity, the means used to monitor and maintain systems and processes, and so on. Domain experts will need to come to understand the reasons for these changes and be able to evangelize them as they interact with their peers.

### Implementors
Implementors are the technical experts who implement the system defined in the RIDDL specification. These people are most often Software, QA, and DevOps Engineers. Of course, throughout implementation they will be supported by the Author(s) and Domain Experts, as well as Project Managers, Scrum Masters, Security Analysts, and so on. It is incumbent on the implementation team to keep the RIDDL sources up to date and accurate as the system evolves.

Implementors should be experts in Reactive Architectures. In addition, software engineers, and to a certain extent, other implementors need to be well versed in the implementation tech stack. The creators of the RIDDL language have found that Scala and Akka deployed into a cloud environment provide the best tooling and support for implementing a reactive system. It is not surprising then, that some of the concepts and constructs found in RIDDL have strong parallels to these tools. It must be noted, however, that reactive systems can be implemented using a variety of languages, frameworks, environments, products and tools. Cloud native offerings can be used with great effect. The critical point is, throughout implementation, reactive principles must be forefront in mind as implementation choices are made.

It must also be stated at this point that even though it may conflict with reactive principles, the business has final say in major implementation choices. It is incumbent on the implementation team to advise decision makers on the risks and challenges that is posed by making choices that conflict with reactive principles. Factors like time, cost, user experience, business rules, availability of technical talent, strategic partners, and so on are all extremely important and may conflict with the choices of the implementation team and sound reactive architecture.

Read the above sectio
-->
