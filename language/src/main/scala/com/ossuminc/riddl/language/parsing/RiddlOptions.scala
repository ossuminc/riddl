package com.ossuminc.riddl.language.parsing

import fastparse.*
import MultiLineWhitespace.*
import Keywords.{keyword, keywords}

object RiddlOptions {

  def adaptorOptions[u: P]: P[String] = keywords(
    StringIn(
      RiddlOption.technology,
      RiddlOption.kind,
      RiddlOption.css,
      RiddlOption.faicon
    ).!
  )
  def applicationOptions[u: P]: P[String] = keywords(
    StringIn(
      RiddlOption.technology,
      RiddlOption.kind,
      RiddlOption.css,
      RiddlOption.faicon
    ).!
  )

  def contextOptions[u: P]: P[String] = keywords(
    StringIn(
      RiddlOption.wrapper,
      RiddlOption.gateway,
      RiddlOption.service,
      RiddlOption.package_,
      RiddlOption.technology,
      RiddlOption.css,
      RiddlOption.kind,
      RiddlOption.faicon
    ).!
  )

  def domainOptions[u: P]: P[String] = keywords(
    StringIn(
      RiddlOption.external,
      RiddlOption.package_,
      RiddlOption.technology,
      RiddlOption.css,
      RiddlOption.kind,
      RiddlOption.faicon
    ).!
  )

  def entityOptions[u: P]: P[String] = keywords(
    StringIn(
      RiddlOption.event_sourced,
      RiddlOption.value,
      RiddlOption.aggregate,
      RiddlOption.transient,
      RiddlOption.consistent,
      RiddlOption.available,
      RiddlOption.finite_state_machine,
      RiddlOption.kind,
      RiddlOption.message_queue,
      RiddlOption.technology,
      RiddlOption.css,
      RiddlOption.faicon
    ).!
  )

  def epicOptions[u: P]: P[String] = keywords {
    StringIn(
      RiddlOption.technology,
      RiddlOption.css,
      RiddlOption.sync,
      RiddlOption.kind,
      RiddlOption.faicon
    ).!
  }

  def repositoryOptions[u: P]: P[String] = keywords(
    StringIn(
      RiddlOption.technology,
      RiddlOption.kind,
      RiddlOption.css,
      RiddlOption.faicon
    ).!
  )

  def sagaOptions[u: P]: P[String] = keywords(
    StringIn(
      RiddlOption.technology,
      RiddlOption.kind,
      RiddlOption.css,
      RiddlOption.faicon,
      RiddlOption.parallel,
      RiddlOption.sequential
    ).!
  )


  def aggregate[u: P]: P[Unit] = keyword(RiddlOption.aggregate)
  def async[u: P]: P[Unit] = keyword(RiddlOption.async)
  def available[u: P]: P[Unit] = keyword(RiddlOption.available)
  def css[u: P]: P[Unit] = keyword(RiddlOption.css)
  def concept[u: P]: P[Unit] = keyword(RiddlOption.concept)
  def consistent[u: P]: P[Unit] = keyword(RiddlOption.consistent)
  def device[u: P]: P[Unit] = keyword(RiddlOption.device)
  def external[u: P]: P[Unit] = keyword(RiddlOption.external)
  def events_sourced[u: P]: P[Unit] = keyword(RiddlOption.event_sourced)
  def finiteStateMachine[u: P]: P[Unit] = keyword(RiddlOption.finite_state_machine)
  def gateway[u: P]: P[Unit] = keyword(RiddlOption.gateway)
  def kind[u: P]: P[Unit] = keyword(RiddlOption.kind)
  def message_queue[u: P]: P[Unit] = keyword(RiddlOption.message_queue)
  def package_[u: P]: P[Unit] = keyword(RiddlOption.package_)
  def parallel[u: P]: P[Unit] = keyword(RiddlOption.parallel)
  def persistent[u: P]: P[Unit] = keyword(RiddlOption.persistent)
  def reply[u: P]: P[Unit] = keyword(RiddlOption.reply)
  def sequential[u: P]: P[Unit] = keyword(RiddlOption.sequential)
  def service[u: P]: P[Unit] = keyword(RiddlOption.service)
  def sync[u: P]: P[Unit] = keyword(RiddlOption.sync)
  def value[u: P]: P[Unit] = keyword(RiddlOption.value)
  def wrapper[u: P]: P[Unit] = keyword(RiddlOption.wrapper)
  def tail_recursive[u: P]: P[Unit] = "tail-recursive"
  def technology[u: P]: P[Unit] = keyword(RiddlOption.technology)
  def transient[u: P]: P[Unit] = keyword(RiddlOption.transient)
  def user[u: P]: P[Unit] = keyword(RiddlOption.user)

}

object RiddlOption {
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
  final val value = "final value"
  final val wrapper = "wrapper"
  final val tail_recursive = "tail-recursive"
  final val technology = "technology"
  final val transient = "transient"
  final val user = "user"

}
