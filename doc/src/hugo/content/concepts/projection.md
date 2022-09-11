---
title: "Projections"
draft: false
---

Projections get their name from  
[Euclidean Geometry](https://en.wikipedia.org/wiki/Projection_(mathematics)) 
but are probably more analogous to a 
[relational database view](https://en.wikipedia.org/wiki/View_(SQL)). The 
concept is very simple in RIDDL: projections gather data from entities and 
other sources, transform that data into a specific data format, and support
querying that data arbitrarily. 

Projections transform update events from entities into a data set that can 
be more easily queried. Projections have handlers that specify both how to
apply updates to the projections state and satisfy queries against that state.
A projection's data is always duplicate and not the system of record for the
data. Typically persistent entities are the system of record for the data. 

## Occurs In
* [Domains]({{< relref "domain.md" >}})

## Contains
* [Fields]({{< relref "field.md" >}})