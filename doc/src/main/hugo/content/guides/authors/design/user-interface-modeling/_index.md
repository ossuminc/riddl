---
title: "User Interface Modelling"
description: "Using RIDDL To Design A User Interface"
date: 2023-12-19T10:50:32-07:00
draft: "false"
weight: 20
---

## Overview
This section helps authors with the task of using RIDDL to design the user
interface for a system. RIDDL provides two kinds of definitions for this:
[_Epics_]({{< relref "../../../../../concepts/epic.md">}}), and 
[_Application_]({{< relref "../../../../../concepts/application.md">}})

Both _Epics_ and _Applications_ are composed of more granular things but
at this point it is important to understand how they are combined to
define the user interface.  Epics allow you to specify the interaction
between the user (human or otherwise) and the system being modeled in RIDDL.
Applications represent the physical or virtual machinery that
the user can manipulate to achieve some objective with the system. The diagram
below shows how Epics and Applications inter-relate.

{{< mermaid class="text-center" >}}
graph TB;
    User( fas:fa-user-tie <br/> User);
    UI( fas:fa-tablet-alt <br/> Application);
    System( fas:fa-microchip <br/> System);

    UI--sends messages to-->System;
    System--responds with messages to-->UI;
    User--interacts with-->UI;
    UI--displays results to-->User;    
{{< /mermaid >}}

## Example

To clarify the concepts introduced in this section, a relatively simple
example from an e-commerce shopping cart will be constructed as you 
work through the material.  This example roughly translates to the
following user journey:
* User selects items from a display of products 
* Selected items are placed in the user's shopping cart
* The shopping cart contents is displayed
* The user drops an item from the shopping cart
* The user orders the contents of the shopping cart
* The user enters payment details and shipping address
* The system confirms the order information.

In the following pages, we will show how to write this sequence
of interactions
{{< toc-tree >}}
