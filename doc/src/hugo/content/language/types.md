---
title: "Types"
weight: 30
---

# Introduction
The RIDDL language allows users to define data types. Definitions of types
are more limited in RIDDL than in programming languages on purpose. The
type system must be easily understandable by non-programmers as the domain
engineer or domain expert is likely the most frequent user. 

# Literal Types
RIDDL supports several literal types that just "exist" because they are
fundamental and well understood in any targeted computing environment. The 
literal types are:

* **Boolean** - a binary value for true/false logic
* **String** - a sequence of characters with a finite length
* **Number**  - a numeric value either integer or decimal
* **Integer** - a numeric value that excludes fractional parts
* **Decimal** - a numeric value that includes fractional parts 
* **Id** - a globally unique identifier
* **Date** - a date value
* **Time** - a time of day value
* **DateTime** - a date and a time value together
* **TimeStamp** - a date and time combined with at least millisecond accuracy
* **URL** - a uniform resource locator

# Named Type Definitions
In addition to the literal types, RIDDL supports the definition of new 
types using a name and a type expression with this syntax:
```
type name = <type-expression>
```
When defining values, one must use a named type defined with the 
`type` keyword. This enforces legibility by naming every type expression.
 
## Type Expressions
RIDDL supports a variety of type expressions for defining named types. The
following sections define the kinds of expressions allowed.

## Renames
It is possible to simply rename a type as another type name. This is common
to increase domain applicability of the name of a literal type. For example,

```
type FirstName = String
```

might be used to make it clear that the intended use of the `String` value 
is to provide a person's first name. 

## Enumerations
Enumerations define type that may take the value of one identifier from a
closed set of constant identifiers using the `any` keyword and the set of
identifiers enclosed in square brackets, like this:

```
type Color = any [Red, Orange, Yellow, Green, Blue, Indigo, Violet]
```

## Alternations
A type can be defined as any one of a set of other type names using the
`select` keyword followed by type names separated by `|`, like this:
 
```
type References = select String | URL
```

There must be at least two types in an alternation.

## Aggregations
A type can be defined as an aggregate of a group of values of other types.
Such aggregations can be nested, even recursively. For example, here is
a type definition for a rectangle located on a Cartesian coordinate system
at (x,y) with a given height and width:

```
type Rectangle = x: Number, y: Number, height: Number, width: Number
```
    
## Cardinality
A type expression can be adorned with a symbol (adopted from regular
expressions) that specifies the cardinality of the type, as follows:
* `?` - optional, 0 or 1 instances
* `*` - zero or more instances
* `+` - one or more instances
Without the adornment, the cardinality is "required" (exactly one).
For example, in this:
```
type MyType = ids: Id+, name: String?
```
the `MyType` type is an aggregate that contains one or more Id values 
in the `ids` field and an optional string value in `name`
