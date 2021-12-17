---
title: "{{ replace .Name "-" " " | title }}"
date: {{ .Date }}
draft: false
---

# {{typeName}}
#### Type: Record (aggregation)

The type **{{typeName}}** is defined as a _record_ various named fields within the region _{typeRegion}}_.
The record defines the following _named fields_:

### Named Fields
{{typeFields}}