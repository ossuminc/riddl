---
title: "4 - Surfaced Parameters"
description: "Surfacing Entity Data in Events"
date: 2022-02-25T10:50:32-07:00
draft: "false"
weight: 10
---

# Optional Versioning

## Q: Should the events for each command have only the fields that might have changed, listed individually, when replying to gateway?

### Example:

event UserMembersModified {
    id: OrganizationId,
    members: MemberId*
}

## Suggestions

This solution removes clutter, and offers more direct access to data than does [optional parameters]({{< relref "optional-parameters.md" >}}).
This more direct access may be useful if parameters are changing frequently or involve a heavy load. However, if a collection is used here,
whether the data returned is the list of modified data elements or the new `collection` of all data element is entirely
up to the developer. This decision may change for each endpoint depending on how heavy the `Event` load can potentially be. However,
one should try to maintain consistency as much as possible.

Note that this applies whether we are dealing with `collection`s of [RIDDL Predefined Types]({{< relref "language/common/types.md#Predefined Types" >}}) or [RIDDL Compound Types]({{< relref "concepts/type.md#compounds" >}}).