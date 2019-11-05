# Conventions

Syntax conventions of RIDDL are very simple and lenient. 
The intended audience is business owners, business analysts, domain engineers,
and software architects. It is intentionally simple and 
readable. The language is free in its formatting. It does
not require indentation and its various constructs can be
arranged on any line.  RIDDL supports the definition of a
variety of concepts taken directly from Domain Driven Design
and the Unified Modeling Language. 

## Definitions and References
The language is simply a declaration of definitions and references. 
A definition introduces a named instance of some concept and a
specification of that thing. If RIDDL supported the concept of a
Cat (it doesn't), then you might specify it like this:
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

This is a simple convention used throughout the language for various 
concept definitions.

## Definitions can be nested
Definitions need not only contain the properties of the concept being defined.
Between the `{` and the `}`, other, nested, definitions may be defined. This is
only true of some concepts like `domain`, `context` and `entity`.  We call 
these definitions "containers" because they contain other definitions. 
Definitions that may not contain other definitions are called "leaves"
because, like tree leaves, they occur at the extremity (most nested) part of
the definitions.

# Work In Progress
Modelling a domain can be hard work. New ideas come up that must be flushed
out.  Sometimes things get left undefined. That's okay! Riddl uses a special
construct, `???` to mean "we don't know yet". It can be used as the body of
any definition. For example it is entirely legal to write:
```text
entity Thing_A_Ma_Bob { ??? }
```
# Descriptions, or Explanation
