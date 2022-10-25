---
title: "Types"
draft: false
---

## Introduction
The RIDDL language allows users to define types of data, or information. RIDDL's
type system is fairly rich, for a specification language, providing 
abstractions for many concretely common information structures. This is done to
make it easier for domain engineers and experts to understand the models they
are creating. 

A *type* defines the shape of some information. There are many kinds of type
definitions allowed, so we have grouped them into categories:

{{< toc >}}

## Predefined Types {#prefdefined}
RIDDL supports several predefined types that just "exist" because they are:
* applicable to nearly all fields of study or knowledge domains
* fundamental in nature, covering the [SI base units](https://en.wikipedia.org/wiki/SI_base_unit)
* fundamental in business, covering basic financial quantities such as currency
* easily represented in any computing environment

RIDDL inherently knows about these predefined types so to use them you just 
use their name, no further definition is required. Here are the 
simple predefined types:

### Simple Predefined Types {#simple}

| Name        | Description                                                       |
|-------------|-------------------------------------------------------------------|
| Abstract    | An unspecified, arbitrary type, compatible with any other type    |
| Nothing     | A type that cannot hold any value, commonly used as a placeholder |
| Boolean     | A Boolean value, with values true or false                        |
| Current     | An SI unit of electric current, measured in Amperes               |
| Date        | A date value comprising a day, month and year                     |
| DateTime    | A combination of Date and Time                                    |
| Duration    | An amount of time, measured in SI units of seconds                |
| Length      | An SI unit of distance measured in meters                         |
| Luminosity  | An SI unit of luminous intensity, measured in candelas            |
| Mass        | An SI unit of mass measured in kilograms                          |
| Mole        | An SI unit of an amount of substance, measured in mol             |
| Number      | An arbitrary number, integer, decimal or floating point           |
| String      | A sequence of Unicode characters                                  |
| Temperature | An SI unit of thermodynamic temperature, measured in Kelvin       |
| Time        | A time value comprising an hour, minute, second and millisecond   |
| TimeStamp   | A fixed point in time                                             |
| UUID        | A randomly unique identifier with low likeliness of collision     |

### Parameterized Predefined Types {#parameterized}
Some predefined types take parameters to customize their content, we 
call these *parameterized predefined types*.

| Name      | Parameters           | Description                                                   |
|-----------|----------------------|---------------------------------------------------------------|
| String    | (`min`,`max`, `enc`) | A String, as above, of a specific length range and encoding.  |
| Id        | (`entity`)           | A unique identifier for a kind of entity given by `entity`    |
| URL       | (`scheme`)           | A URL for a specific URL scheme (e.g. `http`)                 |
| Range     | (`min`,`max`)        | A integer from `min` to `max`                                 |
| LatLong   | (`lat`, `long`)      | A location based on latitude and longitude                    |
| Currency  | (`country-code`)     | The currency of a nation using ISO 3166 country codes         |
| Pattern   | (`regex`)            | A string value that conforms to a regular expression, `regex` |

## Compounds
Compound types add structure around the predefined types and require further
definition in RIDDL.  

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

### Key/Value Mapping {#mapping}
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
[entities]({{< relref "entity.md" >}}) or across 
[pipes]({{< relref "pipe.md" >}}). 

For example, here is a command definition:
```riddl
type JustDoIt = command { id: Id(AnEntity), encouragement: String, swoosh: URL }
```

### Cardinality
You can use a cardinality suffix or prefix with any of the type expressions 
defined above to transform that type expression into the element type of 
a collection.

#### Suffixes
The suffixes allowed are adopted from regular expression syntax with the 
following meanings:

| Suffix | Meaning                                                 |
|--------|---------------------------------------------------------|
 | ` `    | Required: exactly 1 instance of the preceding type      |
| `?`    | Optional: either 0 or 1 instances of the preceding type |
| `*`    | Zero or more instances of the preceding type            |
| `+`    | One or more instances of the preceding type             |
| `...`  | One or more instances of the preceding type             |
| `...?` | Zero or more instances of the preceding type            |

Note the empty first item in the table; without the suffix, the 
cardinality of a type expression is "required" (exactly one).
For example, in this:
```
type MyType = { ids: Id+, name: String? }
```
the `MyType` type is an aggregate that contains one or more Id values
in the `ids` field and an optional string value in `name`

#### Prefixes
The prefixes allowed have a similar meaning to the suffixes:

| Suffix        | Meaning                                                   |
|---------------|-----------------------------------------------------------|
| required      | Required: exactly 1 instance of the preceding type        |
| optional      | Optional: either 0 or 1 instances of the preceding type   |
| many          | Zero or more instances of the preceding type              |
| many required | One or more instances of the preceding type               |

## Occurs In
All [Vital Definitions]({{< relref vital.md >}}) 

## Contains
* [Fields]({{< relref "field.md" >}}) (in aggregations only)
