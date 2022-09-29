---
title: "2 - Same Parameters"
description: "Events with Same Parameters"
date: 2022-02-25T10:50:32-07:00
draft: "false"
weight: 10
---

# Optional Versioning

## Q: Should each command have its own event, containing the exact changed entity?

### Example:

event OrganizationEstablished {
    id: OrganizationId
    info: Info,
    members: MemberId*,
    contacts: Contacts,
}

event OrganizationInfoModified {
    id: OrganizationId
    info: Info,
    members: MemberId*,
    contacts: Contacts,
}

event OrganizationMembersModified {
    id: OrganizationId
    info: Info,
    members: MemberId*,
    contacts: Contacts,
}

event OrganizationContactsModified {
    id: OrganizationId
    info: Info,
    members: MemberId*,
    contacts: Contacts,
}

## Suggestions

This affords much more traceability, but there is a lot of extraneous data coming back in non-creation events. While it makes sense
to return the entire entity in UserEstablished as it was just created and has never been received from backend where data could 
have been added automatically, the other events could have their parameters pared down a bit. 