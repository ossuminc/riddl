---
title: "RIDDL Users"
date: 2022-02-25T10:07:54-07:00
draft: true
weight: 20
---

RIDDL has three primary users:
* **Author:** The Author is the individual who creates the RIDDL specification. This person is the bridge between Domain Experts and Implementors.
* **Domain Experts:** Domain Experts read and validate [specification outputs](../riddloutputs/) for accuracy and completeness.
* **Implementors:** Implementors do the work of converting the RIDDL specification into a working system.

### Author
The author should be an expert in DDD and reactive architecture concepts. This knowledge is essential in the successful construction of the domain model which is encoded into the RIDDL source. While not required, it is also helpful to be familiar with Scala, Akka (including Akka Clustering and Streaming), and cloud architectures.

The author needs a clear and intuitive language to program in (RIDDL). This activity is greatly faciliated by tools to aid the job, for example, IDE plugins, graphical authoring utilities, and so on. The author needs to have the ability to configure these tools and utilites to match their mode of work. The RIDDL complier must emit clear feedback on errors, warnings, and other details that would improve the overall correctness and outputs from RIDDL.  

Finally, the author should have broad control to influence the look and feel, as well as the content of the outputs. For [specification outputs](../riddloutputs/), examples might include fonts, colors, logos, and even layout of the specification web site. For [implementation artifacts](../riddloutputs/), these customizations could include license, copyright, and trademark information that serve as a preamble in code artifacts.

RIDDL sources are intended to kept in version control along with the source code. The RIDDL compiler, [riddlc](../essentialutitlies/riddlc/), has utilities that will automate the process of generating RIDDL outputs from source control or in local environments. These will be discussed further when we dive into riddlc in depth.

### Domain Experts
Domain experts include experts from both business and technical teams. These individuals are recognized authorities and key influencers within the organization. They may or may not be in leadership positions, but they are people who are broadly trusted for their knowledge of business rules/processes and/or systems. These are the [EF Huttons](https://www.youtube.com/watch?v=ByhYlY5WVvQ) of the business. While not comprehensive, these people may be Architects, Analysts, Developers, Managers, QA Engineers, or even Call Center Agents, and line workers from the warehouse.

Domain Experts do not need to be well versed in DDD or Reactive Architecture initially. It is the job of the Author to act as a guide and mentor through these topics. But Domain Experts must be open to change what is for what could be. Reactive Architectures can be very challenging for people to digest. More specifically, quite often techniques used to implement distributed and reactive systems will change user experiences, expectations of consistency and availablity, the means used to monitor and maintain systems and processes, and so on. Domain experts will need to come to understand the reasons for these changes and be able to evangelize them as they interact with their peers.

### Implementors
Implementors are the technical experts who implement the system defined in the RIDDL specification. These people are most often Software, QA, and DevOps Engineers. Of course, throughout implementation they will be supported by the Author(s) and Domain Experts, as well as Project Managers, Scrum Masters, Security Analysts, and so on. It is incumbent on the implementation team to keep the RIDDL sources up to date and accurate as the system evolves.

Implementors should be experts in Reactive Architectures. In addition, software engineers, and to a certain extent, other implementors need to be well versed in the implementation tech stack. The creators of the RIDDL language have found that Scala and Akka deployed into a cloud environment provide the best tooling and support for implementing a reactive system. It is not surprising then, that some of the concepts and constructs found in RIDDL have strong parallels to these tools. It must be noted, however, that reactive systems can be implemented using a variety of languages, frameworks, environments, products and tools. Cloud native offerings can be used with great effect. The critical point is, throughout implementation, reactive principles must be forefront in mind as implementation choices are made. 

It must also be stated at this point that even though it may conflict with reactive principles, the business has final say in major implementation choices. It is incumbent on the implementation team to advise decision makers on the risks and challenges that is posed by making choices that conflict with reactive principles. Factors like time, cost, user experience, business rules, availability of technical talent, strategic partners, and so on are all extremely important and may conflict with the choices of the implementation team and sound reactive architecture.