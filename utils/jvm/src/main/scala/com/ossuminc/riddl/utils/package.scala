package com.ossuminc.riddl

import scala.concurrent.ExecutionContext

package object utils {
  given pc: PlatformIOContext = JVMPlatformIOContext()
  implicit val ec: ExecutionContext = pc.ec
}
