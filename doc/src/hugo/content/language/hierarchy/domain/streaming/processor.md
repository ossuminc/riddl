---
title: "Processors"
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
with no inlets defined is called a _source_ since it originates data and 
only has an outlet. 

## Outlets
An `outlet` statement in the definition of a processor provides the name and 
data type for an output of the processor. There can be multiple outlets to 
the processor but each noe must have a separate `outlet` statement. A 
processor with no outlets is called a _sink_ since it terminates data and 
only has an inlet.

## Syntax Examples
```riddl
processor GetWeatherForecast is {
  outlet Weather is type Forecast
} explained as "This is a source for Forecast data"

processor GetCurrentTemperature is {
  inlet Weather is type Forecast
  outlet CurrentTemp is type Temperature
} explained as "This is a Flow for the current temperature, when it changes"

processor AttenuateSensor is {
  inlet CurrentTemp is type Temperature
} explained as "This is a Sink for making sensor adjustments based on temperature"
```
The above example shows the definition of three processors.
`GetWeatherForecast` is a source of data that generates weather forecast
data as `type Forecast`. Presumably, that data could be used as the input to
the flow named `GetCurrentTemperature` which takes the forecast and
puts out temperate _changes_ as indicated by the latest Forecast received.
Finally, the `AttenuateSenso` processor is a sink of data that makes
adjustments to a sensor's attenuation based on the temperature.


## Common Patterns
There are several patterns that emerge for processors based on the number of 
inlets and outlets the processor has. These patterns are not declared 
directly in RIDDL but inferred from the `inlet` and `outlet` statements in 
the definition of a processor.

|# of Inlets|# of Outlets|Pattern| Description                                                 |
|-----------|------------|-------|-------------------------------------------------------------|
|0|1|Source| Sources originate their data, and publish it to an outlet   |
|1|0|Sink| Sinks terminate their data, and consume it from their inlet |
|1|1|Flow| Flows transform their data from inlet to outlet             |
|N|1|Fan In| Fans in their data from multiple sources to a single outlet |
|1|N|Fan Out| Fans out their data from one source to multiple outlets|

