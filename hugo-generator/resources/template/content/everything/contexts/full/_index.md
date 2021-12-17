---
title: "Full"
weight: -20
---

#### _Bounded Context_

## Description
Domains are composed of bounded contexts so...
Also, contexts can contain many kinds of definitions!

## Types
The following types are defined within this bounded-context:

* [str](types/str) (_alias_)
* [num](types/num) (_alias_)
* [boo](types/boo) (_alias_)
* [indent](types/indent) (_alias_)
* [dat](types/dat) (_alias_)
* [tim](types/tim) (_alias_)
* [stamp](types/stamp) (_alias_)
* [url](types/url) (_alias_)
* [PeachType](types/peachtype) (_record_)
* [enum](types/enum) (_enumeration_)
* [alt](types/alt) (_enumeration_)
* [agg](types/agg) (_record_)
* [oneOrMore](types/oneormore) (_non-empty collection of_ [**agg**](types/agg))
* [zeroOrMore](types/zeroormore) (_collection of_ [**agg**](types/agg))
* [optional](types/optional) (_optional of_ [**agg**](types/agg))

## Entities
The following entities are defined within this bounded-context:

* [Something](entities/something) - Entities are the main objects that contexts define. They can be...