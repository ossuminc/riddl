---
title: "Commands"
---

# Introduction

Commands are the input to the system. They cause a change in state
The change in state is represented by an Event. These events propagate through the
system and in turn, may cause additional changes in state.
Generally, all Events can be traced back to a command (if you go back far enough).
The command does not return anything, however, it issues an event.

For example, a user makes reservations via reservation context.
These commands change the reservation status and create events.
