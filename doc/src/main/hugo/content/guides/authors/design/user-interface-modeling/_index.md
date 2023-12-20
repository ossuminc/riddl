---
title: "User Interface Modelling"
description: "Using RIDDL To Design A User Interface"
date: 2023-12-19T10:50:32-07:00
draft: "false"
weight: 20
---

This section helps authors with the task of using RIDDL to design the user
interface for a system. RIDDL provides two kinds of definitions for this:
* _Epic_, and
* _Application_

Both _Epics_ and _Applications_ are composed of more granular things but
at this point it is important to understand how they are combined to
define the user interface.  Epics allow you to specify the interaction
between the user (human or otherwise) of the system, and the system
itself. Applications represent the physical or virtual machinery that
the user can manipulate to achieve some objective with the system. The diagram
below shows how Epics and Applications inter-relate.

{{< mermaid class="text-center" >}}
graph TB;
  User(User Of The Application);
  UI(Riddl Application Definition);
  System(Other Riddl Definition);

  subgraph Application
    UI--sends messages to-->System;
    System--responds with messages to-->UI;
  end;

  subgraph Epic
    User--interacts with-->UI;
    UI--displays results to-->User;    
  end;
{{< /mermaid >}}

{{< toc-tree >}}
