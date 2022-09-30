---
title: "3 - Optional Parameters"
description: "Entities where every parameter is optional"
date: 2022-02-25T10:50:32-07:00
draft: "false"
weight: 10
---

## Q: Should the event for each command have a version of the entity where all fields are optional,
such that only changed data is sent back to gateway?

### Example:

```
type InfoUpdated {
    name: String?,
    address: Address?,
    members: MemberId*,
    isPrivate: Boolean?,
}

type ContactsUpdated is {
    primaryContacts: MemberId+,
    billingContacts: MemberId*,
    distributionContacts: MemberId*
}

event OrganizationEstablished {
    id: OrganizationId
    info: Info,
    contacts: Contacts,
}

event OrganizationInfoModified {
    id: OrganizationId
    info: InfoUpdated,
}

event UserContactsModified {
    id: OrganizationId
    contacts: ContactsUpdated,
}
```

## Suggestions

This both removes clutter and makes it easier to see exactly what is going on in each event. We are excluding the Contacts object 
from the event since it there is no way it will change when an Info field is changed. We are also using InfoUpdated and 
ContactsUpdated in place of the original objects so we can also reduce our load - if we are modifying Info.name or Info.isPrivate,
we don't need to bring the more complex & hefty Address object along with it because the field is now optional.

These principles apply especially to object like Info that describe the basic details of an entity - they usually have
many more fields than shown in this example, a mixture of `collection`s, [RIDDL Compound Types]({{< relref "concepts/type.md#compounds" >}}), and [RIDDL Predefined Types]({{< relref "language/common/types.md#Predefined Types" >}}).

Any client consuming this event would have to know that the data coming back is not the exact copy of the data but only the fields
which have been updated. We believe this is an easy enough inference to make for most developers.

Note that OrganizationMembersModified is not covered here. This is because it does not have a wrapper [RIDDL Compound Types]({{< relref "concepts/type.md#compounds" >}}), as assumed by the question.

Another note to make is that if a subfield of an object is expected to change frequently this may not be the best option, especially if 
the field is a heftier object like a `collection` that could be very long or an image. Depending on how frequently a subfield, however nested, 
is expected to change, or depending on its potential load, or both, it should be given its own `Command`/`Event` pair. This ensures it is not
interrupting or interrupted by the flow of other changes being handled less frequently. The `Event` in this case should be [surfaced]{{{< relref "surfaced-parameters.md" >}}}
instead.