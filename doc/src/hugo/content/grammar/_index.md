---
title: "Grammar"
draft: false
weight: 25
---

# Overview
This section defines the RIDDL syntax grammar using [EBNF](https://en.wikipedia.org/wiki/Extended_Backus%E2%80%93Naur_form)
This form can be quite technical and is intended for those who are familiar with
EBNF format.  For a more descriptive introduction to the language, please 
refer to the [Tutorial](../tutorial) section.

## Contents


# Terminals
This file shows the definition of the terminal symbols, as productions, and 
groups in categories:

{{% code file=grammar/ebnf/terminals.ebnf language=ebnf %}}

# Common
A number of frequently used productions are useful to understand in the 
sections that follow.

{{% code file=grammar/ebnf/common.ebnf language=ebnf %}}

# Containers

## Entity
{{% code file=grammar/ebnf/entity.ebnf language=ebnf %}}
