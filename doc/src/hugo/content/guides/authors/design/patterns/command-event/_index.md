---
title: "Command/Event Patterns"
description: "Alternatives & Recommendations for Command Handler Messages"
date: 2022-02-25T10:50:32-07:00
draft: "false"
weight: 10
---

## Introduction To Messages
If you are not familiar with the concept of `Messages` as it pertains to RIDDL, see [here]({{< relref "concepts/message.md" >}}).
This document pertains only to Commands and Events.

## Backend v Gateway
This design spec considers only designing services themselves. As such it ignores usability features like fine-grained command
parameters for updating data, unless justified. In a gateway service more consideration would have to be made for usability.

## Problem Statement
There are implementation designs to be made regarding the arrangement of input (`Command`) and output (`Event`) data 
across `Command` [`Handlers`]({{< relref "concepts/handler.md">}}). This documents pertains all types of `Command Handlers`.

These decisions can be summarized in 4 alternatives:
{{< toc-tree >}}

In this section we will be trying to figure out how to structure commands for creating, modifying, and deleting 
fields on User entities. This applies to State based entities as well.

### Here are the riddl specs we will be assuming for each example (borrowed from improving-riddl):

```
entity Organization is {
    state Active is {
        info: Info,
        members: MemberId*,
        contacts: Contacts,
    }
}

type Contacts is {
    primaryContacts: MemberId+,
    billingContacts: MemberId*,
    distributionContacts: MemberId*
}

type Info is {
    name: String,
    address: Address?,
    isPrivate: Boolean,
}

type OrganizationId is Id (Organization)
type MemberId is Id (Member) briefly "Member is not relevant here, so it will not be shown here."

type Address is {
    line1: String,
    line2: String,
    city: String,
    stateProvince: String,
    country: String,
    postalCode: PostalCode
}

type MobileNumber is Pattern("\(([0-9]{3})\)([0-9]{3})-([0-9]{4})") described as "North American Phone Number standard"

type EmailAddress is Pattern("(?:[a-z0-9!#$%&'*+/=?^_`{|}~-](?:\.[a-z0-9!#$%&'*+/=?^_`{|}~-])|\"(?:[\x01-\x08\x0b\x0c\x0e-\x1f\x21\x23-\x5b\x5d-\x7f]|\\[\x01-\x09\x0b\x0c\x0e-\x7f])\")@(?:(?:[a-z0-9](?:[a-z0-9-][a-z0-9])?\.)+[a-z0-9](?:[a-z0-9-][a-z0-9])?|[(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?|[a-z0-9-]*[a-z0-9]:(?:[\x01-\x08\x0b\x0c\x0e-\x1f\x21-\x5a\x53-\x7f]|\\[\x01-\x09\x0b\x0c\x0e-\x7f])+)])") described as "RFC 5322 official standard. See https://emailregex.com"

type CAPostalCode is Pattern("[A-Z]\d[A-Z]\d[A-Z]\d")

type USPostalCode is Pattern("\d{5}(-\d{4})?")

type PostalCode is one of { CAPostalCode, USPostalCode }

### We will be trying to find Events for the following Commands:

command EstablishOrganization {
    info: Info,
    contacts: Contacts
}

command ModifyUserInfo {
    id: OrganizationId,
    info: Info
}

command AddMembersToUser {
    userId: OrganizationId,
    members: MemberId*
}

command ModifyUserContacts {
    userId: MemberId,
    alternate: Alternate
}
```