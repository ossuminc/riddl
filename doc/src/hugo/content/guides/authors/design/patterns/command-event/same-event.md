---
title: "1 - Same Event"
description: "Same Event For Each Command"
date: 2022-02-25T10:50:32-07:00
draft: "false"
weight: 10
---

## Q: Should separate commands on the same entity all yield the same event, containing the exact changed entity?

### Example:

```
event UserModified {
    id: MemberId,
    info: Info,
    members: MemberId*,
    contacts: Contacts,
}
```

## Suggestions

This pattern is an anti-pattern most of the time because it results in lack of transparency. In an event driven system
it is important to be able to easily discover the corresponding event for a command or vice-versa.