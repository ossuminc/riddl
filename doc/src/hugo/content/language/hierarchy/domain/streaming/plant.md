---
title: "Plants"
type: "page"
draft: "false"
weight: 40
---

A RIDDL `plant` is a definition that combines [pipes](pipe) with 
[processors](processor) to specify a model of how data should flow. You may 
define as many plants as needed but each plant is a closed system without 
the ability of the RIDDL model to express the sharing of data between the 
plants. This is done deliberately to prevent unintentional contamination of 
data in large models.  

## Joints
The purpose of a plant definition is to provide the blueprint for how a set
of pipes, processors, and entities are joined together so that data may flow 
end-to-end. This is done by using:
* the names and types of inlets in processors
* the names and types of outlets in processors
* the names and content types of pipes
* the definition of a `joint` to connect pipes and processors

For example, consider this complete plant definition:
{{% code file="content/language/hierarchy/domain/streaming/riddl/plant.riddl"
language="riddl" %}}

In other words, the above plant definition produces this kind of data pipeline:
{{<mermaid align="left">}}
graph LR;

subgraph GetWeatherForecast
  subgraph Weather:outlet
  end
end
subgraph GetCurrentTemperature
  subgraph Weather:inlet
  end
  subgraph CurrentTemperature:outlet
  end
end
subgraph AttenuateSensor
  subgraph CurrentTemperature:inlet
  end
end
Weather:outlet -->|WeatherForecast| Weather:inlet
CurrentTemperature:outlet -->|TemperatureChange| CurrentTemperature:inlet
{{< /mermaid >}}

In the diagram, the arrows represent pipes, yellow boxes represent processors 
and the grey boxes represent the inlet and outlet connection points. 

## Type Safety
Plants are entirely type safe. This means the data type that a pipe 
transmits must match the data type of the publishing processors (outlets) and 
the data types of the consuming processors (inlets). When `riddlc` processes 
a plant specification, it ensures that all the inlet and outlet data types 
match the data types of the connected pipes. 

In the above example, note that each inlet/outlet pair has the same type 
name (`Weather` and `CurrentTemperature`).

## Entities as Processors
An [entity](../context/entity) may also be used as a processor under some 
special circumstances:
* _as a source_ - An entity may be used as a source of events if a command handler 
  is defined for the entity.   
* _as a sink_ - An entity may be used as a sink for events if a reaction handler 
  is defined for the entity.
* _as a flow_ - An entity may be used as a flow to convert commands into events

## Bulkheads


