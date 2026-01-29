---
title: "Translation"
description: "Translating RIDDL input to other forms"
type: "page"
draft: "false"
weight: 50
---

{{% hint warning %}}
**Moved to riddl-gen**

The RIDDL translation commands (Hugo documentation generation and diagram
generation) have been moved to the
[riddl-gen](https://github.com/ossuminc/riddl-gen) repository.

Please use `riddl-gen` for:
- Generating Hugo-based documentation websites from RIDDL models
- Generating various diagram types (context maps, data flow diagrams, etc.)

The core `riddlc` compiler focuses on parsing, validation, and BAST
serialization.
{{% /hint %}}

## What riddlc Can Do

The `riddlc` compiler provides these core capabilities:

- **Parse** - Parse RIDDL source files and report syntax errors
- **Validate** - Validate RIDDL models for semantic correctness
- **Prettify** - Reformat RIDDL source to a standard layout
- **BAST** - Convert between RIDDL and Binary AST format for faster loading
- **Stats** - Generate statistics about RIDDL models

See the [command-line help]({{< relref "command-line-help.md" >}}) for details.