---
title: "Options"
draft: false
---

Options are instructions to translators on how a particular 
definition should be regarded. Any translator can make use of any
option. Options can take a list of string arguments much like the 
options and arguments to a program. If none are specified, the option is 
considered to be a Boolean value that is `true` if specified.

Every [vital definition]({{< relref vital.md >}}) in RIDDL allows a
`technology` option that takes any number of string arguments. These can
specify the technologies intended for the implementation. This idea was
adapted from a similar idea in
[Simon Brown's](https://www.linkedin.com/in/simonbrownjersey/)
[C4 Model For Software Architecture](https://c4model.com/#Notation)

Other options are specific to the kind of vital definition. See the 
vital definition's page for details on the options they take. Non-vital 
definitions do not allow options. 

## Occurs In
All [vital definitions]({{< relref "vital.md" >}})

## Contains
Option values which are simple identifiers with an optional set of string 
arguments.
