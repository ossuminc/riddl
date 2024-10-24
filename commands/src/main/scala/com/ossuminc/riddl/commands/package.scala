package com.ossuminc.riddl

import com.ossuminc.riddl.utils.{PlatformIOContext, JVMPlatformIOContext}

import scala.concurrent.ExecutionContext

package object commands {
  given pc: PlatformIOContext = JVMPlatformIOContext()
  implicit val ec: ExecutionContext = pc.ec
}
