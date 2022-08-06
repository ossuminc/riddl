---
title: "Audience"
date: "2021-12-01T15:34:22-05:00"
draft: "false"
weight: 10
---

## Summary
There are four kinds of readers of this documentation based on the intent of
the reader. Accordingly, the documentation has sections for each kind of
reader:
* **Domain Experts** read and validate [specification outputs](../riddloutputs/)
  for accuracy, correctness, and completeness.
* **Authors** are individuals who create system specifications using RIDDL.  
  Authors form the bridge between Domain Experts and Implementors.
* **Implementors** do the work of converting the RIDDL specification into a
  working software system.
* **Developers** are those people who are creating, extending, documenting and
  otherwise providing RIDDL to the other three kinds of users.

The following sections define these roles more clearly.

## Author
The author should be an expert in DDD and reactive architecture concepts. This
knowledge is essential in the successful construction of the domain model which
is encoded into the RIDDL source files. It is also very helpful if the author
is familiar with:
* Markdown
* Git
* Hugo
* Geekdoc Theme for Hugo
* HOCON configuration format
* Scala programming language

The author needs a clear, precise and intuitive language in which to define a
system. That language is RIDDL. This activity is greatly facilitated by tools
to aid the job, for example, IDE plugins, graphical authoring utilities, and
so on. The author needs to have the ability to configure and run these tools and
utilities to match their mode of work. The RIDDL compiler emits clear
feedback on errors, warnings, and an author must be able to interpret those
messages and use them to adjust the RIDDL sources to improve the overall
correctness in the outputs from RIDDL.

Finally, the author should have broad control to influence the look and feel,
as well as the content of the outputs. For
[specification outputs](../riddloutputs/), examples might include fonts,
colors, logos, and even layout of the specification web site.
For [implementation artifacts](../riddloutputs/), these customizations could
include license, copyright, and trademark information that serve as a preamble
in code artifacts.

RIDDL sources are intended to be kept in version control along with the source
code. The RIDDL compiler, [riddlc](../essentialutitlies/riddlc/), has
utilities that will automate the process of generating RIDDL outputs from
source control or in local environments. These will be discussed further when
we dive into riddlc in depth.

## Domain Experts
Domain experts include experts from both business and technical teams. These
individuals are recognized authorities and key influencers within the
organization. They may or may not be in leadership positions, but they are
people who are broadly trusted for their knowledge of business rules,  
processes, and systems. These are the
[EF_Huttons](https://www.youtube.com/watch?v=ByhYlY5WVvQ) of the business.
While not a comprehensive list, these people may be Architects, Analysts,
Developers, Managers, QA Engineers, or even Call Center Agents, and line
workers from the warehouse.

Domain Experts do not need to be well versed in DDD or Reactive Architecture
initially. It is the job of the Author to act as a guide and mentor through
these topics. But Domain Experts must be open to change what is for what
could be. Reactive Architectures can be very challenging for people to digest.
More specifically, quite often techniques used to implement distributed and
reactive systems will change user experiences, expectations of consistency
and availability, the means used to monitor and maintain systems and processes,
and so on. Domain experts will need to come to understand the reasons for
these changes and be able to evangelize them as they interact with their peers.

## Implementors
Implementors are the technical experts who implement the system defined in the
RIDDL specification. These people are most often Software, QA, and DevOps
Engineers. Of course, throughout implementation they will be supported by the
Author(s) and Domain Experts, as well as Project Managers, Scrum Masters, 
Security Analysts, and so on. It is incumbent on the author and implementation
team members to keep the RIDDL sources up to date and accurate as the system
evolves. The implementation team members must notify the author of changes to 
the model that the technical implementation necessitates.

Implementors should be experts in Reactive Architectures. In addition, software
engineers, and to a certain extent, other implementors need to be well versed
in the implementation tech stack. The creators of the RIDDL language have found
that Scala and Akka deployed into a cloud environment provide the best tooling
and support for implementing a reactive system. It is not surprising then, that
some concepts and constructs found in RIDDL have strong parallels to these 
tools. It must be noted, however, that reactive systems can be implemented
using a variety of languages, frameworks, environments, products and tools. 
Cloud native offerings can be used with great effect. The critical point is, 
throughout implementation, reactive principles must be forefront in mind as
implementation choices are made.

It must also be stated at this point that even though it may conflict with
reactive principles, the business has final say in major implementation 
choices. It is incumbent on the implementation team to advise decision makers
on the risks and challenges that are posed by making choices that conflict with
reactive principles. Factors like time, cost, user experience, business rules,
availability of technical talent, strategic partners, and so on are all 
extremely important and may conflict with the choices of the implementation 
team and sound reactive architecture.

## Developers
Developers are the technical experts that advance RIDDL's state of the art. They
develop the tooling and documentation that makes up RIDDL. Since RIDDL is an 
open source project, developers can be affiliated with many organizations, 
presumably the organizations that derive utility from RIDDL. 

Developers must be experts in:
* Domain Drive Design
* Scala Programming Lanauge
* Li Haoyi's fastparse
* Compiler Design
* Functional programming concepts like folding, AST, etc. 
* Test Driven Development
* Build Automation
* Agile Engineering Practices
