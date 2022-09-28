---
title: "Command/Event Patterns"
description: "Alternatives & Recommendations for Command Handler Messages"
date: 2022-02-25T10:50:32-07:00
draft: "false"
weight: 10
---

## Introduction To Messages
If you are not familiar with the concept of `Messages` as it pertains to RIDDL, see [here](../../../../../concepts/message/).
This document pertains only to Commands and Events.

## Problem Statement
There are implementation designs to be made regarding the arrangement of input (`Command`) and output (`Event`) data 
across [`Command Handlers`](../../../../../concepts/handler#Command%20Handler). This documents pertains all types of `Command Handlers`.

These decisions can be summarized in 4 alternatives:
1. Should separate commands on the same entity all yield the same event, containing the exact changed entity?
2. Or should each command have its own event, containing the exact changed entity?
3. Or should the event for each command have a version of the entity where all fields are optional, 
such that only changed data is sent back to gateway?
4. Or should the events for each command have only the fields that might have changed, listed individually, when replying to gateway?

## Recommendations & Justifications

1. This would only ensure greater obscurity in terms of being able to debug from outside - if the wrong data comes 
back in the event, it is easier to detect a wrong event name.
2. All commands should have unique corresponding events. They should only return the exact new version of the entity 
on entity creation commands because all subsequent changes should have their own specific commands
3. Versions of entities where all fields are optional should be used only for commands that are ensured to modify 
[RIDDL Predefined Types](../../../../../language/common/types#Predefined%20Types) in the entity, except for lists. 
This is because non-list Predefined Types are ensured to be lightweight objects.
4. For all other commands, ie. commands that are ensured to modify lists or 
[RIDDL Compound Types](../../../../../concepts/type#compounds), the modified data should be surfaced as individual 
parameters of the event corresponding to the command