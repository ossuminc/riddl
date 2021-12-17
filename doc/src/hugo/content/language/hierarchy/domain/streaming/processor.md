---
title: "Processors"
weight: 30
---

There are three basic types of processors:
* _source_ - a source of data which manufactures its data, or gets it from
  a none-pipe source, and publishes it to a pipe. Sources have no inlets and
  one or more outlet.
* _sink_ - a destination of data which consumes it from a pipe and handles 
  it in some way without further propagation. Sinks have one or more inlets 
  and no outlets.
* _flow_ - an intermediary for processing data from one pipe to another. It 
  can be regarded as the combination of a _source_ and a _sink_. Flows may have 
  one or more inlets and one or more outlets. Note that the types of data on 
  inlets and outlets may be distinct so flows are permitted to o

Because sources, sinks, and flows are characterized by their inlets and 
outlets, we don't specify which basic type they are in RIDDL. 

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


## Inlets


## Outlets

## Fan In

## Fan Out

## Syntax Example
```riddl
pipeline Plumbing is {
  pipe 
}
```
