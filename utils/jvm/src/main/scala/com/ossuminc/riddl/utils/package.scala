package com.ossuminc.riddl

import com.ossuminc.riddl.utils.{JVMPlatformIOContext, PlatformIOContext}

import scala.concurrent.ExecutionContext

package object utils {
  given pc: PlatformIOContext = JVMPlatformIOContext()
  implicit val ec: ExecutionContext = pc.ec
}
