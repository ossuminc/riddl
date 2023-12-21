---
title: "Comment"
draft: "false"
---

A comment is a semantic component of the RIDDL language and not just ignored by
the parser. Comments use the C language notation, for example:
```C
// This is a comment to the end of the line ------->
/* This is an inline comment with a terminator: */
```
However, comments cannot just occur anywhere white space may occur. They can
occur at the top level of a file, before definitions and after definitions. 

