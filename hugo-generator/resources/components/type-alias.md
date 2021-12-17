---
title: "{{ replace .Name "-" " " | title }}"
date: {{ .Date }}
draft: false
---

# {{typeName}}
#### Type: Alias

The type **{{typeName}}** is defined as an _alias_ to the primitive type `{{typeType}}` within the region _{typeRegion}}_.

This alias was defined in the RIDDL source file '_{{typeSourceFile}}_' like so:
```riddl
type {{typeName}} is {{typeType}}  
```
