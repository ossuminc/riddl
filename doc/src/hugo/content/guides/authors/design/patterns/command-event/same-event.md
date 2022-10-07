---
title: "1 - Same Event"
description: "Same Event For Each Command"
date: 2022-02-25T10:50:32-07:00
draft: "false"
weight: 10
---

*Question:*
> Should separate commands on the same entity all yield the same event, 
> containing the exact changed entity?

### Examples:

```
event UserModified {
    id: MemberId,
    info: Info,
    members: MemberId*,
    contacts: Contacts,
}
```

## Suggestions

In an event driven system it is important to have distinct events for each 
operation such that there is a structural difference in the responses. This 
pattern is advised against for diverse sets of operations, and keeping in mind
advantages of other patterns mentioned in this section.

