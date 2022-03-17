---
title: "Projections"
type: "page"
draft: "false"
weight: 30
---

# Introduction

A projection is a read-only view of entity information. Projections are necessary since entities use
event sourcing which is not a query-friendly format.

Here's the projection process. Usually, events are logged as they are kept appended at the end of
the log file. Logs are string and text. To retrieving meaningful information out of logs, logs are
transformed into a more query-friendly format and stored in queriable repository or DB.

![CQRS/ES](../../../../static/images/cqrs-es.png "CQRS/ES")
