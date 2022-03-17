---
title: "Documentation Conventions"
type: "page"
draft: "false"
weight: 20
---

This page defines the conventions we use throughout this documentation. 

### RIDDL Snippets
Whenever we include RIDDL code in the documentation it will be in a fixed
sized font like this:
```riddl
domain MyDomain is { ??? }
``` 

### Extended Backus-Naur Form
When RIDDL grammar definitions are made, we utilize the EBNF grammar
meta-language to specify the grammar.  You can read about this 
[grammar meta-language on Wikipedia](https://en.wikipedia.org/wiki/Extended_Backus%E2%80%93Naur_form)
For example, here's how EBNF can define a quoted string:
```ebnf
all characters = ? any utf-8 character ? ;
quoted string =  '"', { all characters - '"' }, '"';
```
### Notes
{{% hint info %}}
Whenever an incidental note needs to be presented, it will be shown in blue
like this paragraph.
{{% /hint %}}

### Recommendations 
{{% hint ok %}}
Whenever an important recommendation needs to be made it will be shown in green 
like this paragraph.
{{% /hint %}}

### Warnings
{{% hint warning %}}
Whenever a point that is often a source of confusion needs to be made, it will 
be shown in yellow like this paragraph.
{{% /hint %}}

### Dangers
{{% hint danger %}}
Whenever a point that will lead to errors or have dangerous consequences 
needs be made, it will be shown in red like this paragraph.
{{% /hint %}}

### Language Definitions
{{% hint nada %}}
Whenever an important RIDDL language definition is made, it will appear in a 
box like this.
{{% /hint %}}
