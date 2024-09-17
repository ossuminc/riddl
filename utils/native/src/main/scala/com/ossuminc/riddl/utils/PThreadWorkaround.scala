package com.ossuminc.riddl.utils

import scalanative.unsafe._
import scalanative.libc._
import scalanative.runtime.Platform

type pthread_condattr_t = CStruct0
type clockid_t = CInt


def pthread_condattr_setclock(attr: Ptr[pthread_condattr_t], clock_id: clockid_t): clockid_t =
  if Platform.isMac() then 0
  else PThreadWorkaround.pthread_condattr_setclock(attr, clock_id)
  end if
end pthread_condattr_setclock

object PThreadWorkaround {
  @link("pthread")
  @extern
  def pthread_condattr_setclock(attr: Ptr[pthread_condattr_t], clock_id: clockid_t): CInt = extern
  private object PthreadExt:
  end PthreadExt
}
