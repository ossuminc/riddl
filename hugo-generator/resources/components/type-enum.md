---
title: "{{ replace .Name "-" " " | title }}"
date: {{ .Date }}
draft: false
---

# {{typeName}}
#### Type: Enumeration

The type **{{typeName}}** is defined as an _enumeration_ of possible types within the region _{typeRegion}}_.
The value of this type must be one of the following _possible values_:

### Possible Values
{{typeValues}}