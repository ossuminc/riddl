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
language, please refer to the [Audience](../../../audience) section.


The RIDDL syntax grammar is broken down into the following portions:
* [terminals](terminals) - Terminal symbols used in the grammar
* [common](common) - Common grammar productions used in other files
* [root](root) - Top level root level ("file scope").
* [domain](domain) - Defining domains
* [context](context) - Defining contexts
* [entity](entity) - Defining entities
