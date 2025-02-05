/*
 * Copyright 2019-2025 Ossum, Inc.
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
    KnownOption.technology,
    KnownOption.css,
    KnownOption.kind,
    KnownOption.faicon
  )

  final val domain: Seq[String] = Seq(
    KnownOption.external,
    KnownOption.package_,
    KnownOption.technology,
    KnownOption.css,
    KnownOption.kind,
    KnownOption.faicon
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
    KnownOption.faicon
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
    KnownOption.kind
  )

  final val repository: Seq[String] = Seq(
    KnownOption.technology,
    KnownOption.kind,
    KnownOption.css,
    KnownOption.faicon
  )

  final val saga: Seq[String] = Seq(
    KnownOption.technology,
    KnownOption.kind,
    KnownOption.css,
    KnownOption.faicon,
    KnownOption.parallel,
    KnownOption.sequential
  )

  final val streamlet: Seq[String] = Seq(
    KnownOption.technology,
    KnownOption.css,
    KnownOption.kind
  )
}

object KnownOption {
  final val aggregate = "aggregate"
  final val async = "async"
  final val available = "available"
  final val concept = "concept"
  final val consistent = "consistent"
  final val css = "css"
  final val device = "device"
  final val external = "external"
  final val event_sourced = "event-sourced"
  final val faicon = "faicon"
  final val finite_state_machine = "finite-state-machine"
  final val gateway = "gateway"
  final val kind = "kind"
  final val message_queue = "message-queue"
  final val package_ = "package"
  final val parallel = "parallel"
  final val persistent = "persistent"
  final val reply = "reply"
  final val sequential = "sequential"
  final val service = "service"
  final val sync = "sync"
  final val tail_recursive = "tail-recursive"
  final val value = "final value"
  final val wrapper = "wrapper"
  final val technology = "technology"
  final val transient = "transient"
  final val user = "user"

}
