---
title: "Analyses"
description: "Adding analytical data as an output from AST traversal"
draft: "false"
weight: 5
---

## Uses/Used-by

Common to many programming language compilers, RIDDL too needs to be able to
generate a pair of maps
1. _uses_ = key is a definition, value is a list of definitions used by the key
2. _used-by_ = key is a definition, value is a list of definitions that use 
   the key

