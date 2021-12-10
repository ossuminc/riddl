---
title: "Documentation Conventions"
draft: false
type: page
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
{{% notice note %}}
Whenever an important point needs to be identified it will be introduced as a
note like this paragraph
{{% /notice %}}

### Warnings
{{% notice warning %}}
Whenever a potentially dangerous point needs to be made, 
it will be introduced as a warning like this paragraph.
{{% /notice %}}

### Language Definitions
{{% panel theme="success" header="Definition" %}}
Whenever an important RIDDL language definition is made, it will appear in a 
box like this.
{{% /panel %}}
