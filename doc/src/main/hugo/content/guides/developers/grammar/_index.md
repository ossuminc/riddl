---
title: "Grammar"
type: "page"
draft: "false"
weight: 50
---

This section defines the RIDDL language's grammar formally. 

### Extended Backus-Naur Form
When RIDDL grammar definitions are shown, we use the EBNF grammar
meta-language to specify the grammar.  You can read about this
[grammar meta-language on Wikipedia](https://en.wikipedia.org/wiki/Extended_Backus%E2%80%93Naur_form)

For example, here's how EBNF can define a quoted string:
```ebnf
all characters = ? any utf-8 character ? ;
quoted string =  '"', { all characters - '"' }, '"';
```
This form can be quite technical and is intended for those who are familiar with
EBNF format and lexical parsing.  For a more descriptive introduction to the 
language, please refer to the [Guides]({{< relref "../../_index.md" >}}) 
section.


The RIDDL syntax grammar is broken down into the following portions:
* [terminals]({{< relref "terminals.md" >}}) - Terminal symbols used in the 
  grammar
* [common]({{< relref "common.md" >}}) - Common grammar productions used in 
  other files
* [root]({{< relref "root.md" >}}) - Top level root level ("file scope").
* [domain]({{< relref "domain.md" >}}) - Defining domains
* [context]({{< relref "context.md" >}}) - Defining contexts
* [entity]({{< relref "entity.md" >}}) - Defining entities
