---
title: "Processors"
type: "page"
draft: "false"
weight: 30
---

As the name indicates, a RIDDL `processor` definition specifies the 
inputs and outputs of some processor of data. The inputs to the processor 
are declared with `inlet` statements and the outputs from the processor are 
declared with `outlet` statements.


## Inlets
An `inlet` statement in the definition of a processor provides the name and 
data type for an input to the processor. There can be multiple inlets to the 
processor but each one must have a separate `inlet` statement. A processor 
with no inlets defined is called a _source_ since it originates data and only has an outlet.

## Outlets

An `outlet` statement in the definition of a processor provides the name and data type for an output
of the processor. There can be multiple outlets to the processor but each noe must have a
separate `outlet` statement. A processor with no outlets is called a _sink_ since it terminates data
and only has an inlet.

## Kinds Of Processors

RIDDL supports six kinds of processors that are used as the keyword to introduce the processor. The
kind of processor depends solely on the number of inlet sand outlets that are defined. The keyword
used ensures RIDDL knows how to validate the intention for the number of inlets and outlets.

| # of Inlets | # of Outlets | Kind   | Description                                 |
|-----|------|--------|-------------------------------------------------------------|
| 0   | 1    | Source | Sources originate their data, and publish it to an outlet   |
| 1   | 0    | Sink   | Sinks terminate their data, and consume it from their inlet |
| 1   | 1    | Flow   | Flows transform their data from inlet to outlet             |
| 1   | any  | Split  | Splits their data from one source to multiple outlets       |
| any | 1    | Merge  | Merges their data from multiple intles to a single outlet   |
| any | any  | Multi  | Any other combination is a many-to-many flow                |

## Syntax Examples

```riddl
source GetWeatherForecast is {
  outlet Weather is type Forecast
} explained as "This is a source for Forecast data"

flow GetCurrentTemperature is {
  inlet Weather is type Forecast
  outlet CurrentTemp is type Temperature
} explained as "This is a Flow for the current temperature, when it changes"

sink AttenuateSensor is {
  inlet CurrentTemp is type Temperature
} explained as "This is a Sink for making sensor adjustments based on temperature"
```

The above example shows the definition of three processors of different types.
`GetWeatherForecast` is a source of data that generates weather forecast
data as `type Forecast`. Presumably, that data could be used as the input to
the flow named `GetCurrentTemperature` which takes the forecast and
puts out temperate _changes_ as indicated by the latest Forecast received.
Finally, the `AttenuateSenso` processor is a sink of data that makes
adjustments to a sensor's attenuation based on the temperature.


