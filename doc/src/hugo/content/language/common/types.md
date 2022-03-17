---
title: "Types"
type: "page"
draft: "false"
weight: 30
---

## Introduction
The RIDDL language allows users to define data types. Definitions of types
are more limited in RIDDL than in programming languages on purpose. The
type system must be easily understandable by non-programmers as the domain
engineer or domain expert is likely the most frequent user. 

## Predefined Types
RIDDL supports several predefined types that just "exist" because they are
fundamental and well understood in any targeted computing environment. These 
predefined type names can be used anywhere that a type definition is needed,
for example in a field of an entity's state definition 
[see here](domain/context/entity/state)

The predefined  types are: 
* **String** - a sequence of characters of any length 
* **Boolean** - a binary value for true/false logic
* **Number**  - a numeric value either integer or decimal
* **Integer** - a numeric value that excludes fractional parts
* **Decimal** - a numeric value that includes fractional parts
* **Real** - a real number
* **Id** - a globally unique identifier
* **Date** - a date value
* **Time** - a time of day value
* **DateTime** - a date and a time value together
* **TimeStamp** - a date and time combined with at least millisecond accuracy
* **Duration** - the amount of time between a start and stop time
* **LatLong** - a position on earth
* **Nothing** - a type that cannot hold a value
* **URL** - a uniform resource locator of any scheme


## Named Type Definitions
In addition to the predefined types, RIDDL supports the definition of new 
types using a name and a type expression with this syntax:
```riddl
type name = <type-expression>
```

When defining values, one must use a named type defined with the 
`type` keyword. This enforces legibility by naming every type expression.
 
## Type Expressions
RIDDL supports a variety of type expressions for defining named types. The
following sections define the kinds of expressions allowed.

### Renames
It is possible to rename a predefined or previously defined type as another 
type name.  This is common to increase domain applicability of the name of 
a predefined type. For example,
```riddl
type FirstName = String // rename a predefined type for clarity
```
might be used to make it clear that the intended use of the `String` value
is to provide a person's first name.

### Bounded Strings
The predefined types allow use of unbounded strings but when you want to
fix the minimum or maximum length of a string, it may only be used in a type
expression. The expression `String(<min>, <max>)` can be used to define such 
a type. Both `<min>` and `<max>` are optional. `<min>` defaults to 0, and 
`max` defaults to infinite. For example;
```riddl
type FirstName = String(2, 30) // string between 2 and 30 chars inclusive
type LastName = String(,30) // string between 0 and 30 chars
type Unbounded = String(,) // unbounded, same as just "String"
```

### Patterns
When you need a string to conform to a regular expression, you can use the 
Pattern type expression. The expression `Pattern(<regex>)` will define a 
string that validates its content according to `<regex>` which must be a 
quoted Scala regular expression.  If assignment to the string does not match 
the`<regex>` then an `InvalidateEstateException` will be generated. For example,
here's a pattern for extracting the three components of a North American 
telephone number: 
```riddl
type NATelephoneNumber = pattern("\(?([0-9]{3})\)?-?([0-9]{3})-?([0-9]{4})")
```

### Range
The predefined types allow the use of unbounded integers but when you want 
to constrict the range of values, you need a `Range` type expression.  The 
expression `Range(<min>,<max>)` will define an integer value whose range is 
restricted to the `<min>` and `<max>` values provided.  As with [bounded 
strings](./types#Bounded_Strings), the `<min>` and `<max>` values are 
optional and default to 0 and infinity respectively. For example:
```riddl
type Percent = range(,100) // only value 0 to 100 inclusive
```

### Restricted Scheme URL
The predefined types allow the use of any URL, but when you want to 
restrict the URL to a specific scheme (e.g. "http", "mailto") then you can 
use the URL type expression. The expression `URL(<scheme>)` specifies a URL 
that is restricted to the `<scheme>` specified. For example:
```riddl
type HTTPS_URL = url("https")
```

### Unique Identifier
To define a type that uniquely identifies a runtime entity the `Id` type 
expression can be used.  It requires a `pathIdentifier` parameter which 
specifies the full path (from the root domain) to the runtime entity.  For 
example:
```riddl
type ModelXRef = Id(Autos.Tesla.ModelX)
```

### Enumeration
An enumeration defines a type that may take the value of one identifier from a
closed set of constant identifiers using the `any` keyword and the set of
identifiers enclosed in square brackets, like this:
```
type Color = any of [Red, Orange, Yellow, Green, Blue, Indigo, Violet]
```

### Alternation
A type can be defined as any one type chosen from a set of other type names 
using the `select` keyword followed by type names separated by `|`, like this:
 
```
type References = select String | URL
```

There must be at least two types in an alternation. 

### Aggregation
A type can be defined as an aggregate of a group of values of types. DDD calls
this a "value object".  Aggregations can be nested, even recursively. Each 
value in the aggregation has an identifier (name) and a type separated by a 
colon. For example, here is the type definition for a rectangle located on a 
Cartesian coordinate system at point (x,y) with a given height and width:
```
type Rectangle = { x: Number, y: Number, height: Number, width: Number }
```

### Key/Value Mapping
A type can be defined as a mapping from one type (the key) to another type 
(the value). For example, here is a dictionary definition that maps a word 
(lower case letters) to a type named DictionaryEntry that presumably 
contains all the things one would find in a dictionary entry. 
```riddl
type dictionary = mapping from Pattern("[a-z]+") to DictionaryEntry
```

### Messages
An aggregate type (_value object_ in DDD) can be declared to be one of four
kinds of message types using the `command`, `event`, `query`, and `result` 
keywords. These type definitions are useful for sending messages to 
[entities](../hierarchy/domain/context/entity) or across
[pipelines](../hierarchy/domain/pipeline).  

For example, here is a command definition:
```riddl
type JustDoIt = command { id: Id(AnEntity), encouragement: String, swoosh: URL }
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
type MyType = { ids: Id+, name: String? }
```
the `MyType` type is an aggregate that contains one or more Id values 
in the `ids` field and an optional string value in `name`
