package com.reactific.riddl.language.testkit

import java.time.{Clock, Instant, ZoneId}

/** An implementation of java.time.Clock which only moves through time via calls to `updateInstant`.
 * This allows fine-grained, side-effect-free deterministic control over the progression of system
 * time, which is useful for testing purposes.
 */
final class AdjustableClock(
  private var inst: Instant,
  zone: ZoneId = ZoneId.of("UTC"))
    extends Clock {

  override def getZone: ZoneId = zone

  override def withZone(zone: ZoneId): Clock = new AdjustableClock(instant(), zone)
  override def instant(): Instant = inst

  /** Updates the current time of this clock to the result of applying `f` to the current time
    */
  def updateInstant(f: Instant => Instant): this.type = {
    this.inst = f(this.inst)
    this
  }
}
