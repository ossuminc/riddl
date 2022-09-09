---
title: "Implementor's Guide"
description: "RIDDL documentation focused on implementors' usage"
date: 2022-08-06T10:50:32-07:00
draft: false
weight: 30
---

Implementors are the technical experts who implement the system defined in a
RIDDL specification. These people are most often Software, QA, and DevOps
Engineers. Of course, throughout implementation they will be supported by the
Author(s), Domain Experts, and other related staff. Implementors use the output
from the RIDDL tools to aid in their comprehension and coding of a solution
that corresponds to the RIDDL system specification or model.

It is incumbent on the
author and implementation team members to keep the RIDDL sources up to date
and accurate as the system evolves. The implementation team members must notify the author of changes to
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

{{< toc-tree >}}
