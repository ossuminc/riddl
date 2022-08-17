---
title: "Identifiers"
type: "page"
draft: "false"
weight: 20
---

Identifiers are the names of definitions. In the following domain definition,
```shell
domain foo is { ??? }
```
the identifier is `foo`. Identifiers can be specified in two ways:
* _simple_: any alphabetic character followed by alphanumerics or underscore
* _quoted_: `"` followed by a string of characters chosen from this set: 
  `a-zA-Z0-9_+\-|/@$%&, :"` followed by a `"`

## Path Identifiers
In several places in RIDDL, you may need to reference a definition in
another definition. Such references are called Path Identifiers. 
Path identifiers encode a simple algorithm to find the definition of interest. 

The best way to learn path identifiers is by example, so 
please consider the following example as you read the sections below

```riddl
domain A {
  domain B {
    context C {
      type Simple = String(,30)
    }
    type BSimple = A.B.C.Simple // full path  
    context D {
      type DSimple = .E.ESimple // partial path
      entity E {
        type ESimple = ^^C.Simple // partial path
        type Complicated = .^^C^D.DSimple
      }
    }
  }
}
```

### Path Identifier Syntax
Path identifiers are composed of only the names of the definitions, the caret 
symbol, `^`, and the period character, `.`. The path is interpreted this way:
* Start in the context of the container definition in which the path occurs
* If the next symbol is `^` make the context the parent of the current context
* If the next symbol is an identifier, make the current context that definition
* If the next symbol is a `.` it can be considered as the current context and 
  thus ignored.

Note that separating the names by periods simply allows us to distinguish
the names of adjacent definitions. The resulting definition is the final context.

#### Full Path Identifier
A full path starts from the root of the hierarchy and mentions each
definition name until the sought leaf definition is reached.  The full path
identifier to the `Simple` type definition in the example above is
`A.B.C.Simple` which is also used in the definition of the `BSimple` type

#### Partial Path Identifiers
Path identifiers can be partial too. All partial path identifiers start with
a caret, `^`. A single caret indicates the current container definition in the
hierarchy, two periods indicates the container's container, three periods
indicates the container's container's container, and etc.

In the example, the definitions of both `DSimple` and `ESimple` use partial
paths to name the type.  

For `DSimple` the partial path, `.E.ESimple`, is broken
down like this:
* start with the current container, context `D`
* `.` - select the current container, context `D`
* `E` - select the definition named`E`, entity `E`
* `.` - select the current container, entity `E`
* `ESimple` - select the type named `ESimple`

For `ESimple`, the partial path, `^^C.Simple` is broken down like this:
* start with the current container, entity `E`
* `^` - go up to context `D`
* `^` - go up to domain `B`
* `C` - select context C
* `.` - select current container, context `C`
* `Simple` - select definition named "Simple"

#### Complex Partial Path Identifiers

The definition of `Complicated` uses path `.^^C^D.DSimple` in the example
above. This helps us to see how DSimple is referenced in a complicated
path. The complicated part is the `^` between `C` and `D`. This path is
interpreted like this:
* `.` - start with the current container, entity`E`
* `^` - go up one container to context `D`
* `^` - go up one container to domain `B`
* `C` - in the current container, domain `B`, select context `C`
* `^` - go up one container to domain `B`
* `D` - in the current container, domain `B`, select context `D`
* `.` - select the current container, context `D`
* `DSimple` - in the current container, select definition `DSimple`