---
title: "Author's Guide"
description: "RIDDL documentation focused on RIDDL authors' usage"
date: 2022-02-25T10:50:32-07:00
draft: true
weight: 20
---


The author needs a clear and intuitive language to program in (RIDDL). This activity is greatly faciliated by tools to aid the job, for example, IDE plugins, graphical authoring utilities, and so on. The author needs to have the ability to configure these tools and utilites to match their mode of work. The RIDDL complier must emit clear feedback on errors, warnings, and other details that would improve the overall correctness and outputs from RIDDL.

Finally, the author should have broad control to influence the look and feel, as well as the content of the outputs. For [specification outputs](../riddloutputs/), examples might include fonts, colors, logos, and even layout of the specification web site. For [implementation artifacts](../riddloutputs/), these customizations could include license, copyright, and trademark information that serve as a preamble in code artifacts.

RIDDL sources are intended to kept in version control along with the source code. The RIDDL compiler, [riddlc](../essentialutitlies/riddlc/), has utilities that will automate the process of generating RIDDL outputs from source control or in local environments. These will be discussed further when we dive into riddlc in depth.

Authors are the individuals who create system specifications. Such work is
greatly aided by using a clear, concise, precise, and intuitive language
in which to specify a system. That language is RIDDL. Authors form the
bridge between Domain Experts and Implementors.

Authors should have an expert understanding of DDD, and be familiar with
reactive architecture, API design and distributed systems' concepts. While not 
required, it is also helpful to be familiar with Scala, Akka (including Akka 
Clustering and Streaming), and cloud architectures. This knowledge is useful for
the successful construction of va viable domain model encoded in the RIDDL 
language. Consequently, Authors must acquire a full understanding of the 
syntax, structure and semantics of RIDDL.

It will also be very helpful if authors become familiar with:
* Markdown
* Git
* Hugo
* Geekdoc Theme for Hugo
* HOCON configuration format
* Scala programming language
* The Actor Model

This activity is greatly facilitated by tools
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
