---
title: "3 - Optional or List Parameters | Author's Guide"
description: "Entities where every parameter is optional or list"
date: 2022-02-25T10:50:32-07:00
draft: "false"
weight: 10
---

*Question:*
> Should the event for each command have a version of the entity where all 
> fields are optional, or lists, such that only changed data is sent back to
> the gateway?

### Examples:

```
type InfoUpdated {
    name: String?,
    address: Address?,
    members: MemberId*,
    isPrivate: Boolean?,
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
    contacts: Contacts,
}
```

## Suggestions

This both removes clutter and makes it easier to see exactly what is going on in each event. We are excluding the Contacts object 
from the OrganizationInfoModified event since it there is no way it will change during this operation. We are also using InfoUpdated in place 
of the original objects so that we can reduce our load - if we are modifying Info.name or Info.isPrivate, we don't need to bring the more 
complex & hefty Address object along with it because the field is now optional.

These principles apply especially to objects like Info that describe the basic details of an entity - they usually have
many more fields than shown in this example, a mixture of `collection`s, [RIDDL Compound Types]({{< relref "concepts/type.md#compounds" >}}), and [RIDDL Predefined Types]({{< relref "language/common/types.md#Predefined Types" >}}).

Anybody looking at this event would have to know that the data coming back is not the exact copy of the data but only the fields
which have been updated. This is especially pertinent in the case of `collection`s because there are no optional versions of objects (like InfoUpdated)
to indicate that there is something different going on here. 

Either way, it is advised to annotate optional versions of objects and event parameters which are `collection`s using the [`briefly` or `description` syntax]({{< relref "concepts.md#common-attributes" >}})

Note that OrganizationMembersModified is not covered here. This is because it does not have a wrapper [RIDDL Compound Types]({{< relref "concepts/type.md#compounds" >}}), as assumed by the question.

Another note to make is that if a subfield of an object is expected to change frequently this may not be the best option, especially if 
the field is a `collection` that could be very long, as described above, or otherwise potentially heavy object. Depending on how frequently 
a subfield, however nested, is expected to change, or depending on its potential load, or both, it should be given its own `Command`/`Event`
pair. This ensures it is not interrupting or interrupted by the flow of other changes being handled less frequently. The `Event` in this case
should be [surfaced]({{< relref "surfaced-parameters.md" >}}) instead.

Keep in mind that using optional versions of objects like InfoUpdated does lead to a bit more overhead, both in terms of development and in terms
of message size. Optional parameters still consume space in protobuffer messages, even if just to indicate emptiness, and modifying a data field on
a returned type would require not forgetting to modify the optional version correctly. Protobuffer message space used to indicate emptiness should
especially be taken into consideration when comparing this solution to [surfaced parameters]({{< relref "surfaced-parameters.md" >}}) instead
