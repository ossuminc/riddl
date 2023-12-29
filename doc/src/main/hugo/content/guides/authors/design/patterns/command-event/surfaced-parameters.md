---
title: "4 - Surfaced Parameters | Author's Guide"
description: "Surfacing Entity Data in Events"
date: 2022-02-25T10:50:32-07:00
draft: "false"
weight: 10
---

*Question:* 
> Should the events for each command have only the fields that might have
> changed, listed individually, when replying to gateway?

### Examples:
```
event UserMembersModified {
    id: OrganizationId,
    members: MemberId*
}

command AddUserPrimaryContacts {
    userId: MemberId,
    contact: MemberId*
}

command RemoveUserPrimaryContacts {
    userId: MemberId,
    contact: MemberId*
}

event OrganizationPrimaryContactsAdded {
    id: OrganizationId
    primaryContacts: MemberId+
}

event OrganizationBillingContactsRemoved {
    id: OrganizationId
    primaryContacts: MemberId*
}
```

## Suggestions

This solution removes clutter, and offers more direct access to data than does
[optional parameters]({{< relref "optional-parameters.md" >}}). This more direct
access may be useful if parameters are changing frequently or involve a heavy
load. However, if a collection is used here, whether the data returned is the
list of modified data elements or the new `collection` of all data element is
entirely up to the developer. This decision may change for each endpoint
depending on how heavy the `Event` load can potentially be. However, one should
try to maintain consistency as much as possible.

Aside from the UserMembersModified `event`, the example `command`s and their
correlated `event`s may or may not make sense to use. It depends on how often
these commands will be used. In this case it is probably not the most fitting
solution, but if we were dealing with financial data that was constantly being
modified - say a list of stocks being used by a high-frequency trading chip - it
would make sense to have individualized `command`s for these actions. If this
is the case, note that it is unclear whether the return `event`s are intended 
to return the modified elements or the new list in its entirety. It may make
sense to return just the added elements in Added, and the new list in
Removed. No matter what, it is advised to leave annotations regarding these 
decisions using the 
[`briefly` or `description` syntax]({{< relref "concepts.md#common-attributes" >}}).

Note that this applies whether we are dealing with `collection`s of
[RIDDL Predefined Types]({{< relref "language/common/types.md#Predefined Types" >}})
or [RIDDL Compound Types]({{< relref "concepts/type.md#compounds" >}}).
