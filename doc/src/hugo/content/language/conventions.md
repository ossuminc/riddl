---
title: "Conventions"
weight: 10
---

# Introduction

Syntax conventions of RIDDL are very simple and lenient. 
The intended audience is business owners, business analysts, domain engineers,
and software architects. It is intentionally simple and 
readable. The language is free in its formatting. It does
not require indentation and its various constructs can be
arranged on any line.  RIDDL supports the definition of a
variety of concepts taken directly from Domain Driven Design
and the Unified Modeling Language as well as software architecture. 

The following language conventions are adhered to throughout the language for
ease of use (special cases and rule contraventions cause confusion). 

# Language Consistency
Most things in RIDDL are consistent throughout the language. We believe this 
makes learning the language easier since there are no exceptions to fundamental constructs.
The sections below define the consistent language features.

# Everything Is A Definition
The language is declarative. You don't say how to do something, you specify the end
result you want to see. The language aims to capture a detailed and concise definition
of the abstractions a complex system will require. It does not specify how those
abstractions should be built. That is for an engineer to implement.

# Every Definition Can Be Documented
Every thing you can define can have a `described by` suffix which lets you document
the definition using markdown.

# Many Definitions Are Containers
A container is a definition that contains other definitions. For example, 
a domain definition is a recursively nested definition:
```riddl
domain root is {
  domain branch is {
    domain leaf {
    }
  }
}
```
That is domains are definitions that can contain the definition oof (sub)
domains. Similarly `context` can define `entity` 
```riddl
context foo is {
  entity bar is {
    ...
  }
}
```
## Definitions And References
The language is simply a hierarchically nested set of definitions and
containers of definitions.  A definition introduces a named instance of some
concept in the RIDDL meta-model. The specification of that definition
proceeds directly following the `is` keyword.

If RIDDL supported the concept of a Cat and its owner  (it doesn't in both 
those cases), then you might specify a cat named "Smudge" with an owner 
named "Reid" like this:
```text
cat Smudge is {
  owner is entity Reid
}
```
Here is an explanation of each of these tokens:

* `cat` is the  kind of concept that the author wants to define
* `Smudge` is the name the author wants to assign to this `cat` concept
* `is` is a required keyword for readability
* `{` starts the definition of Smudge the cat
* `owner` is the name of a property that all "cat" concepts have
* `is` is another keyword required for readability
* `entity Reid` is a reference to an instance of a concept. The concept is an
 `entity` and the name of it is `Reid`. References to many kinds of concepts
  are always made in this way, by name with a prefix for the kind of concept.
* `}` completes the definition of Smudge the cat.

This is a simple convention used throughout the language for all 
concept definitions and references to them. 

## Containers
Containers are definitions that contain other, nested, definitions. Between the
`{` and the `}` that define the boundaries of a definition, you may place other
definitions. Such nested definitions are deemed to be **contained**. 
Not every definition is a container as it only applies to concepts
like `domain`, `context` and `entity`.   

The full list of all Container types is as follows:

* `topic`
* `feature`
* `entity`
* `adaptor`
* `context`
* `interaction`
* `domain`

## Leaves
Definitions that may not contain other definitions are called "leaves"
because, like tree leaves, they occur at the extremity (most nested) part of
the definitional hierarchy. 

## Work In Progress
Modelling a domain can be hard work. New ideas come up that must be flushed
out.  Sometimes things get left undefined. That's okay! Riddl uses a special
construct, `???` to mean "we don't know yet". It can be used as the body of
any definition. For example it is entirely legal to write:
```text
cat Smudge is { ??? }
```
If we aren't sure of the characteristics of the cat named "Smudge"
 
## Descriptions (Explanations)
A definition may also be accompanied by some text or markup at its end to
describe or explain the purpose of that definition. We call these
 **descriptions** or **explanations** because this is the text that is
used to describe or explain the RIDDL definition, and it is used to generate
documentation for the definition.  A description occurs directly after the
definition's closing curly bracket and is preceded using
either the `described as` or `explained as` keyword phrase. Essentially it 
looks like this:
```text
cat Smudge is { ??? } explained as "TBD but owned by Reid"
```  
See the @ref:[Quick Start](quickstart.md) for more examples 
of definition descriptions.  

The grammar for a definition is this:
```ebnf
<description> ::= ("described" | "explained") "as" "{" <description-body> "}"

<description-body> ::= <literal-string-description> | <doc-block-description>

<literal-string-description> ::= <literal-string>+

<literal-string> ::= "\"" 

<doc-block-description> ::=  

```

What occurs within a description/explanation can be one of three things:

* A single literal string in quotations: `"this string"`, as shown above.
* A curly brace enclosed list of "docblock" lines which consists of a group
  of lines, each preceded by a vertical bar. The bar denotes the left margin.
  Markdown syntax is accepted. 
* A curly brace enclosed list of four sections: `brief`, `details`, 
  `items` and `see`

Each of these is explained in more detail in the sections that follow. 

### Single Literal String
Pretty simple, like this:
```riddl
domain SomeDomain is { ??? } explained as "Not very helpful"
```

### Documentation Block
Allowing markdown syntax, like this:
```riddl
domain SomeDomain is { ??? } explained as {
  |## Overview
  |This domain is rather vague, it has no content.
  |## Utility
  |The utility of this domain is dubious because:
  |* It has no content
  |* Its name is not useful
  |* It is only an example of RIDDL syntax
}
```

### Separate Sections
When more formal documentation is required for major definitions (domains,
contexts, entities), then you should use the sectioned style to group
your documentation into standard sections, like this: 

* `brief` is a simple text description of the definition
* `details` is a longer textual description enclosed in a block `{ }`, 
avoiding the need to quote the text.  This property may include Markdown 
directives that will be rendered in any generated documentation.
* `items` is a means of including references to other entities or definitions 
is also enclosed withing a block `{ }`.  
* `see` is a block where additional resources supporting the description may 
be listed.

All of these nested blocks can use markdown in a doc block or simple literal
strings depending on your needs. For example:
```riddl
domain SomeDomain is { ??? } explained as {
  brief { "this domain is rather vague, it has no content" } 
  description {
    |The utility of this domain is dubious because:
  }
  items("Aspects Of Utility") {
    |* It has no content
    |* Its name is not useful
    |* It is only an example of RIDDL syntax
  
  }
}
```


 

