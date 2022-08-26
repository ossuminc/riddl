---
title: "Home"
date: "2021-12-01T15:34:22-05:00"
draft: "false" 
weight: 1 
creatordisplayname: "Reid Spencer"
creatoremail: "reid@ossuminc.com"
---

![RIDDL Logo](/images/RIDDL-Logo-128x128.png)

RIDDL is a language and toolset for specifying a system design 
using a combination of unique ideas and ideas from prior works. 
Before we get into the details, this documentation has multiple
paths by which you can learn RIDDL, depending on how you learn.

Everyone should read the [Introduction](introduction) section as it
provides a high level introduction to RIDDL, defines what RIDDL is, 
what it can do, and what it is based upon.  Definitely start 
with the [Introduction](introduction) if you're new to RIDDL.

There is an [Audiences](audience) section which provides learning 
paths for different kinds of readers:
<div style="background-color: #8f9ea8; padding-left: 3px">
{{< columns >}}
{{< button size="large" relref="audience/domain-experts-guide" >}}Expert's Guide {{< /button >}}
For knowledge domain experts who would provide concepts, structure, and event 
flows to a RIDDL author. 
<--->
{{< button size="large" relref="audience/authors-guide" >}}Author's Guide {{< /button >}}
For those who will write RIDDL specifications and must know the language 
intricacies for full and accurate specifications.
<--->
{{< button size="large" relref="audience/implementors-guide" >}}Implementor's Guide{{< /button >}}
For those who will implement software systems based on RIDDL specifications and
the output of RIDDL based tools.
<--->
{{< button size="large" relref="audience/developers-guide" >}}Developer's Guide {{< /button >}}
For those who will work on  the RIDDL software itself and tools such as `riddlc` 
{{< /columns >}}
</div>

If you're just interested in the concepts, there is the [Concepts](concepts) 
section which describes the various concepts used by RIDDL, their relationships,
and utility. This section will give you a complete understanding of the ideas
used in RIDDL, without worrying about the syntax of the language that is used
to express those ideas.

For those that like to learn incrementally, there is a 
[Tutorial](tutorial) that walks the reader through the 
construction 
of a RIDDL model for a restaurant chain, and the resulting software system 
to operate it.

For those that like to learn by example, the model produced in the 
[Tutorial](tutorial section) is presented, step-by-step, with new concepts 
added as they occur, in the [Example](examples) section.

If you want to dive into the technical specification for the RIDDL language,
you can go to the [Language](language) section and learn the language from 
first principles. 

If you want to dive into the software tools that RIDDL provides, you can go
to the [Tooling](tooling) section and learn how to install and use them. 

If you're interested in knowing how we plan to extend RIDDl in the future, 
please review our [Future Work](future-work) section. 

{{< toc-tree >}}
