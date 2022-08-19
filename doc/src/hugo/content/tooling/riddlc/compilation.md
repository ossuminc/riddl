---
title: "Compilation"
draft: "false"
type: "page" 
weight: 40
---

# Introduction
The Riddl compiler performs several analyses which are described in this 
section and known collectively as *compilation*. Each of these analyses
occurs in a compilation phase, as described in the following sections.

## Lexical Analysis
Riddl uses the excellent [fastparse](https://www.lihaoyi.com/fastparse/)
library by [Li Haoyi](http://www.lihaoyi.com/). This phase parses the raw
textual input to make sure it is syntactically correct. From that syntax, an
abstract syntax tree (AST) is produced. Incorrect syntax leads to errors 
without further analysis.

## Structural Analysis
If lexical analysis succeeds, an Abstract Syntax Tree (AST) is compiled as the
internal representation of the RIDDL source input within the tools' memory. 
Structural Analysis succeeds when the AST is constructed without error. The  
AST represents the containment hierarchy of the input definitions. 

## Style Analysis
To aid in reader comprehension, a certain way of using the RIDDL language is
recommended. Optionally, the compiler can generate *style warnings* to indicate
language specifications that deviate from that recommended style.
 
## Semantic Analysis
The Riddl AST is very flexible. It can accept a wide range of input, even input
that doesn't necessarily make logical sense. For example, suppose you wrote this:
```text
entity MyLittlePachyderm is {
  state is {
    tusk is TuskDefinition
 }
}
```
This defines an entity type named `MyLittlePachyderm` which can be used as the 
pattern to instantiate many entities with this form. This entity is defined with
a single state value, `tusk` that has the type `TuskDefinition`. Note that there
is no definition of `TuskDefinition` as there is for the entity, state and tusk.
Consequently, we don't know the type of the `tusk` field so our specification 
is incomplete and would fail semantic analysis. 

Semantic analysis, also known as validation, is the process of finding omissions,
as described above, as well as:

* references to undefined things,
* references to existing things of the wrong type, 
* constructs that may be confusing,
* definitional and logical inconsistencies
* and, etc. 

The semantic analysis phase generates messages that identify the omissions and 
inconsistencies in the input specification. These validity issues typically
stop the compiler from proceeding with translation because using an invalid 
input model tends to produce output that is flawed or less than useful.  

