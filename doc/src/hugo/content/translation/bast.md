---
title: "Binary AST"
type: "section"
weight: 40
---

When the `riddlc` compiler parses a RIDDL document, it translates it to an Abstract 
Syntax Tree (AST) in memory. The AST is then used by other passes to validate and translate the 
AST into other forms. The binary AST (BAST) translator converts the AST in memory into a binary 
format that is stored for later usage.  Saving the BAST format and then reading it back into 
the compiler avoids the time to both parse the RIDDL document and validate it for consistency.

Consequently, the `riddlc` offers a translator from validated AST to BAST format and the ability 
to read BAST files instead of RIDDL files. 
