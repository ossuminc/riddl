/*
 * Copyright 2019-2026 Ossum Inc.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ossuminc.riddl.language

import scala.scalajs.js.annotation.JSExportTopLevel

/** AN object that defines the names of the known options */
@JSExportTopLevel("KnownOptions")
object KnownOptions {

  final val adaptor: Seq[String] = Seq(
    KnownOption.technology,
    KnownOption.kind,
    KnownOption.css,
    KnownOption.faicon
  )

  final val application: Seq[String] = Seq(
    KnownOption.technology,
    KnownOption.kind,
    KnownOption.css,
    KnownOption.faicon
  )

  final val connector: Seq[String] = Seq(
    KnownOption.persistent,
    KnownOption.technology,
    KnownOption.kind
  )

  final val context: Seq[String] = Seq(
    KnownOption.wrapper,
    KnownOption.gateway,
    KnownOption.service,
    KnownOption.package_,
    KnownOption.namespace,
    KnownOption.technology,
    KnownOption.css,
    KnownOption.kind,
    KnownOption.faicon,
    KnownOption.protocol,
    KnownOption.event_catalog_version,
    KnownOption.sql_dialect,
    KnownOption.backstage_owner,
    KnownOption.backstage_lifecycle,
    KnownOption.backstage_type
  )

  final val domain: Seq[String] = Seq(
    KnownOption.external,
    KnownOption.package_,
    KnownOption.namespace,
    KnownOption.technology,
    KnownOption.css,
    KnownOption.kind,
    KnownOption.faicon,
    KnownOption.event_catalog_version,
    KnownOption.sql_dialect,
    KnownOption.backstage_owner,
    KnownOption.backstage_lifecycle,
    KnownOption.backstage_type,
    KnownOption.confluence_space,
    KnownOption.confluence_parent
  )

  final val entity: Seq[String] = Seq(
    KnownOption.event_sourced,
    KnownOption.value,
    KnownOption.aggregate,
    KnownOption.transient,
    KnownOption.consistent,
    KnownOption.available,
    KnownOption.finite_state_machine,
    KnownOption.kind,
    KnownOption.message_queue,
    KnownOption.technology,
    KnownOption.css,
    KnownOption.faicon,
    KnownOption.protocol,
    KnownOption.sql_dialect,
    KnownOption.sql_table,
    KnownOption.backstage_owner,
    KnownOption.backstage_lifecycle,
    KnownOption.backstage_type
  )

  final val epic: Seq[String] = Seq(
    KnownOption.technology,
    KnownOption.css,
    KnownOption.sync,
    KnownOption.kind,
    KnownOption.faicon
  )

  final val projector: Seq[String] = Seq(
    KnownOption.technology,
    KnownOption.css,
    KnownOption.faicon,
    KnownOption.kind,
    KnownOption.protocol,
    KnownOption.backstage_owner,
    KnownOption.backstage_lifecycle,
    KnownOption.backstage_type
  )

  final val repository: Seq[String] = Seq(
    KnownOption.technology,
    KnownOption.kind,
    KnownOption.css,
    KnownOption.faicon,
    KnownOption.protocol,
    KnownOption.sql_dialect,
    KnownOption.sql_table,
    KnownOption.backstage_owner,
    KnownOption.backstage_lifecycle,
    KnownOption.backstage_type
  )

  /** Options advertised for a Saga. A saga is SEQUENTIAL by definition, so
    * [[KnownOption.sequential]] is deliberately NOT listed here — an option to request the default
    * behaviour is redundant (A11). The constant itself is retained as public API.
    */
  final val saga: Seq[String] = Seq(
    KnownOption.technology,
    KnownOption.kind,
    KnownOption.css,
    KnownOption.faicon,
    KnownOption.protocol,
    KnownOption.parallel,
    KnownOption.compensate
  )

  final val streamlet: Seq[String] = Seq(
    KnownOption.technology,
    KnownOption.css,
    KnownOption.kind,
    KnownOption.protocol
  )

  /** Options recognized on a portlet (Inlet or Outlet). `async` marks a portlet as a codegen async
    * boundary (anti-fusion); see RecognizedOptions.registry.
    */
  final val portlet: Seq[String] = Seq(
    KnownOption.async
  )
}

object KnownOption {
  final val aggregate = "aggregate"
  final val async = "async"
  final val available = "available"
  final val backstage_lifecycle = "backstage_lifecycle"
  final val backstage_owner = "backstage_owner"
  final val backstage_type = "backstage_type"
  final val compensate = "compensate"
  final val concept = "concept"
  final val confluence_parent = "confluence_parent"
  final val confluence_space = "confluence_space"
  final val consistent = "consistent"
  final val css = "css"
  final val device = "device"
  final val external = "external"
  final val event_catalog_version = "event_catalog_version"
  final val event_sourced = "event-sourced"
  final val faicon = "faicon"
  final val finite_state_machine = "finite-state-machine"
  final val gateway = "gateway"
  final val kind = "kind"
  final val message_queue = "message-queue"
  final val namespace = "namespace"
  final val package_ = "package"
  final val parallel = "parallel"
  final val persistent = "persistent"
  final val protocol = "protocol"
  final val reply = "reply"
  final val sequential = "sequential"
  final val service = "service"
  final val sql_dialect = "sql_dialect"
  final val sql_table = "sql_table"
  final val sync = "sync"
  final val tail_recursive = "tail-recursive"
  final val value = "final value"
  final val wrapper = "wrapper"
  final val technology = "technology"
  final val transient = "transient"
  final val user = "user"

}
