---
title: "Descriptions"
type: "page"
weight: 15
draft: "false"
---

The RIDDL syntax is simply a hierarchical arrangement of definitions. Each definition can have a
description. Descriptions are used to generate documentation and follow markdown conventions.

## Examples
There are several ways to describe a definition. In each of the examples we attach a description 
to some `domain` named `Foo`. The definition is not important for our purposes here. Descriptions 
come after the definition using one of four phrases:
* `described by`
* `described as`
* `explained by`
* `explained as`

These four phrases are equivalent but provided to suit the nature of the definitions to which they
may be applied. For example: 
```riddl
domain Foo is {
} explained as "Foo is not a very good domain name" 
```
is equivalent to:
```riddl
domain Foo is {
} described by "Foo is not a very good domain name" 
```

## Quoted String Format
The examples show above use a single string as the description. This is appropriate when 
the description is short, as is typical for small definitions.

## Quoted Strings Format
Alternatively, a large description may be provided as a set of quoted strings enclosed in curly braces.
For example:
```riddl
domain Foo is {
} described by {
   "Foo is not a very good domain name"
   "And an empty domain doesn't define anything!"
} 
```
Note in this case that we have embedded [markdown syntax](https://www.markdownguide.org/basic-syntax)
into the description. 

## Markdown Format
Alternatively, to make things a little more free-form, and aligned on the left column, a
description may be formed using just a vertical bar character to indicate the line start. 
For example:
```riddl
domain Foo is {
} described by {
   |# Warning
   |Foo is not a very good domain name
   |And an empty domain doesn't define anything!
} 
```
Note in this case that we have embedded [markdown syntax](https://www.markdownguide.org/basic-syntax)
into the description.  The `# Warning` syntax is an indication to a markdown processor that a 
new heading with the text "Warning" should be started. All descriptions are encouraged to use 
this markdown syntax style. 

## Using Markdown Syntax
[Markdown syntax](https://www.markdownguide.org/basic-syntax) is encouraged in descriptions because
the [`riddlc` compiler](../../introduction/compilation) can translate RIDDL specifications into 
the input of the website generator [hugo](https://gohugo.io/about), which expects markdown. 
In this way, a large RIDDL specification can be translated automatically into a beautiful
website. 

The full range of markdown, html, and shortcode syntax that hugo supports may be used in
RIDDL descriptions. [See this link for more details on hugo](https://gohugo.io/documentation/)
